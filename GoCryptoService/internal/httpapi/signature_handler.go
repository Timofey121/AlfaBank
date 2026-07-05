package httpapi

import (
	"net/http"
	"time"

	"gocryptoservice/internal/service"
)

type signRequest struct {
	Data     string `json:"data"`
	KeyAlias string `json:"keyAlias"`
	Mode     string `json:"mode"`
}

type signResponse struct {
	OperationID       string    `json:"operationId"`
	Signature         string    `json:"signature"`
	Mode              string    `json:"mode"`
	SignerCertificate string    `json:"signerCertificate"`
	Timestamp         time.Time `json:"timestamp"`
}

type verifyRequest struct {
	Signature string `json:"signature"`
	Data      string `json:"data"`
	Mode      string `json:"mode"`
}

type verifyResponse struct {
	OperationID   string     `json:"operationId"`
	Valid         bool       `json:"valid"`
	SignerSubject string     `json:"signerSubject,omitempty"`
	SignerSerial  string     `json:"signerSerial,omitempty"`
	CertNotBefore *time.Time `json:"certNotBefore,omitempty"`
	CertNotAfter  *time.Time `json:"certNotAfter,omitempty"`
	Timestamp     time.Time  `json:"timestamp"`
}

type SignatureHandler struct {
	signatures *service.SignatureService
}

func NewSignatureHandler(signatures *service.SignatureService) *SignatureHandler {
	return &SignatureHandler{signatures: signatures}
}

func (h *SignatureHandler) Sign(w http.ResponseWriter, r *http.Request) {
	var req signRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if req.Mode == "" {
		req.Mode = "ATTACHED"
	}
	if !requireNonBlank(w, "data", req.Data) || !requireNonBlank(w, "keyAlias", req.KeyAlias) {
		return
	}
	if !requireMode(w, req.Mode) {
		return
	}

	result, err := h.signatures.Sign(r.Context(), req.Data, req.KeyAlias, req.Mode)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, signResponse{
		OperationID:       result.OperationID,
		Signature:         result.Signature,
		Mode:              result.Mode,
		SignerCertificate: result.SignerCertificate,
		Timestamp:         result.Timestamp,
	})
}

func (h *SignatureHandler) Verify(w http.ResponseWriter, r *http.Request) {
	var req verifyRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if req.Mode == "" {
		req.Mode = "ATTACHED"
	}
	if !requireNonBlank(w, "signature", req.Signature) {
		return
	}
	if !requireMode(w, req.Mode) {
		return
	}

	result, err := h.signatures.Verify(r.Context(), req.Signature, req.Data, req.Mode)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, verifyResponse{
		OperationID:   result.OperationID,
		Valid:         result.Valid,
		SignerSubject: result.SignerSubject,
		SignerSerial:  result.SignerSerial,
		CertNotBefore: result.CertNotBefore,
		CertNotAfter:  result.CertNotAfter,
		Timestamp:     result.Timestamp,
	})
}
