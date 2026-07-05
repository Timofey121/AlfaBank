package service

import "fmt"

type CryptoOperationError struct {
	Msg   string
	Cause error
}

func (e *CryptoOperationError) Error() string { return e.Msg }
func (e *CryptoOperationError) Unwrap() error { return e.Cause }

func NewCryptoOperationError(prefix string, cause error) *CryptoOperationError {
	return &CryptoOperationError{Msg: fmt.Sprintf("%s: %s", prefix, cause), Cause: cause}
}

type NetworkError struct {
	Msg   string
	Cause error
}

func (e *NetworkError) Error() string { return e.Msg }
func (e *NetworkError) Unwrap() error { return e.Cause }

type ValidationError struct {
	Msg string
}

func (e *ValidationError) Error() string { return e.Msg }

type KeystoreError struct {
	Msg   string
	Cause error
}

func (e *KeystoreError) Error() string { return e.Msg }
func (e *KeystoreError) Unwrap() error { return e.Cause }

func NewKeystoreError(prefix string, cause error) *KeystoreError {
	return &KeystoreError{Msg: fmt.Sprintf("%s: %s", prefix, cause), Cause: cause}
}
