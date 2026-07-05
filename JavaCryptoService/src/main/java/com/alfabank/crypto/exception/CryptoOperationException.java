package com.alfabank.crypto.exception;

public class CryptoOperationException extends CryptoAppException {
    public CryptoOperationException(String message) { super(message); }
    public CryptoOperationException(String message, Throwable cause) { super(message, cause); }
}
