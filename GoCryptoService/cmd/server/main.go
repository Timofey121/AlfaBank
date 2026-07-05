package main

import (
	"log/slog"
	"net/http"
	"os"

	"gocryptoservice/internal/config"
	"gocryptoservice/internal/httpapi"
	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/repository"
	"gocryptoservice/internal/service"
)

func main() {
	if err := run(); err != nil {
		slog.Error("fatal", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	db, err := repository.OpenDB(cfg.DBPath)
	if err != nil {
		return err
	}
	defer db.Close()

	ks, err := keystore.New(cfg.KeystorePath, cfg.KeystorePassword)
	if err != nil {
		return err
	}

	opsRepo := repository.NewOperationsRepository(db)
	sigDetailRepo := repository.NewSignatureDetailRepository(db)
	encDetailRepo := repository.NewEncryptionDetailRepository(db)
	fetchDetailRepo := repository.NewFetchDetailRepository(db)

	auditService := service.NewAuditService(opsRepo)

	services := httpapi.Services{
		KeyGen:     service.NewKeyGenerationService(ks, auditService),
		Signature:  service.NewSignatureService(ks, auditService, sigDetailRepo),
		Encryption: service.NewEncryptionService(ks, auditService, encDetailRepo),
		Hash:       service.NewHashService(auditService),
		Fetch:      service.NewFetchService(auditService, fetchDetailRepo),
		Operations: service.NewOperationsService(opsRepo, sigDetailRepo, encDetailRepo, fetchDetailRepo),
	}

	router := httpapi.NewRouter(services)

	addr := ":" + cfg.Port
	slog.Info("starting GoCryptoService", "addr", addr, "keystorePath", cfg.KeystorePath, "dbPath", cfg.DBPath)
	return http.ListenAndServe(addr, router)
}
