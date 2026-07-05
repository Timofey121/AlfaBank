package keystore

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"golang.org/x/crypto/scrypt"
)

const (
	fileSuffix        = ".pem.enc"
	certPEMType       = "CERTIFICATE"
	encKeyPEMType     = "ENCRYPTED PRIVATE KEY DATA"
	scryptN           = 32768
	scryptR           = 8
	scryptP           = 1
	scryptKeyLen      = 32
	saltSize          = 16
	nonceSize         = 12
)

var (
	ErrKeyAliasNotFound = errors.New("key alias not found in keystore")
	ErrAliasExists      = errors.New("alias already exists in keystore")
)

type Entry struct {
	Certificate *x509.Certificate
	PrivateKey  *rsa.PrivateKey
}

type Manager struct {
	mu       sync.RWMutex
	entries  map[string]*Entry
	path     string
	password []byte
}

func New(path, password string) (*Manager, error) {
	m := &Manager{
		entries:  make(map[string]*Entry),
		path:     path,
		password: []byte(password),
	}
	if err := m.load(); err != nil {
		return nil, err
	}
	return m, nil
}

func (m *Manager) load() error {
	if err := os.MkdirAll(m.path, 0o700); err != nil {
		return fmt.Errorf("failed to create keystore directory: %w", err)
	}
	entries, err := os.ReadDir(m.path)
	if err != nil {
		return fmt.Errorf("failed to read keystore directory: %w", err)
	}
	for _, de := range entries {
		if de.IsDir() || !strings.HasSuffix(de.Name(), fileSuffix) {
			continue
		}
		alias := strings.TrimSuffix(de.Name(), fileSuffix)
		entry, err := m.readEntryFile(filepath.Join(m.path, de.Name()))
		if err != nil {
			return fmt.Errorf("failed to load keystore entry %q: %w", alias, err)
		}
		m.entries[alias] = entry
	}
	return nil
}

func (m *Manager) GetPrivateKey(alias string) (*rsa.PrivateKey, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	e, ok := m.entries[alias]
	if !ok {
		return nil, fmt.Errorf("%w: %s", ErrKeyAliasNotFound, alias)
	}
	return e.PrivateKey, nil
}

func (m *Manager) GetCertificate(alias string) (*x509.Certificate, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	e, ok := m.entries[alias]
	if !ok {
		return nil, fmt.Errorf("%w: %s", ErrKeyAliasNotFound, alias)
	}
	return e.Certificate, nil
}

func (m *Manager) StoreKeyEntry(alias string, privateKey *rsa.PrivateKey, cert *x509.Certificate) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, exists := m.entries[alias]; exists {
		return fmt.Errorf("%w: %s", ErrAliasExists, alias)
	}
	path := filepath.Join(m.path, alias+fileSuffix)
	if err := m.writeEntryFile(path, cert, privateKey); err != nil {
		return fmt.Errorf("failed to persist keystore entry: %w", err)
	}
	m.entries[alias] = &Entry{Certificate: cert, PrivateKey: privateKey}
	return nil
}

func (m *Manager) writeEntryFile(path string, cert *x509.Certificate, privateKey *rsa.PrivateKey) error {
	keyDER, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		return fmt.Errorf("failed to marshal private key: %w", err)
	}

	salt := make([]byte, saltSize)
	if _, err := rand.Read(salt); err != nil {
		return fmt.Errorf("failed to generate salt: %w", err)
	}
	nonce := make([]byte, nonceSize)
	if _, err := rand.Read(nonce); err != nil {
		return fmt.Errorf("failed to generate nonce: %w", err)
	}
	encKey, err := scrypt.Key(m.password, salt, scryptN, scryptR, scryptP, scryptKeyLen)
	if err != nil {
		return fmt.Errorf("failed to derive encryption key: %w", err)
	}
	block, err := aes.NewCipher(encKey)
	if err != nil {
		return err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return err
	}
	ciphertext := gcm.Seal(nil, nonce, keyDER, nil)

	blockBody := make([]byte, 0, saltSize+nonceSize+len(ciphertext))
	blockBody = append(blockBody, salt...)
	blockBody = append(blockBody, nonce...)
	blockBody = append(blockBody, ciphertext...)

	var out []byte
	out = append(out, pem.EncodeToMemory(&pem.Block{Type: certPEMType, Bytes: cert.Raw})...)
	out = append(out, pem.EncodeToMemory(&pem.Block{Type: encKeyPEMType, Bytes: blockBody})...)

	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, "keystore-*.tmp")
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}
	tmpPath := tmp.Name()
	defer os.Remove(tmpPath)

	if _, err := tmp.Write(out); err != nil {
		tmp.Close()
		return fmt.Errorf("failed to write temp file: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("failed to close temp file: %w", err)
	}
	if err := os.Rename(tmpPath, path); err != nil {
		return fmt.Errorf("failed to move temp file into place: %w", err)
	}
	return nil
}

func (m *Manager) readEntryFile(path string) (*Entry, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	certBlock, rest := pem.Decode(raw)
	if certBlock == nil || certBlock.Type != certPEMType {
		return nil, fmt.Errorf("missing or invalid %s PEM block", certPEMType)
	}
	keyBlock, _ := pem.Decode(rest)
	if keyBlock == nil || keyBlock.Type != encKeyPEMType {
		return nil, fmt.Errorf("missing or invalid %s PEM block", encKeyPEMType)
	}

	cert, err := x509.ParseCertificate(certBlock.Bytes)
	if err != nil {
		return nil, fmt.Errorf("failed to parse certificate: %w", err)
	}

	body := keyBlock.Bytes
	if len(body) < saltSize+nonceSize {
		return nil, fmt.Errorf("encrypted private key block is truncated")
	}
	salt := body[:saltSize]
	nonce := body[saltSize : saltSize+nonceSize]
	ciphertext := body[saltSize+nonceSize:]

	encKey, err := scrypt.Key(m.password, salt, scryptN, scryptR, scryptP, scryptKeyLen)
	if err != nil {
		return nil, fmt.Errorf("failed to derive encryption key: %w", err)
	}
	block, err := aes.NewCipher(encKey)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	keyDER, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt private key (wrong password?): %w", err)
	}

	key, err := x509.ParsePKCS8PrivateKey(keyDER)
	if err != nil {
		return nil, fmt.Errorf("failed to parse private key: %w", err)
	}
	rsaKey, ok := key.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("private key is not RSA")
	}

	return &Entry{Certificate: cert, PrivateKey: rsaKey}, nil
}
