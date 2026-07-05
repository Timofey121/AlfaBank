package httpapi

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
)

func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		writeValidationError(w, "request body is invalid or malformed JSON: "+err.Error())
		return false
	}
	return true
}

func requireNonBlank(w http.ResponseWriter, field, value string) bool {
	if strings.TrimSpace(value) == "" {
		writeValidationError(w, field+": is required")
		return false
	}
	return true
}

func requireRange(w http.ResponseWriter, field string, value, min, max int) bool {
	if value < min || value > max {
		writeValidationError(w, field+": must be between "+strconv.Itoa(min)+" and "+strconv.Itoa(max))
		return false
	}
	return true
}

func requireMode(w http.ResponseWriter, mode string) bool {
	if mode != "ATTACHED" && mode != "DETACHED" {
		writeValidationError(w, "mode: mode must be ATTACHED or DETACHED")
		return false
	}
	return true
}
