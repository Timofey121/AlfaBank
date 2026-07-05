package com.alfabank.crypto.service;

import com.alfabank.crypto.config.KeystoreProperties;
import com.alfabank.crypto.dto.request.GenerateKeystoreRequest;
import com.alfabank.crypto.dto.response.GenerateKeystoreResponse;
import com.alfabank.crypto.exception.CryptoAppException;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.CryptoOperation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyGenerationServiceTest {

    static {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
    }

    @Mock private AuditService auditService;

    private KeyGenerationService keyGenerationService;
    private KeystoreManager keystoreManager;
    private KeyStore keyStore;
    private String keystorePath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        keystorePath = tempDir.resolve("keystore.p12").toString();

        keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, "changeit".toCharArray());

        KeystoreProperties properties = new KeystoreProperties();
        properties.setPath(keystorePath);
        properties.setPassword("changeit");
        properties.setType("PKCS12");

        keystoreManager = new KeystoreManager(keyStore, properties, new ReentrantReadWriteLock());
        keyGenerationService = new KeyGenerationService(keystoreManager, new AuditedOperation(auditService));

        CryptoOperation pendingOp = new CryptoOperation();
        pendingOp.setId("keygen-op-id");
        pendingOp.setType("KEY_GENERATION");
        lenient().when(auditService.createPending(eq("KEY_GENERATION"), any(), any())).thenReturn(pendingOp);
    }

    @Test
    void generate_createsAliasAndPersistsToDisk() throws Exception {
        GenerateKeystoreResponse response = keyGenerationService.generate(
                new GenerateKeystoreRequest("my-alias", "MyService", 365));

        assertEquals("my-alias", response.alias());
        assertNotNull(response.certBase64());
        assertTrue(keyStore.containsAlias("my-alias"));
        assertTrue(Files.exists(Path.of(keystorePath)), "keystore file should be persisted to disk");

        KeyStore reloaded = KeyStore.getInstance("PKCS12");
        try (var is = Files.newInputStream(Path.of(keystorePath))) {
            reloaded.load(is, "changeit".toCharArray());
        }
        assertTrue(reloaded.containsAlias("my-alias"), "persisted file should contain the new alias on reload");

        verify(auditService).markSuccess(any(), any(), anyLong());
        verify(auditService, never()).markFailed(any(), any(), any(), anyLong());
    }

    @Test
    void generate_duplicateAlias_marksFailedAndThrows() {
        keyGenerationService.generate(new GenerateKeystoreRequest("dup-alias", "First", 365));
        clearInvocations(auditService);

        assertThrows(CryptoAppException.class, () ->
                keyGenerationService.generate(new GenerateKeystoreRequest("dup-alias", "Second", 365)));

        verify(auditService).markFailed(any(), eq("KEY_GENERATION_FAILED"), any(), anyLong());
        verify(auditService, never()).markSuccess(any(), any(), anyLong());
    }
}
