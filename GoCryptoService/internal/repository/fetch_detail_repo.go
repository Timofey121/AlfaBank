package repository

import (
	"context"
	"database/sql"
	"fmt"

	"gocryptoservice/internal/model"
)

type FetchDetailRepository struct {
	db *sql.DB
}

func NewFetchDetailRepository(db *sql.DB) *FetchDetailRepository {
	return &FetchDetailRepository{db: db}
}

func (r *FetchDetailRepository) Save(ctx context.Context, d *model.FetchDetail) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO fetch_details (operation_id, source_url, http_status, content_type, size_bytes)
		VALUES (?, ?, ?, ?, ?)
	`, d.OperationID, d.SourceURL, nullInt(d.HTTPStatus), nullStr(d.ContentType), nullInt64(d.SizeBytes))
	if err != nil {
		return fmt.Errorf("failed to save fetch detail: %w", err)
	}
	return nil
}

func (r *FetchDetailRepository) FindByID(ctx context.Context, operationID string) (*model.FetchDetail, error) {
	var (
		d           model.FetchDetail
		httpStatus  sql.NullInt64
		contentType sql.NullString
		sizeBytes   sql.NullInt64
	)
	err := r.db.QueryRowContext(ctx, `
		SELECT operation_id, source_url, http_status, content_type, size_bytes
		FROM fetch_details WHERE operation_id = ?
	`, operationID).Scan(&d.OperationID, &d.SourceURL, &httpStatus, &contentType, &sizeBytes)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to find fetch detail %q: %w", operationID, err)
	}
	d.HTTPStatus = intPtr(httpStatus)
	d.ContentType = strPtr(contentType)
	d.SizeBytes = int64Ptr(sizeBytes)
	return &d, nil
}
