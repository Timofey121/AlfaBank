package httpapi

import (
	"net/http"
	"time"

	"gocryptoservice/internal/service"
)

type fetchRequest struct {
	URL            string `json:"url"`
	TimeoutSeconds *int   `json:"timeoutSeconds"`
}

type fetchResponse struct {
	OperationID string    `json:"operationId"`
	Content     string    `json:"content"`
	ContentType string    `json:"contentType"`
	SizeBytes   int64     `json:"sizeBytes"`
	HTTPStatus  int       `json:"httpStatus"`
	FetchedAt   time.Time `json:"fetchedAt"`
}

type FetchHandler struct {
	fetch *service.FetchService
}

func NewFetchHandler(fetch *service.FetchService) *FetchHandler {
	return &FetchHandler{fetch: fetch}
}

func (h *FetchHandler) FetchDocument(w http.ResponseWriter, r *http.Request) {
	var req fetchRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if !requireNonBlank(w, "url", req.URL) {
		return
	}
	timeoutSeconds := 30
	if req.TimeoutSeconds != nil {
		timeoutSeconds = *req.TimeoutSeconds
	}
	if !requireRange(w, "timeoutSeconds", timeoutSeconds, 1, 120) {
		return
	}

	result, err := h.fetch.Fetch(r.Context(), req.URL, timeoutSeconds)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, fetchResponse{
		OperationID: result.OperationID,
		Content:     result.Content,
		ContentType: result.ContentType,
		SizeBytes:   result.SizeBytes,
		HTTPStatus:  result.HTTPStatus,
		FetchedAt:   result.FetchedAt,
	})
}
