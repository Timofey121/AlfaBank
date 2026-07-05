package service

import (
	"context"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"time"

	gocrypto "gocryptoservice/internal/crypto"
	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/model"
	"gocryptoservice/internal/repository"

	"crypto/x509"
)

type EncryptResult struct {
	OperationID string
	Ciphertext  string
	Timestamp   time.Time
}

type DecryptResult struct {
	OperationID string
	Plaintext   string
	Filename    string
	Timestamp   time.Time
}

type EncryptionService struct {
	keystore *keystore.Manager
	audit    *AuditService
	details  *repository.EncryptionDetailRepository
}

func NewEncryptionService(ks *keystore.Manager, audit *AuditService, details *repository.EncryptionDetailRepository) *EncryptionService {
	return &EncryptionService{keystore: ks, audit: audit, details: details}
}

func (s *EncryptionService) Encrypt(ctx context.Context, plaintextB64, recipientCertB64, filename string) (EncryptResult, error) {
	return Run(ctx, s.audit, model.OpEncrypt, nil, nil, "ENCRYPT_FAILED",
		func(op *model.CryptoOperation) (EncryptResult, []byte, error) {
			plaintext, err := base64.StdEncoding.DecodeString(plaintextB64)
			if err != nil {
				return EncryptResult{}, nil, &ValidationError{Msg: "invalid base64 in plaintext: " + err.Error()}
			}
			certDER, err := base64.StdEncoding.DecodeString(recipientCertB64)
			if err != nil {
				return EncryptResult{}, nil, &ValidationError{Msg: "invalid base64 in recipientCertificate: " + err.Error()}
			}
			cert, err := x509.ParseCertificate(certDER)
			if err != nil {
				return EncryptResult{}, nil, NewCryptoOperationError("recipient certificate is invalid", err)
			}
			now := time.Now()
			if now.Before(cert.NotBefore) || now.After(cert.NotAfter) {
				return EncryptResult{}, nil, &CryptoOperationError{Msg: "recipient certificate is not valid: outside its validity period"}
			}

			packed := packWithFilename(plaintext, filename)
			ciphertext, err := gocrypto.Encrypt(packed, cert)
			if err != nil {
				return EncryptResult{}, nil, NewCryptoOperationError("encryption failed", err)
			}

			detail := &model.EncryptionDetail{
				OperationID: op.ID,
				Algorithm:   strPtr("AES256_GCM"),
				RecipientDN: strPtr(cert.Subject.String()),
				InputSize:   intPtr(len(plaintext)),
				OutputSize:  intPtr(len(ciphertext)),
			}
			if filename != "" {
				detail.OriginalFilename = strPtr(filename)
			}
			if err := s.details.Save(ctx, detail); err != nil {
				return EncryptResult{}, nil, err
			}

			result := EncryptResult{
				OperationID: op.ID,
				Ciphertext:  base64.StdEncoding.EncodeToString(ciphertext),
				Timestamp:   time.Now(),
			}
			return result, ciphertext, nil
		})
}

func (s *EncryptionService) Decrypt(ctx context.Context, ciphertextB64, keyAlias string) (DecryptResult, error) {
	alias := keyAlias
	return Run(ctx, s.audit, model.OpDecrypt, nil, &alias, "DECRYPT_FAILED",
		func(op *model.CryptoOperation) (DecryptResult, []byte, error) {
			ciphertext, err := base64.StdEncoding.DecodeString(ciphertextB64)
			if err != nil {
				return DecryptResult{}, nil, &ValidationError{Msg: "invalid base64 in ciphertext: " + err.Error()}
			}

			privKey, err := s.keystore.GetPrivateKey(keyAlias)
			if err != nil {
				if errors.Is(err, keystore.ErrKeyAliasNotFound) {
					return DecryptResult{}, nil, err
				}
				return DecryptResult{}, nil, NewKeystoreError("failed to load private key", err)
			}

			decrypted, err := gocrypto.Decrypt(ciphertext, privKey)
			if err != nil {
				return DecryptResult{}, nil, NewCryptoOperationError("decryption failed", err)
			}

			filename, content, err := unpackFilename(decrypted)
			if err != nil {
				return DecryptResult{}, nil, NewCryptoOperationError("decrypted content is malformed", err)
			}

			result := DecryptResult{
				OperationID: op.ID,
				Plaintext:   base64.StdEncoding.EncodeToString(content),
				Filename:    filename,
				Timestamp:   time.Now(),
			}
			return result, content, nil
		})
}

const filenamePackMarker byte = 0x01

func packWithFilename(content []byte, filename string) []byte {
	nameBytes := []byte(filename)
	packed := make([]byte, 0, 1+4+len(nameBytes)+len(content))
	packed = append(packed, filenamePackMarker)
	packed = binary.BigEndian.AppendUint32(packed, uint32(len(nameBytes)))
	packed = append(packed, nameBytes...)
	packed = append(packed, content...)
	return packed
}

func unpackFilename(packed []byte) (filename string, content []byte, err error) {
	if len(packed) == 0 || packed[0] != filenamePackMarker {
		return "", packed, nil
	}
	if len(packed) < 1+4 {
		return "", nil, errors.New("missing filename header")
	}
	nameLen := int(binary.BigEndian.Uint32(packed[1:5]))
	if nameLen < 0 || nameLen > len(packed)-1-4 {
		return "", nil, errors.New("invalid filename header")
	}
	name := packed[5 : 5+nameLen]
	rest := packed[5+nameLen:]
	return string(name), rest, nil
}

func strPtr(s string) *string { return &s }
func intPtr(i int) *int       { return &i }
