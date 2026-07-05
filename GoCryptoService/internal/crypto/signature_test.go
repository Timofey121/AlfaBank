package crypto

import (
	"testing"
	"time"
)

func TestSignVerifyAttached(t *testing.T) {
	privKey, cert := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	data := []byte("attached message")

	envelope, err := Sign(data, privKey, cert, true)
	if err != nil {
		t.Fatalf("Sign failed: %v", err)
	}

	result, err := Verify(envelope, nil, true)
	if err != nil {
		t.Fatalf("Verify failed: %v", err)
	}
	if !result.Valid {
		t.Error("expected valid signature")
	}
	if result.Cert.SerialNumber.Cmp(cert.SerialNumber) != 0 {
		t.Error("verify returned unexpected certificate")
	}
}

func TestSignVerifyDetached(t *testing.T) {
	privKey, cert := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	data := []byte("detached message")

	envelope, err := Sign(data, privKey, cert, false)
	if err != nil {
		t.Fatalf("Sign failed: %v", err)
	}

	result, err := Verify(envelope, data, false)
	if err != nil {
		t.Fatalf("Verify failed: %v", err)
	}
	if !result.Valid {
		t.Error("expected valid signature")
	}

	if _, err := Verify(envelope, nil, false); err != ErrDetachedDataRequired {
		t.Errorf("expected ErrDetachedDataRequired, got %v", err)
	}
}

func TestVerifyTamperedDataFails(t *testing.T) {
	privKey, cert := generateTestCert(t, time.Now().Add(-time.Hour), time.Now().Add(time.Hour))
	envelope, err := Sign([]byte("original"), privKey, cert, true)
	if err != nil {
		t.Fatalf("Sign failed: %v", err)
	}
	envelope[len(envelope)-1] ^= 0xFF

	result, err := Verify(envelope, nil, true)
	if err != nil {
		t.Fatalf("Verify returned error instead of invalid result: %v", err)
	}
	if result.Valid {
		t.Error("expected invalid signature after tampering")
	}
}

func TestVerifyExpiredCertIsInvalid(t *testing.T) {
	privKey, cert := generateTestCert(t, time.Now().Add(-2*time.Hour), time.Now().Add(-time.Hour))
	envelope, err := Sign([]byte("data"), privKey, cert, true)
	if err != nil {
		t.Fatalf("Sign failed: %v", err)
	}

	result, err := Verify(envelope, nil, true)
	if err != nil {
		t.Fatalf("Verify failed: %v", err)
	}
	if result.Valid {
		t.Error("expected invalid result for expired certificate")
	}
	if result.Cert == nil {
		t.Error("expected certificate to be returned even when expired")
	}
}
