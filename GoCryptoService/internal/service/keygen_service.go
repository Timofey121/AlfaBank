package service

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"errors"
	"math/big"
	"strings"
	"time"

	"gocryptoservice/internal/keystore"
	"gocryptoservice/internal/model"
)

type GenerateKeystoreResult struct {
	Alias        string
	Subject      string
	SerialNumber string
	NotBefore    time.Time
	NotAfter     time.Time
	CertBase64   string
}

type KeyGenerationService struct {
	keystore *keystore.Manager
	audit    *AuditService
}

func NewKeyGenerationService(ks *keystore.Manager, audit *AuditService) *KeyGenerationService {
	return &KeyGenerationService{keystore: ks, audit: audit}
}

func (s *KeyGenerationService) Generate(ctx context.Context, alias, cn string, validityDays int) (GenerateKeystoreResult, error) {
	return Run(ctx, s.audit, model.OpKeyGeneration, nil, &alias, "KEY_GENERATION_FAILED",
		func(op *model.CryptoOperation) (GenerateKeystoreResult, []byte, error) {
			privKey, err := rsa.GenerateKey(rand.Reader, 2048)
			if err != nil {
				return GenerateKeystoreResult{}, nil, NewCryptoOperationError("key generation failed", err)
			}

			subject := parseNaiveDN("CN=" + cn + ",O=CryptoService,C=RU")

			notBefore := time.Now()
			notAfter := notBefore.Add(time.Duration(validityDays) * 24 * time.Hour)
			serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 160))
			if err != nil {
				return GenerateKeystoreResult{}, nil, NewCryptoOperationError("serial number generation failed", err)
			}

			template := &x509.Certificate{
				SerialNumber:          serial,
				Subject:               subject,
				NotBefore:             notBefore,
				NotAfter:              notAfter,
				SignatureAlgorithm:    x509.SHA256WithRSA,
				KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign,
				BasicConstraintsValid: true,
				IsCA:                  true,
			}
			certDER, err := x509.CreateCertificate(rand.Reader, template, template, &privKey.PublicKey, privKey)
			if err != nil {
				return GenerateKeystoreResult{}, nil, NewCryptoOperationError("certificate creation failed", err)
			}
			cert, err := x509.ParseCertificate(certDER)
			if err != nil {
				return GenerateKeystoreResult{}, nil, NewCryptoOperationError("failed to parse generated certificate", err)
			}

			if err := s.keystore.StoreKeyEntry(alias, privKey, cert); err != nil {
				if errors.Is(err, keystore.ErrAliasExists) {
					return GenerateKeystoreResult{}, nil, NewCryptoOperationError("failed to store key entry", err)
				}
				return GenerateKeystoreResult{}, nil, NewKeystoreError("failed to store key entry", err)
			}

			result := GenerateKeystoreResult{
				Alias:        alias,
				Subject:      cert.Subject.String(),
				SerialNumber: cert.SerialNumber.Text(16),
				NotBefore:    cert.NotBefore,
				NotAfter:     cert.NotAfter,
				CertBase64:   base64.StdEncoding.EncodeToString(certDER),
			}
			return result, certDER, nil
		})
}

func parseNaiveDN(dn string) pkix.Name {
	var name pkix.Name
	for _, part := range strings.Split(dn, ",") {
		kv := strings.SplitN(part, "=", 2)
		if len(kv) != 2 {
			continue
		}
		key := strings.ToUpper(strings.TrimSpace(kv[0]))
		value := strings.TrimSpace(kv[1])
		switch key {
		case "CN":
			name.CommonName = value
		case "O":
			name.Organization = append(name.Organization, value)
		case "OU":
			name.OrganizationalUnit = append(name.OrganizationalUnit, value)
		case "C":
			name.Country = append(name.Country, value)
		case "L":
			name.Locality = append(name.Locality, value)
		case "ST":
			name.Province = append(name.Province, value)
		case "STREET":
			name.StreetAddress = append(name.StreetAddress, value)
		case "POSTALCODE":
			name.PostalCode = append(name.PostalCode, value)
		}
	}
	return name
}
