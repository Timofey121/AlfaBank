package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.CmsSignatureProvider;
import com.alfabank.crypto.crypto.CmsVerificationProvider;
import com.alfabank.crypto.dto.request.SignRequest;
import com.alfabank.crypto.dto.request.VerifyRequest;
import com.alfabank.crypto.dto.response.SignResponse;
import com.alfabank.crypto.dto.response.VerifyResponse;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.CryptoOperation;
import com.alfabank.crypto.repository.SignatureDetailRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    @Mock private CmsSignatureProvider signatureProvider;
    @Mock private CmsVerificationProvider verificationProvider;
    @Mock private KeystoreManager keystoreManager;
    @Mock private AuditService auditService;
    @Mock private SignatureDetailRepository detailRepository;
    private SignatureService signatureService;

    private CryptoOperation pendingOp;
    private KeyPair keyPair;
    private X509Certificate cert;

    @BeforeEach
    void setUp() throws Exception {
        signatureService = new SignatureService(
                signatureProvider, verificationProvider, keystoreManager,
                new AuditedOperation(auditService), detailRepository);
        pendingOp = new CryptoOperation();
        pendingOp.setId("sig-op-id");
        pendingOp.setType("SIGN");
        when(auditService.createPending(any(), any(), any())).thenReturn(pendingOp);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        cert = com.alfabank.crypto.crypto.TestKeyHelper.generateSelfSignedCert(keyPair);
    }

    @Test
    void sign_returnsSignatureBase64() throws Exception {
        byte[] fakeSignature = "fakesig".getBytes();
        when(keystoreManager.getPrivateKey("test-alias")).thenReturn(keyPair.getPrivate());
        when(keystoreManager.getCertificate("test-alias")).thenReturn(cert);
        when(signatureProvider.sign(any(), any(), any(), anyBoolean())).thenReturn(fakeSignature);

        String data = Base64.getEncoder().encodeToString("document".getBytes());
        SignResponse response = signatureService.sign(new SignRequest(data, "test-alias", "ATTACHED"));

        assertNotNull(response.signature());
        assertEquals("ATTACHED", response.mode());
        assertEquals("sig-op-id", response.operationId());
        verify(auditService).markSuccess(any(), any(), anyLong());
    }

    @Test
    void sign_marksFailedOnProviderError() {
        when(keystoreManager.getPrivateKey(any())).thenReturn(keyPair.getPrivate());
        when(keystoreManager.getCertificate(any())).thenReturn(cert);
        when(signatureProvider.sign(any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("signing error"));

        String data = Base64.getEncoder().encodeToString("doc".getBytes());
        assertThrows(RuntimeException.class,
                () -> signatureService.sign(new SignRequest(data, "k", "ATTACHED")));
        verify(auditService).markFailed(any(), eq("SIGN_FAILED"), any(), anyLong());
    }

    @Test
    void verify_returnsValidTrue_whenProviderSaysValid() throws Exception {
        X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());
        CmsVerificationProvider.VerifyResult result = new CmsVerificationProvider.VerifyResult(true, holder);
        when(verificationProvider.verify(any(), any(), anyBoolean())).thenReturn(result);

        String sig = Base64.getEncoder().encodeToString("anysig".getBytes());
        VerifyResponse response = signatureService.verify(new VerifyRequest(sig, null, "ATTACHED"));

        assertTrue(response.valid());
        assertNotNull(response.signerSubject());
    }

    @Test
    void verify_returnsValidFalse_whenSignatureTampered() throws Exception {
        X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());
        when(verificationProvider.verify(any(), any(), anyBoolean()))
                .thenReturn(new CmsVerificationProvider.VerifyResult(false, holder));

        String sig = Base64.getEncoder().encodeToString("badsig".getBytes());
        VerifyResponse response = signatureService.verify(new VerifyRequest(sig, null, "ATTACHED"));

        assertFalse(response.valid());
    }
}
