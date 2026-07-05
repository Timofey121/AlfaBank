package httpapi

import (
	"net/http"
	"time"

	"gocryptoservice/internal/service"
)

type generateKeystoreRequest struct {
	Alias        string `json:"alias"`
	CN           string `json:"cn"`
	ValidityDays int    `json:"validityDays"`
}

type generateKeystoreResponse struct {
	Alias        string    `json:"alias"`
	Subject      string    `json:"subject"`
	SerialNumber string    `json:"serialNumber"`
	NotBefore    time.Time `json:"notBefore"`
	NotAfter     time.Time `json:"notAfter"`
	CertBase64   string    `json:"certBase64"`
}

type AdminHandler struct {
	keyGen *service.KeyGenerationService
}

func NewAdminHandler(keyGen *service.KeyGenerationService) *AdminHandler {
	return &AdminHandler{keyGen: keyGen}
}

func (h *AdminHandler) GenerateKeystore(w http.ResponseWriter, r *http.Request) {
	var req generateKeystoreRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if !requireNonBlank(w, "alias", req.Alias) || !requireNonBlank(w, "cn", req.CN) {
		return
	}
	if !requireRange(w, "validityDays", req.ValidityDays, 1, 3650) {
		return
	}

	result, err := h.keyGen.Generate(r.Context(), req.Alias, req.CN, req.ValidityDays)
	if err != nil {
		handleServiceError(w, err)
		return
	}

	writeJSON(w, http.StatusCreated, generateKeystoreResponse{
		Alias:        result.Alias,
		Subject:      result.Subject,
		SerialNumber: result.SerialNumber,
		NotBefore:    result.NotBefore,
		NotAfter:     result.NotAfter,
		CertBase64:   result.CertBase64,
	})
}
