package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/binary"
	"errors"
	"fmt"
)

const envelopeVersion byte = 0x01

const nonceSize = 12

var ErrMalformedEnvelope = errors.New("ciphertext envelope is malformed")

func Encrypt(plaintext []byte, recipientCert *x509.Certificate) ([]byte, error) {
	pub, ok := recipientCert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("recipient certificate does not contain an RSA public key")
	}

	aesKey := make([]byte, 32)
	if _, err := rand.Read(aesKey); err != nil {
		return nil, fmt.Errorf("failed to generate AES key: %w", err)
	}
	nonce := make([]byte, nonceSize)
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("failed to generate nonce: %w", err)
	}

	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return nil, fmt.Errorf("failed to init AES cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("failed to init GCM: %w", err)
	}
	ciphertext := gcm.Seal(nil, nonce, plaintext, nil)

	wrappedKey, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, pub, aesKey, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to wrap AES key: %w", err)
	}
	if len(wrappedKey) > 0xFFFF {
		return nil, fmt.Errorf("wrapped key too large: %d bytes", len(wrappedKey))
	}

	envelope := make([]byte, 0, 1+2+len(wrappedKey)+nonceSize+len(ciphertext))
	envelope = append(envelope, envelopeVersion)
	envelope = binary.BigEndian.AppendUint16(envelope, uint16(len(wrappedKey)))
	envelope = append(envelope, wrappedKey...)
	envelope = append(envelope, nonce...)
	envelope = append(envelope, ciphertext...)
	return envelope, nil
}

func Decrypt(envelope []byte, privateKey *rsa.PrivateKey) ([]byte, error) {
	if len(envelope) < 1+2 || envelope[0] != envelopeVersion {
		return nil, ErrMalformedEnvelope
	}
	wrappedKeyLen := int(binary.BigEndian.Uint16(envelope[1:3]))
	offset := 3
	if len(envelope) < offset+wrappedKeyLen+nonceSize {
		return nil, ErrMalformedEnvelope
	}
	wrappedKey := envelope[offset : offset+wrappedKeyLen]
	offset += wrappedKeyLen
	nonce := envelope[offset : offset+nonceSize]
	offset += nonceSize
	ciphertext := envelope[offset:]

	aesKey, err := rsa.DecryptOAEP(sha256.New(), rand.Reader, privateKey, wrappedKey, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to unwrap AES key: %w", err)
	}
	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return nil, fmt.Errorf("failed to init AES cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("failed to init GCM: %w", err)
	}
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt: %w", err)
	}
	return plaintext, nil
}
