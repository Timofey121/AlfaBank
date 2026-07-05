package com.alfabank.crypto.exception;

public class CryptoAppException extends RuntimeException {
    public CryptoAppException(String message) { super(message); }
    public CryptoAppException(String message, Throwable cause) { super(message, cause); }
}
