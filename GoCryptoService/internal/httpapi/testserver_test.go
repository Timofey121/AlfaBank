package httpapi

import (
	"net/http"
	"path/filepath"
	"testing"

	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/repository"
	"gocryptoservice/internal/service"
)

func newTestServer(t *testing.T) http.Handler {
	t.Helper()
	dir := t.TempDir()

	db, err := repository.OpenDB(filepath.Join(dir, "test.sqlite"))
	if err != nil {
		t.Fatalf("OpenDB failed: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	ks, err := keystore.New(filepath.Join(dir, "certs"), "test-password")
	if err != nil {
		t.Fatalf("keystore.New failed: %v", err)
	}

	opsRepo := repository.NewOperationsRepository(db)
	sigDetailRepo := repository.NewSignatureDetailRepository(db)
	encDetailRepo := repository.NewEncryptionDetailRepository(db)
	fetchDetailRepo := repository.NewFetchDetailRepository(db)
	auditService := service.NewAuditService(opsRepo)

	services := Services{
		KeyGen:     service.NewKeyGenerationService(ks, auditService),
		Signature:  service.NewSignatureService(ks, auditService, sigDetailRepo),
		Encryption: service.NewEncryptionService(ks, auditService, encDetailRepo),
		Hash:       service.NewHashService(auditService),
		Fetch:      service.NewFetchService(auditService, fetchDetailRepo),
		Operations: service.NewOperationsService(opsRepo, sigDetailRepo, encDetailRepo, fetchDetailRepo),
	}
	return NewRouter(services)
}
