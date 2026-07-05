package service

import (
	"context"
	"fmt"
	"time"

	"gocryptoservice/internal/model"
	"gocryptoservice/internal/repository"
)

const maxPageSize = 200

type OperationRecord struct {
	ID        string
	Type      string
	Status    string
	CreatedAt time.Time
	KeyAlias  string
}

type SignatureInfo struct {
	SignMode      string
	SignerSubject string
	SignerSerial  string
	CertNotBefore *time.Time
	CertNotAfter  *time.Time
	IsValid       *bool
}

type EncryptionInfo struct {
	Algorithm        string
	RecipientDN      string
	InputSize        *int
	OutputSize       *int
	OriginalFilename string
}

type FetchInfo struct {
	SourceURL   string
	HTTPStatus  *int
	ContentType string
	SizeBytes   *int64
}

type OperationDetail struct {
	ID         string
	Type       string
	Status     string
	KeyAlias   string
	InputHash  string
	OutputHash string
	ErrorCode  string
	ErrorMsg   string
	DurationMs *int64
	CreatedAt  time.Time
	Signature  *SignatureInfo
	Encryption *EncryptionInfo
	Fetch      *FetchInfo
}

type OperationsService struct {
	ops     *repository.OperationsRepository
	sigs    *repository.SignatureDetailRepository
	encs    *repository.EncryptionDetailRepository
	fetches *repository.FetchDetailRepository
}

func NewOperationsService(ops *repository.OperationsRepository, sigs *repository.SignatureDetailRepository,
	encs *repository.EncryptionDetailRepository, fetches *repository.FetchDetailRepository) *OperationsService {
	return &OperationsService{ops: ops, sigs: sigs, encs: encs, fetches: fetches}
}

func (s *OperationsService) List(ctx context.Context, page, size int, typeFilter string) ([]OperationRecord, int, error) {
	var typePtr *model.OperationType
	if typeFilter != "" {
		t, ok := model.ParseOperationType(typeFilter)
		if !ok {
			return nil, 0, &ValidationError{Msg: fmt.Sprintf("invalid operation type: %s", typeFilter)}
		}
		typePtr = &t
	}
	cappedSize := size
	if cappedSize > maxPageSize {
		cappedSize = maxPageSize
	}
	ops, total, err := s.ops.List(ctx, typePtr, page, cappedSize)
	if err != nil {
		return nil, 0, err
	}
	records := make([]OperationRecord, 0, len(ops))
	for _, op := range ops {
		records = append(records, toRecord(op))
	}
	return records, total, nil
}

func (s *OperationsService) GetByID(ctx context.Context, id string) (*OperationDetail, error) {
	op, err := s.ops.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if op == nil {
		return nil, nil
	}

	detail := toDetail(*op)
	switch op.Type {
	case model.OpSign, model.OpVerify:
		d, err := s.sigs.FindByID(ctx, op.ID)
		if err != nil {
			return nil, err
		}
		if d != nil {
			detail.Signature = &SignatureInfo{
				SignMode:      deref(d.SignMode),
				SignerSubject: deref(d.SignerSubject),
				SignerSerial:  deref(d.SignerSerial),
				CertNotBefore: d.CertNotBefore,
				CertNotAfter:  d.CertNotAfter,
				IsValid:       d.IsValid,
			}
		}
	case model.OpEncrypt, model.OpDecrypt:
		d, err := s.encs.FindByID(ctx, op.ID)
		if err != nil {
			return nil, err
		}
		if d != nil {
			detail.Encryption = &EncryptionInfo{
				Algorithm:        deref(d.Algorithm),
				RecipientDN:      deref(d.RecipientDN),
				InputSize:        d.InputSize,
				OutputSize:       d.OutputSize,
				OriginalFilename: deref(d.OriginalFilename),
			}
		}
	case model.OpFetch:
		d, err := s.fetches.FindByID(ctx, op.ID)
		if err != nil {
			return nil, err
		}
		if d != nil {
			detail.Fetch = &FetchInfo{
				SourceURL:   d.SourceURL,
				HTTPStatus:  d.HTTPStatus,
				ContentType: deref(d.ContentType),
				SizeBytes:   d.SizeBytes,
			}
		}
	}
	return &detail, nil
}

func toRecord(op model.CryptoOperation) OperationRecord {
	return OperationRecord{
		ID:        op.ID,
		Type:      string(op.Type),
		Status:    op.Status,
		CreatedAt: op.CreatedAt,
		KeyAlias:  deref(op.KeyAlias),
	}
}

func toDetail(op model.CryptoOperation) OperationDetail {
	return OperationDetail{
		ID:         op.ID,
		Type:       string(op.Type),
		Status:     op.Status,
		KeyAlias:   deref(op.KeyAlias),
		InputHash:  deref(op.InputHash),
		OutputHash: deref(op.OutputHash),
		ErrorCode:  deref(op.ErrorCode),
		ErrorMsg:   deref(op.ErrorMsg),
		DurationMs: op.DurationMs,
		CreatedAt:  op.CreatedAt,
	}
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
