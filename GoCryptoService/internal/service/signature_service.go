package service

import (
	"context"
	"encoding/base64"
	"errors"
	"time"

	gocrypto "gocryptoservice/internal/crypto"
	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/model"
	"gocryptoservice/internal/repository"
)

type SignResult struct {
	OperationID       string
	Signature         string
	Mode              string
	SignerCertificate string
	Timestamp         time.Time
}

type VerifyResult struct {
	OperationID   string
	Valid         bool
	SignerSubject string
	SignerSerial  string
	CertNotBefore *time.Time
	CertNotAfter  *time.Time
	Timestamp     time.Time
}

type SignatureService struct {
	keystore *keystore.Manager
	audit    *AuditService
	details  *repository.SignatureDetailRepository
}

func NewSignatureService(ks *keystore.Manager, audit *AuditService, details *repository.SignatureDetailRepository) *SignatureService {
	return &SignatureService{keystore: ks, audit: audit, details: details}
}

func (s *SignatureService) Sign(ctx context.Context, dataB64, keyAlias, mode string) (SignResult, error) {
	alias := keyAlias
	return Run(ctx, s.audit, model.OpSign, nil, &alias, "SIGN_FAILED",
		func(op *model.CryptoOperation) (SignResult, []byte, error) {
			data, err := base64.StdEncoding.DecodeString(dataB64)
			if err != nil {
				return SignResult{}, nil, &ValidationError{Msg: "invalid base64 in data: " + err.Error()}
			}

			privKey, err := s.keystore.GetPrivateKey(keyAlias)
			if err != nil {
				if errors.Is(err, keystore.ErrKeyAliasNotFound) {
					return SignResult{}, nil, err
				}
				return SignResult{}, nil, NewKeystoreError("failed to load private key", err)
			}
			cert, err := s.keystore.GetCertificate(keyAlias)
			if err != nil {
				if errors.Is(err, keystore.ErrKeyAliasNotFound) {
					return SignResult{}, nil, err
				}
				return SignResult{}, nil, NewKeystoreError("failed to load certificate", err)
			}

			attached := mode == "ATTACHED"
			sigBytes, err := gocrypto.Sign(data, privKey, cert, attached)
			if err != nil {
				return SignResult{}, nil, NewCryptoOperationError("signing failed", err)
			}

			detail := &model.SignatureDetail{
				OperationID:   op.ID,
				SignMode:      strPtr(mode),
				SignerSubject: strPtr(cert.Subject.String()),
				SignerSerial:  strPtr(cert.SerialNumber.Text(16)),
				CertNotBefore: timePtr(cert.NotBefore),
				CertNotAfter:  timePtr(cert.NotAfter),
			}
			if err := s.details.Save(ctx, detail); err != nil {
				return SignResult{}, nil, err
			}

			result := SignResult{
				OperationID:       op.ID,
				Signature:         base64.StdEncoding.EncodeToString(sigBytes),
				Mode:              mode,
				SignerCertificate: base64.StdEncoding.EncodeToString(cert.Raw),
				Timestamp:         time.Now(),
			}
			return result, sigBytes, nil
		})
}

func (s *SignatureService) Verify(ctx context.Context, sigB64, dataB64, mode string) (VerifyResult, error) {
	return Run(ctx, s.audit, model.OpVerify, nil, nil, "VERIFY_FAILED",
		func(op *model.CryptoOperation) (VerifyResult, []byte, error) {
			sigBytes, err := base64.StdEncoding.DecodeString(sigB64)
			if err != nil {
				return VerifyResult{}, nil, &ValidationError{Msg: "invalid base64 in signature: " + err.Error()}
			}
			var data []byte
			if dataB64 != "" {
				data, err = base64.StdEncoding.DecodeString(dataB64)
				if err != nil {
					return VerifyResult{}, nil, &ValidationError{Msg: "invalid base64 in data: " + err.Error()}
				}
			}

			attached := mode == "ATTACHED"
			vr, err := gocrypto.Verify(sigBytes, data, attached)
			if err != nil {
				return VerifyResult{}, nil, NewCryptoOperationError("verification failed", err)
			}

			detail := &model.SignatureDetail{
				OperationID: op.ID,
				SignMode:    strPtr(mode),
				IsValid:     boolPtr(vr.Valid),
			}
			result := VerifyResult{
				OperationID: op.ID,
				Valid:       vr.Valid,
				Timestamp:   time.Now(),
			}
			if vr.Cert != nil {
				detail.SignerSubject = strPtr(vr.Cert.Subject.String())
				detail.SignerSerial = strPtr(vr.Cert.SerialNumber.Text(16))
				detail.CertNotBefore = timePtr(vr.NotBefore)
				detail.CertNotAfter = timePtr(vr.NotAfter)
				result.SignerSubject = vr.Cert.Subject.String()
				result.SignerSerial = vr.Cert.SerialNumber.Text(16)
				result.CertNotBefore = timePtr(vr.NotBefore)
				result.CertNotAfter = timePtr(vr.NotAfter)
			}
			if err := s.details.Save(ctx, detail); err != nil {
				return VerifyResult{}, nil, err
			}

			return result, nil, nil
		})
}

func timePtr(t time.Time) *time.Time { return &t }
func boolPtr(b bool) *bool           { return &b }
