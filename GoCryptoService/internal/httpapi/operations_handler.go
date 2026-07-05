package httpapi

import (
	"net/http"
	"strconv"
	"time"

	"gocryptoservice/internal/service"
)

type operationRecordResponse struct {
	ID        string    `json:"id"`
	Type      string    `json:"type"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
	KeyAlias  string    `json:"keyAlias,omitempty"`
}

type pageResponse struct {
	Content       []operationRecordResponse `json:"content"`
	Page          int                        `json:"page"`
	Size          int                        `json:"size"`
	TotalElements int                        `json:"totalElements"`
	TotalPages    int                        `json:"totalPages"`
}

type signatureInfoResponse struct {
	SignMode      string     `json:"signMode,omitempty"`
	SignerSubject string     `json:"signerSubject,omitempty"`
	SignerSerial  string     `json:"signerSerial,omitempty"`
	CertNotBefore *time.Time `json:"certNotBefore,omitempty"`
	CertNotAfter  *time.Time `json:"certNotAfter,omitempty"`
	IsValid       *bool      `json:"isValid,omitempty"`
}

type encryptionInfoResponse struct {
	Algorithm        string `json:"algorithm,omitempty"`
	RecipientDN      string `json:"recipientDn,omitempty"`
	InputSize        *int   `json:"inputSize,omitempty"`
	OutputSize       *int   `json:"outputSize,omitempty"`
	OriginalFilename string `json:"originalFilename,omitempty"`
}

type fetchInfoResponse struct {
	SourceURL   string `json:"sourceUrl"`
	HTTPStatus  *int   `json:"httpStatus,omitempty"`
	ContentType string `json:"contentType,omitempty"`
	SizeBytes   *int64 `json:"sizeBytes,omitempty"`
}

type operationDetailResponse struct {
	ID         string                  `json:"id"`
	Type       string                  `json:"type"`
	Status     string                  `json:"status"`
	KeyAlias   string                  `json:"keyAlias,omitempty"`
	InputHash  string                  `json:"inputHash,omitempty"`
	OutputHash string                  `json:"outputHash,omitempty"`
	ErrorCode  string                  `json:"errorCode,omitempty"`
	ErrorMsg   string                  `json:"errorMsg,omitempty"`
	DurationMs *int64                  `json:"durationMs,omitempty"`
	CreatedAt  time.Time               `json:"createdAt"`
	Signature  *signatureInfoResponse  `json:"signature,omitempty"`
	Encryption *encryptionInfoResponse `json:"encryption,omitempty"`
	Fetch      *fetchInfoResponse      `json:"fetch,omitempty"`
}

type OperationsHandler struct {
	operations *service.OperationsService
}

func NewOperationsHandler(operations *service.OperationsService) *OperationsHandler {
	return &OperationsHandler{operations: operations}
}

func (h *OperationsHandler) List(w http.ResponseWriter, r *http.Request) {
	page := queryInt(r, "page", 0)
	size := queryInt(r, "size", 20)
	typeFilter := r.URL.Query().Get("type")

	records, total, err := h.operations.List(r.Context(), page, size, typeFilter)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	content := make([]operationRecordResponse, 0, len(records))
	for _, rec := range records {
		content = append(content, operationRecordResponse{
			ID: rec.ID, Type: rec.Type, Status: rec.Status, CreatedAt: rec.CreatedAt, KeyAlias: rec.KeyAlias,
		})
	}
	totalPages := 0
	if size > 0 {
		totalPages = (total + size - 1) / size
	}
	writeJSON(w, http.StatusOK, pageResponse{
		Content: content, Page: page, Size: size, TotalElements: total, TotalPages: totalPages,
	})
}

func (h *OperationsHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	detail, err := h.operations.GetByID(r.Context(), id)
	if err != nil {
		handleServiceError(w, err)
		return
	}
	if detail == nil {
		w.WriteHeader(http.StatusNotFound)
		return
	}

	resp := operationDetailResponse{
		ID: detail.ID, Type: detail.Type, Status: detail.Status, KeyAlias: detail.KeyAlias,
		InputHash: detail.InputHash, OutputHash: detail.OutputHash, ErrorCode: detail.ErrorCode,
		ErrorMsg: detail.ErrorMsg, DurationMs: detail.DurationMs, CreatedAt: detail.CreatedAt,
	}
	if detail.Signature != nil {
		resp.Signature = &signatureInfoResponse{
			SignMode: detail.Signature.SignMode, SignerSubject: detail.Signature.SignerSubject,
			SignerSerial: detail.Signature.SignerSerial, CertNotBefore: detail.Signature.CertNotBefore,
			CertNotAfter: detail.Signature.CertNotAfter, IsValid: detail.Signature.IsValid,
		}
	}
	if detail.Encryption != nil {
		resp.Encryption = &encryptionInfoResponse{
			Algorithm: detail.Encryption.Algorithm, RecipientDN: detail.Encryption.RecipientDN,
			InputSize: detail.Encryption.InputSize, OutputSize: detail.Encryption.OutputSize,
			OriginalFilename: detail.Encryption.OriginalFilename,
		}
	}
	if detail.Fetch != nil {
		resp.Fetch = &fetchInfoResponse{
			SourceURL: detail.Fetch.SourceURL, HTTPStatus: detail.Fetch.HTTPStatus,
			ContentType: detail.Fetch.ContentType, SizeBytes: detail.Fetch.SizeBytes,
		}
	}
	writeJSON(w, http.StatusOK, resp)
}

func queryInt(r *http.Request, key string, def int) int {
	v := r.URL.Query().Get(key)
	if v == "" {
		return def
	}
	i, err := strconv.Atoi(v)
	if err != nil {
		return def
	}
	return i
}
