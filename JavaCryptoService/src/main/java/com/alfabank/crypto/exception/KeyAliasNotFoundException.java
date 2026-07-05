package com.alfabank.crypto.exception;

public class KeyAliasNotFoundException extends KeystoreException {
    public KeyAliasNotFoundException(String alias) {
        super("Key alias not found in keystore: " + alias);
    }
}
