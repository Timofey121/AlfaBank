package com.alfabank.crypto.exception;

public class KeystoreException extends CryptoAppException {
    public KeystoreException(String message) { super(message); }
    public KeystoreException(String message, Throwable cause) { super(message, cause); }
}
