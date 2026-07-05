package service

import (
	"context"
	"log/slog"
	"time"

	"gocryptoservice/internal/model"
)

type Action[T any] func(op *model.CryptoOperation) (value T, auditOutput []byte, err error)

func Run[T any](ctx context.Context, audit *AuditService, opType model.OperationType, inputData []byte,
	keyAlias *string, failureCode string, action Action[T]) (T, error) {
	var zero T
	start := time.Now()

	op, err := audit.CreatePending(ctx, opType, inputData, keyAlias)
	if err != nil {
		return zero, err
	}

	value, auditOutput, err := action(op)
	if err != nil {
		slog.Error("operation failed", "id", op.ID, "type", opType, "error", err)
		if markErr := audit.MarkFailed(ctx, op, failureCode, err.Error(), start); markErr != nil {
			slog.Error("failed to record failure", "id", op.ID, "error", markErr)
		}
		return zero, err
	}

	if err := audit.MarkSuccess(ctx, op, auditOutput, start); err != nil {
		return zero, err
	}
	return value, nil
}
