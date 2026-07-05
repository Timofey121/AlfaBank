package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.CmsSignatureProvider;
import com.alfabank.crypto.crypto.CmsVerificationProvider;
import com.alfabank.crypto.dto.request.SignRequest;
import com.alfabank.crypto.dto.request.VerifyRequest;
import com.alfabank.crypto.dto.response.SignResponse;
import com.alfabank.crypto.dto.response.VerifyResponse;
import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.OperationType;
import com.alfabank.crypto.model.SignatureDetail;
import com.alfabank.crypto.repository.SignatureDetailRepository;
import org.bouncycastle.cert.X509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;

@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private final CmsSignatureProvider signatureProvider;
    private final CmsVerificationProvider verificationProvider;
    private final KeystoreManager keystoreManager;
    private final AuditedOperation auditedOperation;
    private final SignatureDetailRepository detailRepository;

    public SignatureService(CmsSignatureProvider signatureProvider,
                            CmsVerificationProvider verificationProvider,
                            KeystoreManager keystoreManager,
                            AuditedOperation auditedOperation,
                            SignatureDetailRepository detailRepository) {
        this.signatureProvider = signatureProvider;
        this.verificationProvider = verificationProvider;
        this.keystoreManager = keystoreManager;
        this.auditedOperation = auditedOperation;
        this.detailRepository = detailRepository;
    }

    @Transactional
    public SignResponse sign(SignRequest request) {
        return auditedOperation.run(OperationType.SIGN, null, request.keyAlias(), "SIGN_FAILED",
                (msg, cause) -> new CryptoOperationException("Sign failed: " + msg, cause),
                op -> {
                    byte[] data = Base64.getDecoder().decode(request.data());
                    log.info("PKCS#7 sign requested: alias={}, mode={}, dataSize={}",
                            request.keyAlias(), request.mode(), data.length);
                    PrivateKey privateKey = keystoreManager.getPrivateKey(request.keyAlias());
                    X509Certificate cert = keystoreManager.getCertificate(request.keyAlias());
                    boolean attached = "ATTACHED".equals(request.mode());
                    byte[] signatureBytes = signatureProvider.sign(data, privateKey, cert, attached);
                    saveSignatureDetail(op.getId(), request.mode(), cert, null);
                    log.info("[{}] signed successfully, sigSize={} bytes", op.getId(), signatureBytes.length);
                    SignResponse response = new SignResponse(
                            op.getId(),
                            Base64.getEncoder().encodeToString(signatureBytes),
                            request.mode(),
                            Base64.getEncoder().encodeToString(cert.getEncoded()),
                            Instant.now()
                    );
                    return AuditedOperation.Outcome.of(response, signatureBytes);
                });
    }

    @Transactional
    public VerifyResponse verify(VerifyRequest request) {
        return auditedOperation.run(OperationType.VERIFY, null, null, "VERIFY_FAILED",
                (msg, cause) -> new CryptoOperationException("Verify failed: " + msg, cause),
                op -> {
                    byte[] sigBytes = Base64.getDecoder().decode(request.signature());
                    byte[] originalData = request.data() != null ? Base64.getDecoder().decode(request.data()) : null;
                    boolean attached = "ATTACHED".equals(request.mode());
                    log.info("PKCS#7 verify requested: mode={}, sigSize={}", request.mode(), sigBytes.length);
                    CmsVerificationProvider.VerifyResult result = verificationProvider.verify(sigBytes, originalData, attached);
                    X509CertificateHolder cert = result.signerCert();
                    saveVerifyDetail(op.getId(), request.mode(), cert, result.valid());
                    log.info("[{}] verification result: valid={}, signer={}", op.getId(), result.valid(),
                            cert != null ? cert.getSubject() : "unknown");
                    VerifyResponse response = new VerifyResponse(
                            op.getId(),
                            result.valid(),
                            cert != null ? cert.getSubject().toString() : null,
                            cert != null ? cert.getSerialNumber().toString(16) : null,
                            cert != null ? cert.getNotBefore().toInstant() : null,
                            cert != null ? cert.getNotAfter().toInstant() : null,
                            Instant.now()
                    );
                    return AuditedOperation.Outcome.of(response);
                });
    }

    private void saveSignatureDetail(String opId, String mode, X509Certificate cert, Boolean isValid) {
        SignatureDetail d = new SignatureDetail();
        d.setOperationId(opId);
        d.setSignMode(mode);
        d.setSignerSubject(cert.getSubjectX500Principal().getName());
        d.setSignerSerial(cert.getSerialNumber().toString(16));
        d.setCertNotBefore(cert.getNotBefore().toInstant());
        d.setCertNotAfter(cert.getNotAfter().toInstant());
        d.setIsValid(isValid);
        detailRepository.save(d);
    }

    private void saveVerifyDetail(String opId, String mode, X509CertificateHolder cert, boolean valid) {
        SignatureDetail d = new SignatureDetail();
        d.setOperationId(opId);
        d.setSignMode(mode);
        if (cert != null) {
            d.setSignerSubject(cert.getSubject().toString());
            d.setSignerSerial(cert.getSerialNumber().toString(16));
            d.setCertNotBefore(cert.getNotBefore().toInstant());
            d.setCertNotAfter(cert.getNotAfter().toInstant());
        }
        d.setIsValid(valid);
        detailRepository.save(d);
    }
}
