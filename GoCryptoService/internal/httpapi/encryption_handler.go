package httpapi

import (
	"net/http"
	"time"

	"gocryptoservice/internal/service"
)

type encryptRequest struct {
	Plaintext            string `json:"plaintext"`
	RecipientCertificate string `json:"recipientCertificate"`
	Filename             string `json:"filename"`
}

type encryptResponse struct {
	OperationID string    `json:"operationId"`
	Ciphertext  string    `json:"ciphertext"`
	Timestamp   time.Time `json:"timestamp"`
}

type decryptRequest struct {
	Ciphertext string `json:"ciphertext"`
	KeyAlias   string `json:"keyAlias"`
}

type decryptResponse struct {
	OperationID string    `json:"operationId"`
	Plaintext   string    `json:"plaintext"`
	Filename    string    `json:"filename,omitempty"`
	Timestamp   time.Time `json:"timestamp"`
}

type EncryptionHandler struct {
	encryption *service.EncryptionService
}

func NewEncryptionHandler(encryption *service.EncryptionService) *EncryptionHandler {
	return &EncryptionHandler{encryption: encryption}
}

func (h *EncryptionHandler) Encrypt(w http.ResponseWriter, r *http.Request) {
	var req encryptRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if !requireNonBlank(w, "plaintext", req.Plaintext) || !requireNonBlank(w, "recipientCertificate", req.RecipientCertificate) {
		return
	}
	if len(req.Filename) > 255 {
		writeValidationError(w, "filename: exceeds maximum allowed length")
		return
	}

	result, err := h.encryption.Encrypt(r.Context(), req.Plaintext, req.RecipientCertificate, req.Filename)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, encryptResponse{
		OperationID: result.OperationID,
		Ciphertext:  result.Ciphertext,
		Timestamp:   result.Timestamp,
	})
}

func (h *EncryptionHandler) Decrypt(w http.ResponseWriter, r *http.Request) {
	var req decryptRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if !requireNonBlank(w, "ciphertext", req.Ciphertext) || !requireNonBlank(w, "keyAlias", req.KeyAlias) {
		return
	}

	result, err := h.encryption.Decrypt(r.Context(), req.Ciphertext, req.KeyAlias)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, decryptResponse{
		OperationID: result.OperationID,
		Plaintext:   result.Plaintext,
		Filename:    result.Filename,
		Timestamp:   result.Timestamp,
	})
}
