package repository

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"gocryptoservice/internal/model"
)

type OperationsRepository struct {
	db *sql.DB
}

func NewOperationsRepository(db *sql.DB) *OperationsRepository {
	return &OperationsRepository{db: db}
}

func (r *OperationsRepository) Save(ctx context.Context, op *model.CryptoOperation) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO crypto_operations
			(id, type, status, app_source, input_hash, output_hash, key_alias, error_code, error_msg, duration_ms, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(id) DO UPDATE SET
			type=excluded.type, status=excluded.status, app_source=excluded.app_source,
			input_hash=excluded.input_hash, output_hash=excluded.output_hash, key_alias=excluded.key_alias,
			error_code=excluded.error_code, error_msg=excluded.error_msg, duration_ms=excluded.duration_ms,
			created_at=excluded.created_at
	`,
		op.ID, string(op.Type), op.Status, op.AppSource,
		nullStr(op.InputHash), nullStr(op.OutputHash), nullStr(op.KeyAlias),
		nullStr(op.ErrorCode), nullStr(op.ErrorMsg), nullInt64(op.DurationMs),
		op.CreatedAt.UTC().Format(time.RFC3339Nano),
	)
	if err != nil {
		return fmt.Errorf("failed to save operation: %w", err)
	}
	return nil
}

func (r *OperationsRepository) FindByID(ctx context.Context, id string) (*model.CryptoOperation, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, type, status, app_source, input_hash, output_hash, key_alias, error_code, error_msg, duration_ms, created_at
		FROM crypto_operations WHERE id = ?
	`, id)
	op, err := scanOperation(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to find operation %q: %w", id, err)
	}
	return op, nil
}

func (r *OperationsRepository) List(ctx context.Context, opType *model.OperationType, page, size int) ([]model.CryptoOperation, int, error) {
	var total int
	var countRow *sql.Row
	if opType != nil {
		countRow = r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM crypto_operations WHERE type = ?`, string(*opType))
	} else {
		countRow = r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM crypto_operations`)
	}
	if err := countRow.Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("failed to count operations: %w", err)
	}

	var rows *sql.Rows
	var err error
	query := `
		SELECT id, type, status, app_source, input_hash, output_hash, key_alias, error_code, error_msg, duration_ms, created_at
		FROM crypto_operations
	`
	if opType != nil {
		rows, err = r.db.QueryContext(ctx, query+` WHERE type = ? ORDER BY created_at DESC LIMIT ? OFFSET ?`,
			string(*opType), size, page*size)
	} else {
		rows, err = r.db.QueryContext(ctx, query+` ORDER BY created_at DESC LIMIT ? OFFSET ?`, size, page*size)
	}
	if err != nil {
		return nil, 0, fmt.Errorf("failed to list operations: %w", err)
	}
	defer rows.Close()

	var results []model.CryptoOperation
	for rows.Next() {
		op, err := scanOperation(rows)
		if err != nil {
			return nil, 0, fmt.Errorf("failed to scan operation: %w", err)
		}
		results = append(results, *op)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	return results, total, nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanOperation(row rowScanner) (*model.CryptoOperation, error) {
	var (
		op          model.CryptoOperation
		typeStr     string
		inputHash   sql.NullString
		outputHash  sql.NullString
		keyAlias    sql.NullString
		errorCode   sql.NullString
		errorMsg    sql.NullString
		durationMs  sql.NullInt64
		createdAtStr string
	)
	if err := row.Scan(&op.ID, &typeStr, &op.Status, &op.AppSource, &inputHash, &outputHash,
		&keyAlias, &errorCode, &errorMsg, &durationMs, &createdAtStr); err != nil {
		return nil, err
	}
	op.Type = model.OperationType(typeStr)
	op.InputHash = strPtr(inputHash)
	op.OutputHash = strPtr(outputHash)
	op.KeyAlias = strPtr(keyAlias)
	op.ErrorCode = strPtr(errorCode)
	op.ErrorMsg = strPtr(errorMsg)
	op.DurationMs = int64Ptr(durationMs)
	createdAt, err := time.Parse(time.RFC3339Nano, createdAtStr)
	if err != nil {
		return nil, fmt.Errorf("invalid created_at: %w", err)
	}
	op.CreatedAt = createdAt
	return &op, nil
}
