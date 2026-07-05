package service

import (
	"context"
	"encoding/base64"
	"time"

	"gocryptoservice/internal/crypto"
	"gocryptoservice/internal/model"
)

type HashResult struct {
	OperationID    string
	Algorithm      string
	Hash           string
	InputSizeBytes int
	Timestamp      time.Time
}

type HashService struct {
	audit *AuditService
}

func NewHashService(audit *AuditService) *HashService {
	return &HashService{audit: audit}
}

func (s *HashService) Hash(ctx context.Context, dataB64 string) (HashResult, error) {
	return Run(ctx, s.audit, model.OpHash, nil, nil, "HASH_FAILED",
		func(op *model.CryptoOperation) (HashResult, []byte, error) {
			data, err := base64.StdEncoding.DecodeString(dataB64)
			if err != nil {
				return HashResult{}, nil, &ValidationError{Msg: "invalid base64 in data: " + err.Error()}
			}
			hash := crypto.SHA256Hex(data)
			result := HashResult{
				OperationID:    op.ID,
				Algorithm:      "SHA-256",
				Hash:           hash,
				InputSizeBytes: len(data),
				Timestamp:      time.Now(),
			}
			return result, []byte(hash), nil
		})
}
