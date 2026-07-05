package service

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"

	"gocryptoservice/internal/crypto"
	"gocryptoservice/internal/model"
	"gocryptoservice/internal/repository"
)

const appSource = "GO"

type AuditService struct {
	repo *repository.OperationsRepository
}

func NewAuditService(repo *repository.OperationsRepository) *AuditService {
	return &AuditService{repo: repo}
}

func (s *AuditService) CreatePending(ctx context.Context, opType model.OperationType, inputData []byte, keyAlias *string) (*model.CryptoOperation, error) {
	op := &model.CryptoOperation{
		ID:        uuid.NewString(),
		Type:      opType,
		Status:    model.StatusPending,
		AppSource: appSource,
		KeyAlias:  keyAlias,
		CreatedAt: time.Now(),
	}
	if inputData != nil {
		hash := crypto.SHA256Hex(inputData)
		op.InputHash = &hash
	}
	if err := s.repo.Save(ctx, op); err != nil {
		return nil, fmt.Errorf("failed to create pending operation: %w", err)
	}
	return op, nil
}

func (s *AuditService) MarkSuccess(ctx context.Context, op *model.CryptoOperation, outputData []byte, startedAt time.Time) error {
	op.Status = model.StatusSuccess
	duration := time.Since(startedAt).Milliseconds()
	op.DurationMs = &duration
	if outputData != nil {
		hash := crypto.SHA256Hex(outputData)
		op.OutputHash = &hash
	}
	if err := s.repo.Save(ctx, op); err != nil {
		return fmt.Errorf("failed to mark operation success: %w", err)
	}
	return nil
}

func (s *AuditService) MarkFailed(ctx context.Context, op *model.CryptoOperation, errorCode, errorMsg string, startedAt time.Time) error {
	op.Status = model.StatusFailed
	op.ErrorCode = &errorCode
	op.ErrorMsg = &errorMsg
	duration := time.Since(startedAt).Milliseconds()
	op.DurationMs = &duration
	if err := s.repo.Save(ctx, op); err != nil {
		return fmt.Errorf("failed to mark operation failed: %w", err)
	}
	return nil
}
