package com.alfabank.crypto.crypto;

import com.alfabank.crypto.exception.CryptoOperationException;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class DigestProvider implements CryptoProvider {

    public String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256", "BC");
            return Hex.toHexString(md.digest(data));
        } catch (Exception e) {
            throw new CryptoOperationException("Hash computation failed: " + e.getMessage(), e);
        }
    }
}
