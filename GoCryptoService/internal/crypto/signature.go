package crypto

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/binary"
	"errors"
	"fmt"
	"time"
)

const (
	SignModeAttached byte = 0x00
	SignModeDetached byte = 0x01
)

var ErrDetachedDataRequired = errors.New("original data required for DETACHED verification")

func Sign(data []byte, privateKey *rsa.PrivateKey, cert *x509.Certificate, attached bool) ([]byte, error) {
	digest := sha256.Sum256(data)
	sig, err := rsa.SignPSS(rand.Reader, privateKey, crypto.SHA256, digest[:], nil)
	if err != nil {
		return nil, fmt.Errorf("failed to sign: %w", err)
	}

	certDER := cert.Raw
	if len(certDER) > 0xFFFF || len(sig) > 0xFFFF {
		return nil, fmt.Errorf("certificate or signature too large to encode")
	}

	mode := SignModeDetached
	if attached {
		mode = SignModeAttached
	}

	envelope := make([]byte, 0, 1+2+len(certDER)+2+len(sig)+len(data))
	envelope = append(envelope, mode)
	envelope = binary.BigEndian.AppendUint16(envelope, uint16(len(certDER)))
	envelope = append(envelope, certDER...)
	envelope = binary.BigEndian.AppendUint16(envelope, uint16(len(sig)))
	envelope = append(envelope, sig...)
	if attached {
		envelope = append(envelope, data...)
	}
	return envelope, nil
}

type VerifyResult struct {
	Valid      bool
	Cert       *x509.Certificate
	NotBefore  time.Time
	NotAfter   time.Time
}

func Verify(envelope []byte, externalData []byte, attached bool) (VerifyResult, error) {
	if len(envelope) < 1+2 {
		return VerifyResult{}, ErrMalformedEnvelope
	}
	offset := 1
	certLen := int(binary.BigEndian.Uint16(envelope[offset : offset+2]))
	offset += 2
	if len(envelope) < offset+certLen+2 {
		return VerifyResult{}, ErrMalformedEnvelope
	}
	certDER := envelope[offset : offset+certLen]
	offset += certLen
	sigLen := int(binary.BigEndian.Uint16(envelope[offset : offset+2]))
	offset += 2
	if len(envelope) < offset+sigLen {
		return VerifyResult{}, ErrMalformedEnvelope
	}
	sig := envelope[offset : offset+sigLen]
	offset += sigLen

	var data []byte
	if attached {
		data = envelope[offset:]
	} else {
		if len(externalData) == 0 {
			return VerifyResult{}, ErrDetachedDataRequired
		}
		data = externalData
	}

	cert, err := x509.ParseCertificate(certDER)
	if err != nil {
		return VerifyResult{}, fmt.Errorf("failed to parse signer certificate: %w", err)
	}

	now := time.Now()
	if now.Before(cert.NotBefore) || now.After(cert.NotAfter) {
		return VerifyResult{Valid: false, Cert: cert, NotBefore: cert.NotBefore, NotAfter: cert.NotAfter}, nil
	}

	pub, ok := cert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return VerifyResult{Valid: false, Cert: cert, NotBefore: cert.NotBefore, NotAfter: cert.NotAfter}, nil
	}

	digest := sha256.Sum256(data)
	if err := rsa.VerifyPSS(pub, crypto.SHA256, digest[:], sig, nil); err != nil {
		return VerifyResult{Valid: false, Cert: cert, NotBefore: cert.NotBefore, NotAfter: cert.NotAfter}, nil
	}

	return VerifyResult{Valid: true, Cert: cert, NotBefore: cert.NotBefore, NotAfter: cert.NotAfter}, nil
}
