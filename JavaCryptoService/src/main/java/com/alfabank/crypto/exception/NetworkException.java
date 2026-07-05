package com.alfabank.crypto.exception;

public class NetworkException extends CryptoAppException {
    public NetworkException(String message) { super(message); }
    public NetworkException(String message, Throwable cause) { super(message, cause); }
}
