package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.CmsDecryptionProvider;
import com.alfabank.crypto.crypto.CmsEncryptionProvider;
import com.alfabank.crypto.crypto.TestKeyHelper;
import com.alfabank.crypto.dto.request.DecryptRequest;
import com.alfabank.crypto.dto.request.EncryptRequest;
import com.alfabank.crypto.dto.response.DecryptResponse;
import com.alfabank.crypto.dto.response.EncryptResponse;
import com.alfabank.crypto.exception.CryptoAppException;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.repository.EncryptionDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    private static final String KEY_ALIAS = "test-alias";

    @Mock private KeystoreManager keystoreManager;
    @Mock private AuditService auditService;
    @Mock private EncryptionDetailRepository detailRepository;

    private CmsEncryptionProvider encryptionProvider;
    private CmsDecryptionProvider decryptionProvider;

    private EncryptionService encryptionService;

    private CryptoOperation pendingOp;
    private KeyPair keyPair;
    private X509Certificate cert;
    private String certBase64;

    @BeforeEach
    void setUp() throws Exception {
        encryptionProvider = new CmsEncryptionProvider();
        decryptionProvider = new CmsDecryptionProvider();
        encryptionService = new EncryptionService(
                encryptionProvider, decryptionProvider, keystoreManager,
                new AuditedOperation(auditService), detailRepository);

        pendingOp = new CryptoOperation();
        pendingOp.setId("enc-op-id");
        pendingOp.setType("ENCRYPT");
        lenient().when(auditService.createPending(any(), any(), any())).thenReturn(pendingOp);

        keyPair = TestKeyHelper.generateRsaKeyPair();
        cert = TestKeyHelper.generateSelfSignedCert(keyPair);
        certBase64 = Base64.getEncoder().encodeToString(cert.getEncoded());

        lenient().when(keystoreManager.getPrivateKey(KEY_ALIAS)).thenReturn(keyPair.getPrivate());
    }

    private EncryptResponse encrypt(String plaintext, String filename) {
        return encryptionService.encrypt(new EncryptRequest(
                Base64.getEncoder().encodeToString(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                certBase64, filename));
    }

    private EncryptResponse encryptBytes(byte[] plaintext, String filename) {
        return encryptionService.encrypt(new EncryptRequest(
                Base64.getEncoder().encodeToString(plaintext), certBase64, filename));
    }

    private DecryptResponse decrypt(String ciphertextBase64) {
        return encryptionService.decrypt(new DecryptRequest(ciphertextBase64, KEY_ALIAS));
    }

    @Test
    void encryptThenDecrypt_withFilename_roundTripsFilenameAndContent() {
        String plaintext = "Hello, this is a secret document.";

        EncryptResponse encryptResponse = encrypt(plaintext, "invoice.pdf");
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertEquals("invoice.pdf", decryptResponse.filename());
        assertEquals(plaintext, new String(
                Base64.getDecoder().decode(decryptResponse.plaintext()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void encryptThenDecrypt_nullFilename_filenameAbsentContentCorrect() {
        String plaintext = "No filename here";

        EncryptResponse encryptResponse = encrypt(plaintext, null);
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertNull(decryptResponse.filename());
        assertEquals(plaintext, new String(
                Base64.getDecoder().decode(decryptResponse.plaintext()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void encryptThenDecrypt_blankFilename_filenameAbsentContentCorrect() {
        String plaintext = "Blank filename treated as none";

        EncryptResponse encryptResponse = encrypt(plaintext, "   ");
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertNull(decryptResponse.filename());
        assertEquals(plaintext, new String(
                Base64.getDecoder().decode(decryptResponse.plaintext()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void decrypt_legacyPlaintextWithoutMarker_returnsWholeContentWithNoFilenameAndDoesNotThrow() {
        byte[] legacyPlaintext = "legacy content with no packed filename".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertNotEquals((byte) 0x01, legacyPlaintext[0]);

        byte[] legacyCiphertext = encryptionProvider.encrypt(legacyPlaintext, cert);
        String legacyCiphertextBase64 = Base64.getEncoder().encodeToString(legacyCiphertext);

        DecryptResponse decryptResponse = assertDoesNotThrow(() -> decrypt(legacyCiphertextBase64));

        assertNull(decryptResponse.filename());
        assertArrayEquals(legacyPlaintext, Base64.getDecoder().decode(decryptResponse.plaintext()));
    }

    @Test
    void encryptThenDecrypt_emptyContentWithFilename_roundTrips() {
        byte[] emptyContent = new byte[0];

        EncryptResponse encryptResponse = encryptBytes(emptyContent, "empty.txt");
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertEquals("empty.txt", decryptResponse.filename());
        assertArrayEquals(emptyContent, Base64.getDecoder().decode(decryptResponse.plaintext()));
    }

    @Test
    void encryptThenDecrypt_veryShortContentWithFilename_roundTrips() {
        byte[] shortContent = "x".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        EncryptResponse encryptResponse = encryptBytes(shortContent, "a.txt");
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertEquals("a.txt", decryptResponse.filename());
        assertArrayEquals(shortContent, Base64.getDecoder().decode(decryptResponse.plaintext()));
    }

    @Test
    void encryptThenDecrypt_utf8Filename_roundTripsExactly() {
        String plaintext = "content with a unicode filename";
        String utf8Filename = "отчёт_文件_2024.pdf";

        EncryptResponse encryptResponse = encrypt(plaintext, utf8Filename);
        DecryptResponse decryptResponse = decrypt(encryptResponse.ciphertext());

        assertEquals(utf8Filename, decryptResponse.filename());
        assertEquals(plaintext, new String(
                Base64.getDecoder().decode(decryptResponse.plaintext()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void encrypt_success_createsPendingAndMarksSuccess() {
        encrypt("some content", "file.txt");

        verify(auditService).createPending(eq("ENCRYPT"), any(), any());
        verify(auditService).markSuccess(eq(pendingOp), any(), anyLong());
        verify(auditService, never()).markFailed(any(), any(), any(), anyLong());
    }

    @Test
    void decrypt_success_createsPendingAndMarksSuccess() {
        EncryptResponse encryptResponse = encrypt("some content", "file.txt");
        clearInvocations(auditService);

        decrypt(encryptResponse.ciphertext());

        verify(auditService).createPending(eq("DECRYPT"), any(), eq(KEY_ALIAS));
        verify(auditService).markSuccess(eq(pendingOp), any(), anyLong());
        verify(auditService, never()).markFailed(any(), any(), any(), anyLong());
    }

    @Test
    void encrypt_cryptoFailure_marksFailedWithPreservedOperation() {
        EncryptRequest badRequest = new EncryptRequest(
                Base64.getEncoder().encodeToString("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString("not a real certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "file.txt");

        assertThrows(CryptoAppException.class, () -> encryptionService.encrypt(badRequest));

        verify(auditService).markFailed(eq(pendingOp), eq("ENCRYPT_FAILED"), any(), anyLong());
        verify(auditService, never()).markSuccess(any(), any(), anyLong());
        assertEquals("enc-op-id", pendingOp.getId());
        assertEquals("ENCRYPT", pendingOp.getType());
    }

    @Test
    void decrypt_cryptoFailure_marksFailedWithPreservedOperation() throws Exception {
        EncryptResponse encryptResponse = encrypt("some content", "file.txt");
        clearInvocations(auditService);

        KeyPair wrongKeyPair = TestKeyHelper.generateRsaKeyPair();
        when(keystoreManager.getPrivateKey(KEY_ALIAS)).thenReturn(wrongKeyPair.getPrivate());

        assertThrows(CryptoAppException.class, () -> decrypt(encryptResponse.ciphertext()));

        verify(auditService).markFailed(eq(pendingOp), eq("DECRYPT_FAILED"), any(), anyLong());
        verify(auditService, never()).markSuccess(any(), any(), anyLong());
        assertEquals("enc-op-id", pendingOp.getId());
        assertEquals("ENCRYPT", pendingOp.getType());
    }
}
