package httpapi

import (
	"net/http"
	"time"

	"gocryptoservice/internal/service"
)

type hashRequest struct {
	Data string `json:"data"`
}

type hashResponse struct {
	OperationID    string    `json:"operationId"`
	Algorithm      string    `json:"algorithm"`
	Hash           string    `json:"hash"`
	InputSizeBytes int       `json:"inputSizeBytes"`
	Timestamp      time.Time `json:"timestamp"`
}

type HashHandler struct {
	hash *service.HashService
}

func NewHashHandler(hash *service.HashService) *HashHandler {
	return &HashHandler{hash: hash}
}

func (h *HashHandler) Hash(w http.ResponseWriter, r *http.Request) {
	var req hashRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if !requireNonBlank(w, "data", req.Data) {
		return
	}

	result, err := h.hash.Hash(r.Context(), req.Data)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, hashResponse{
		OperationID:    result.OperationID,
		Algorithm:      result.Algorithm,
		Hash:           result.Hash,
		InputSizeBytes: result.InputSizeBytes,
		Timestamp:      result.Timestamp,
	})
}
