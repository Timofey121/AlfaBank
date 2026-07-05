package httpapi

import (
	"net/http"

	"gocryptoservice/internal/service"
)

type Services struct {
	KeyGen     *service.KeyGenerationService
	Signature  *service.SignatureService
	Encryption *service.EncryptionService
	Hash       *service.HashService
	Fetch      *service.FetchService
	Operations *service.OperationsService
}

func NewRouter(s Services) http.Handler {
	mux := http.NewServeMux()

	admin := NewAdminHandler(s.KeyGen)
	signature := NewSignatureHandler(s.Signature)
	encryption := NewEncryptionHandler(s.Encryption)
	hash := NewHashHandler(s.Hash)
	fetch := NewFetchHandler(s.Fetch)
	operations := NewOperationsHandler(s.Operations)

	mux.HandleFunc("POST /api/v1/admin/generate-keystore", admin.GenerateKeystore)

	mux.HandleFunc("POST /api/v1/crypto/sign", signature.Sign)
	mux.HandleFunc("POST /api/v1/crypto/verify", signature.Verify)
	mux.HandleFunc("POST /api/v1/crypto/encrypt", encryption.Encrypt)
	mux.HandleFunc("POST /api/v1/crypto/decrypt", encryption.Decrypt)
	mux.HandleFunc("POST /api/v1/crypto/hash", hash.Hash)

	mux.HandleFunc("POST /api/v1/fetch/document", fetch.FetchDocument)

	mux.HandleFunc("GET /api/v1/operations", operations.List)
	mux.HandleFunc("GET /api/v1/operations/{id}", operations.GetByID)

	return mux
}
