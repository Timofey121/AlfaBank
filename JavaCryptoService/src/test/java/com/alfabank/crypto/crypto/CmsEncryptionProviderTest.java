package com.alfabank.crypto.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class CmsEncryptionProviderTest {

    private static CmsEncryptionProvider encryptionProvider;
    private static CmsDecryptionProvider decryptionProvider;
    private static KeyPair keyPair;
    private static X509Certificate cert;

    @BeforeAll
    static void setUp() throws Exception {
        encryptionProvider = new CmsEncryptionProvider();
        decryptionProvider = new CmsDecryptionProvider();
        keyPair = TestKeyHelper.generateRsaKeyPair();
        cert = TestKeyHelper.generateSelfSignedCert(keyPair);
    }

    @Test
    void encrypt_producesNonEmptyBytes() throws Exception {
        byte[] plaintext = "Secret message".getBytes();
        byte[] ciphertext = encryptionProvider.encrypt(plaintext, cert);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.length > 0);
        assertFalse(new String(ciphertext).contains("Secret message"));
    }

    @Test
    void encryptThenDecrypt_returnsOriginal() throws Exception {
        byte[] plaintext = "Hello, crypto world!".getBytes();
        byte[] ciphertext = encryptionProvider.encrypt(plaintext, cert);
        byte[] decrypted = decryptionProvider.decrypt(ciphertext, keyPair.getPrivate());
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decrypt_wrongKey_throwsException() throws Exception {
        byte[] plaintext = "Test".getBytes();
        byte[] ciphertext = encryptionProvider.encrypt(plaintext, cert);

        KeyPair wrongKeyPair = TestKeyHelper.generateRsaKeyPair();
        assertThrows(Exception.class, () ->
                decryptionProvider.decrypt(ciphertext, wrongKeyPair.getPrivate())
        );
    }
}
