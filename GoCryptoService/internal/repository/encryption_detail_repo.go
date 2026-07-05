package repository

import (
	"context"
	"database/sql"
	"fmt"

	"gocryptoservice/internal/model"
)

type EncryptionDetailRepository struct {
	db *sql.DB
}

func NewEncryptionDetailRepository(db *sql.DB) *EncryptionDetailRepository {
	return &EncryptionDetailRepository{db: db}
}

func (r *EncryptionDetailRepository) Save(ctx context.Context, d *model.EncryptionDetail) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO encryption_details (operation_id, algorithm, recipient_dn, input_size, output_size, original_filename)
		VALUES (?, ?, ?, ?, ?, ?)
	`, d.OperationID, nullStr(d.Algorithm), nullStr(d.RecipientDN), nullInt(d.InputSize), nullInt(d.OutputSize), nullStr(d.OriginalFilename))
	if err != nil {
		return fmt.Errorf("failed to save encryption detail: %w", err)
	}
	return nil
}

func (r *EncryptionDetailRepository) FindByID(ctx context.Context, operationID string) (*model.EncryptionDetail, error) {
	var (
		d           model.EncryptionDetail
		algorithm   sql.NullString
		recipientDN sql.NullString
		inputSize   sql.NullInt64
		outputSize  sql.NullInt64
		filename    sql.NullString
	)
	err := r.db.QueryRowContext(ctx, `
		SELECT operation_id, algorithm, recipient_dn, input_size, output_size, original_filename
		FROM encryption_details WHERE operation_id = ?
	`, operationID).Scan(&d.OperationID, &algorithm, &recipientDN, &inputSize, &outputSize, &filename)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to find encryption detail %q: %w", operationID, err)
	}
	d.Algorithm = strPtr(algorithm)
	d.RecipientDN = strPtr(recipientDN)
	d.InputSize = intPtr(inputSize)
	d.OutputSize = intPtr(outputSize)
	d.OriginalFilename = strPtr(filename)
	return &d, nil
}
