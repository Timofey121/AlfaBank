package httpapi

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/service"
)

type errorBody struct {
	Error errorDetail `json:"error"`
}

type errorDetail struct {
	Code      string    `json:"code"`
	Message   string    `json:"message"`
	Timestamp time.Time `json:"timestamp"`
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(errorBody{Error: errorDetail{Code: code, Message: message, Timestamp: time.Now()}})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeValidationError(w http.ResponseWriter, message string) {
	writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", message)
}

func handleServiceError(w http.ResponseWriter, err error) {
	var keystoreErr *service.KeystoreError
	var cryptoErr *service.CryptoOperationError
	var networkErr *service.NetworkError
	var validationErr *service.ValidationError

	switch {
	case errors.Is(err, keystore.ErrKeyAliasNotFound):
		writeError(w, http.StatusUnprocessableEntity, "KEY_ALIAS_NOT_FOUND", err.Error())
	case errors.As(err, &keystoreErr):
		slog.Error("keystore error", "error", err)
		writeError(w, http.StatusUnprocessableEntity, "KEYSTORE_ERROR", "Keystore operation failed")
	case errors.As(err, &cryptoErr):
		slog.Error("crypto operation failed", "error", err)
		writeError(w, http.StatusUnprocessableEntity, "CRYPTO_OPERATION_FAILED", "Crypto operation failed")
	case errors.As(err, &networkErr):
		slog.Error("network error", "error", err)
		writeError(w, http.StatusBadGateway, "NETWORK_ERROR", "Failed to fetch document")
	case errors.As(err, &validationErr):
		slog.Warn("invalid request", "error", err)
		writeError(w, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request parameter")
	default:
		slog.Error("unexpected error", "error", err)
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Internal server error")
	}
}
