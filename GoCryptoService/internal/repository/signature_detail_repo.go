package repository

import (
	"context"
	"database/sql"
	"fmt"

	"gocryptoservice/internal/model"
)

type SignatureDetailRepository struct {
	db *sql.DB
}

func NewSignatureDetailRepository(db *sql.DB) *SignatureDetailRepository {
	return &SignatureDetailRepository{db: db}
}

func (r *SignatureDetailRepository) Save(ctx context.Context, d *model.SignatureDetail) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO signature_details (operation_id, sign_mode, signer_subject, signer_serial, cert_not_before, cert_not_after, is_valid)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`, d.OperationID, nullStr(d.SignMode), nullStr(d.SignerSubject), nullStr(d.SignerSerial),
		nullTime(d.CertNotBefore), nullTime(d.CertNotAfter), nullBool(d.IsValid))
	if err != nil {
		return fmt.Errorf("failed to save signature detail: %w", err)
	}
	return nil
}

func (r *SignatureDetailRepository) FindByID(ctx context.Context, operationID string) (*model.SignatureDetail, error) {
	var (
		d             model.SignatureDetail
		signMode      sql.NullString
		signerSubject sql.NullString
		signerSerial  sql.NullString
		certNotBefore sql.NullString
		certNotAfter  sql.NullString
		isValid       sql.NullBool
	)
	err := r.db.QueryRowContext(ctx, `
		SELECT operation_id, sign_mode, signer_subject, signer_serial, cert_not_before, cert_not_after, is_valid
		FROM signature_details WHERE operation_id = ?
	`, operationID).Scan(&d.OperationID, &signMode, &signerSubject, &signerSerial, &certNotBefore, &certNotAfter, &isValid)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to find signature detail %q: %w", operationID, err)
	}
	d.SignMode = strPtr(signMode)
	d.SignerSubject = strPtr(signerSubject)
	d.SignerSerial = strPtr(signerSerial)
	var perr error
	if d.CertNotBefore, perr = timePtr(certNotBefore); perr != nil {
		return nil, fmt.Errorf("invalid cert_not_before: %w", perr)
	}
	if d.CertNotAfter, perr = timePtr(certNotAfter); perr != nil {
		return nil, fmt.Errorf("invalid cert_not_after: %w", perr)
	}
	d.IsValid = boolPtr(isValid)
	return &d, nil
}
