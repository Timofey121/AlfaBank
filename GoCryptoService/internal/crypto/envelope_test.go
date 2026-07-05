package crypto

import (
	"bytes"
	"testing"
	"time"
)

func TestEncryptDecryptRoundTrip(t *testing.T) {
	privKey, cert := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	plaintext := []byte("the quick brown fox jumps over the lazy dog")

	envelope, err := Encrypt(plaintext, cert)
	if err != nil {
		t.Fatalf("Encrypt failed: %v", err)
	}
	if bytes.Contains(envelope, plaintext) {
		t.Errorf("envelope should not contain plaintext verbatim")
	}

	decrypted, err := Decrypt(envelope, privKey)
	if err != nil {
		t.Fatalf("Decrypt failed: %v", err)
	}
	if !bytes.Equal(decrypted, plaintext) {
		t.Errorf("Decrypt() = %q, want %q", decrypted, plaintext)
	}
}

func TestDecryptWithWrongKeyFails(t *testing.T) {
	_, cert := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	wrongKey, _ := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))

	envelope, err := Encrypt([]byte("secret"), cert)
	if err != nil {
		t.Fatalf("Encrypt failed: %v", err)
	}
	if _, err := Decrypt(envelope, wrongKey); err == nil {
		t.Error("Decrypt with wrong key should fail")
	}
}

func TestDecryptMalformedEnvelope(t *testing.T) {
	privKey, _ := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	if _, err := Decrypt([]byte{0xFF, 0x01}, privKey); err == nil {
		t.Error("Decrypt with malformed envelope should fail")
	}
	if _, err := Decrypt(nil, privKey); err == nil {
		t.Error("Decrypt with empty envelope should fail")
	}
}
