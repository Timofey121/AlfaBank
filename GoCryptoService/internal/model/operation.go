package model

import "time"

const (
	StatusPending = "PENDING"
	StatusSuccess = "SUCCESS"
	StatusFailed  = "FAILED"
)

type CryptoOperation struct {
	ID         string
	Type       OperationType
	Status     string
	AppSource  string
	InputHash  *string
	OutputHash *string
	KeyAlias   *string
	ErrorCode  *string
	ErrorMsg   *string
	DurationMs *int64
	CreatedAt  time.Time
}

type SignatureDetail struct {
	OperationID   string
	SignMode      *string
	SignerSubject *string
	SignerSerial  *string
	CertNotBefore *time.Time
	CertNotAfter  *time.Time
	IsValid       *bool
}

type EncryptionDetail struct {
	OperationID      string
	Algorithm        *string
	RecipientDN      *string
	InputSize        *int
	OutputSize       *int
	OriginalFilename *string
}

type FetchDetail struct {
	OperationID string
	SourceURL   string
	HTTPStatus  *int
	ContentType *string
	SizeBytes   *int64
}
