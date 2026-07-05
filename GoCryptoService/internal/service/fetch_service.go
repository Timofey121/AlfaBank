package service

import (
	"context"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"gocryptoservice/internal/model"
	"gocryptoservice/internal/repository"
)

const maxResponseSizeBytes = 10 * 1024 * 1024

type FetchResult struct {
	OperationID string
	Content     string
	ContentType string
	SizeBytes   int64
	HTTPStatus  int
	FetchedAt   time.Time
}

type FetchService struct {
	audit   *AuditService
	details *repository.FetchDetailRepository
}

func NewFetchService(audit *AuditService, details *repository.FetchDetailRepository) *FetchService {
	return &FetchService{audit: audit, details: details}
}

func (s *FetchService) Fetch(ctx context.Context, urlStr string, timeoutSeconds int) (FetchResult, error) {
	if err := validateFetchURL(urlStr); err != nil {
		return FetchResult{}, err
	}

	return Run(ctx, s.audit, model.OpFetch, []byte(urlStr), nil, "FETCH_FAILED",
		func(op *model.CryptoOperation) (FetchResult, []byte, error) {
			client := &http.Client{
				Timeout: time.Duration(timeoutSeconds) * time.Second,
				Transport: &http.Transport{
					DialContext: (&net.Dialer{Timeout: 10 * time.Second}).DialContext,
				},
				CheckRedirect: func(*http.Request, []*http.Request) error {
					return http.ErrUseLastResponse
				},
			}

			req, err := http.NewRequestWithContext(ctx, http.MethodGet, urlStr, nil)
			if err != nil {
				return FetchResult{}, nil, &NetworkError{Msg: "failed to build request for " + urlStr, Cause: err}
			}
			resp, err := client.Do(req)
			if err != nil {
				return FetchResult{}, nil, &NetworkError{Msg: "fetch failed for URL: " + urlStr, Cause: err}
			}
			defer resp.Body.Close()

			if resp.StatusCode >= 300 && resp.StatusCode < 400 {
				return FetchResult{}, nil, &NetworkError{Msg: fmt.Sprintf(
					"server responded with redirect status %d for %s; redirects are not followed", resp.StatusCode, urlStr)}
			}

			if declared := parseContentLength(resp.Header.Get("Content-Length")); declared > maxResponseSizeBytes {
				return FetchResult{}, nil, &NetworkError{Msg: fmt.Sprintf(
					"response for %s declared size %d bytes, exceeding the maximum allowed %d bytes",
					urlStr, declared, maxResponseSizeBytes)}
			}

			body, err := io.ReadAll(resp.Body)
			if err != nil {
				return FetchResult{}, nil, &NetworkError{Msg: "failed to read response body for " + urlStr, Cause: err}
			}

			contentType := resp.Header.Get("Content-Type")
			if contentType == "" {
				contentType = "application/octet-stream"
			}

			detail := &model.FetchDetail{
				OperationID: op.ID,
				SourceURL:   urlStr,
				HTTPStatus:  intPtr(resp.StatusCode),
				ContentType: strPtr(contentType),
				SizeBytes:   int64Ptr(int64(len(body))),
			}
			if err := s.details.Save(ctx, detail); err != nil {
				return FetchResult{}, nil, err
			}

			result := FetchResult{
				OperationID: op.ID,
				Content:     base64.StdEncoding.EncodeToString(body),
				ContentType: contentType,
				SizeBytes:   int64(len(body)),
				HTTPStatus:  resp.StatusCode,
				FetchedAt:   time.Now(),
			}
			return result, body, nil
		})
}

func validateFetchURL(urlStr string) error {
	parsed, err := url.Parse(urlStr)
	if err != nil {
		return &ValidationError{Msg: "invalid URL: " + err.Error()}
	}
	if parsed.Scheme != "https" {
		return &ValidationError{Msg: "only https URLs are allowed"}
	}
	host := parsed.Hostname()
	if host == "" {
		return &ValidationError{Msg: "URL must contain a valid host"}
	}
	addr, err := net.ResolveIPAddr("ip", host)
	if err != nil {
		return &ValidationError{Msg: "unable to resolve host: " + host}
	}
	if isDisallowedAddress(addr.IP) {
		return &ValidationError{Msg: "URL host resolves to a disallowed network address"}
	}
	return nil
}

func isDisallowedAddress(ip net.IP) bool {
	return ip.IsLoopback() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() ||
		ip.IsPrivate() || ip.IsUnspecified() || ip.IsMulticast()
}

func parseContentLength(header string) int64 {
	if header == "" {
		return -1
	}
	v, err := strconv.ParseInt(header, 10, 64)
	if err != nil {
		return -1
	}
	return v
}

func int64Ptr(i int64) *int64 { return &i }
