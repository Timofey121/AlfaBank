package com.alfabank.crypto.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CmsSignatureProviderTest {

    private static CmsSignatureProvider signatureProvider;
    private static CmsVerificationProvider verificationProvider;
    private static KeyPair keyPair;
    private static X509Certificate cert;

    @BeforeAll
    static void setUp() throws Exception {
        signatureProvider = new CmsSignatureProvider();
        verificationProvider = new CmsVerificationProvider();
        keyPair = TestKeyHelper.generateRsaKeyPair();
        cert = TestKeyHelper.generateSelfSignedCert(keyPair);
    }

    @Test
    void signAttached_producesNonEmptyBytes() throws Exception {
        byte[] data = "Hello, World!".getBytes();
        byte[] signature = signatureProvider.sign(data, keyPair.getPrivate(), cert, true);
        assertNotNull(signature);
        assertTrue(signature.length > 0);
    }

    @Test
    void signDetached_producesNonEmptyBytes() throws Exception {
        byte[] data = "Test document".getBytes();
        byte[] signature = signatureProvider.sign(data, keyPair.getPrivate(), cert, false);
        assertNotNull(signature);
        assertTrue(signature.length > 0);
    }

    @Test
    void verifyAttached_validSignature_returnsTrue() throws Exception {
        byte[] data = "Attached document".getBytes();
        byte[] signature = signatureProvider.sign(data, keyPair.getPrivate(), cert, true);

        CmsVerificationProvider.VerifyResult result = verificationProvider.verify(signature, null, true);

        assertTrue(result.valid());
        assertNotNull(result.signerCert());
    }

    @Test
    void verifyDetached_validSignature_returnsTrue() throws Exception {
        byte[] data = "Detached document".getBytes();
        byte[] signature = signatureProvider.sign(data, keyPair.getPrivate(), cert, false);

        CmsVerificationProvider.VerifyResult result = verificationProvider.verify(signature, data, false);

        assertTrue(result.valid());
        assertNotNull(result.signerCert());
    }

    @Test
    void verifyDetached_tamperedData_returnsFalse() throws Exception {
        byte[] data = "Original data".getBytes();
        byte[] signature = signatureProvider.sign(data, keyPair.getPrivate(), cert, false);
        byte[] tamperedData = "Tampered data".getBytes();

        CmsVerificationProvider.VerifyResult result =
                verificationProvider.verify(signature, tamperedData, false);

        assertFalse(result.valid());
    }
}
