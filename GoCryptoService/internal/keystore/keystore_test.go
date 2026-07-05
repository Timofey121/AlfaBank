package keystore

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"errors"
	"math/big"
	"testing"
	"time"
)

func generateTestCert(t *testing.T) (*rsa.PrivateKey, *x509.Certificate) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("failed to generate key: %v", err)
	}
	template := &x509.Certificate{
		SerialNumber:          big.NewInt(42),
		Subject:               pkix.Name{CommonName: "keystore-test"},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		SignatureAlgorithm:    x509.SHA256WithRSA,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("failed to create certificate: %v", err)
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatalf("failed to parse certificate: %v", err)
	}
	return key, cert
}

func TestStoreAndReload(t *testing.T) {
	dir := t.TempDir()
	m, err := New(dir, "correct-password")
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}

	privKey, cert := generateTestCert(t)
	if err := m.StoreKeyEntry("my-alias", privKey, cert); err != nil {
		t.Fatalf("StoreKeyEntry failed: %v", err)
	}

	reloaded, err := New(dir, "correct-password")
	if err != nil {
		t.Fatalf("reload New failed: %v", err)
	}

	gotKey, err := reloaded.GetPrivateKey("my-alias")
	if err != nil {
		t.Fatalf("GetPrivateKey failed: %v", err)
	}
	if gotKey.D.Cmp(privKey.D) != 0 {
		t.Error("reloaded private key does not match original")
	}

	gotCert, err := reloaded.GetCertificate("my-alias")
	if err != nil {
		t.Fatalf("GetCertificate failed: %v", err)
	}
	if gotCert.SerialNumber.Cmp(cert.SerialNumber) != 0 {
		t.Error("reloaded certificate does not match original")
	}
}

func TestAliasAlreadyExists(t *testing.T) {
	dir := t.TempDir()
	m, err := New(dir, "pw")
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	privKey, cert := generateTestCert(t)
	if err := m.StoreKeyEntry("dup", privKey, cert); err != nil {
		t.Fatalf("StoreKeyEntry failed: %v", err)
	}
	err = m.StoreKeyEntry("dup", privKey, cert)
	if !errors.Is(err, ErrAliasExists) {
		t.Errorf("expected ErrAliasExists, got %v", err)
	}
}

func TestAliasNotFound(t *testing.T) {
	dir := t.TempDir()
	m, err := New(dir, "pw")
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	if _, err := m.GetPrivateKey("missing"); !errors.Is(err, ErrKeyAliasNotFound) {
		t.Errorf("expected ErrKeyAliasNotFound, got %v", err)
	}
	if _, err := m.GetCertificate("missing"); !errors.Is(err, ErrKeyAliasNotFound) {
		t.Errorf("expected ErrKeyAliasNotFound, got %v", err)
	}
}

func TestWrongPasswordFailsToReload(t *testing.T) {
	dir := t.TempDir()
	m, err := New(dir, "right-password")
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	privKey, cert := generateTestCert(t)
	if err := m.StoreKeyEntry("alias", privKey, cert); err != nil {
		t.Fatalf("StoreKeyEntry failed: %v", err)
	}

	if _, err := New(dir, "wrong-password"); err == nil {
		t.Error("expected error when reloading with wrong password")
	}
}
