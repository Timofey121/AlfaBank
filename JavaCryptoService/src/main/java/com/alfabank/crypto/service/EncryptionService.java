package com.alfabank.crypto.service;

import com.alfabank.crypto.crypto.CmsDecryptionProvider;
import com.alfabank.crypto.crypto.CmsEncryptionProvider;
import com.alfabank.crypto.dto.request.DecryptRequest;
import com.alfabank.crypto.dto.request.EncryptRequest;
import com.alfabank.crypto.dto.response.DecryptResponse;
import com.alfabank.crypto.dto.response.EncryptResponse;
import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.EncryptionDetail;
import com.alfabank.crypto.model.OperationType;
import com.alfabank.crypto.repository.EncryptionDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private final CmsEncryptionProvider encryptionProvider;
    private final CmsDecryptionProvider decryptionProvider;
    private final KeystoreManager keystoreManager;
    private final AuditedOperation auditedOperation;
    private final EncryptionDetailRepository detailRepository;

    public EncryptionService(CmsEncryptionProvider encryptionProvider,
                             CmsDecryptionProvider decryptionProvider,
                             KeystoreManager keystoreManager,
                             AuditedOperation auditedOperation,
                             EncryptionDetailRepository detailRepository) {
        this.encryptionProvider = encryptionProvider;
        this.decryptionProvider = decryptionProvider;
        this.keystoreManager = keystoreManager;
        this.auditedOperation = auditedOperation;
        this.detailRepository = detailRepository;
    }

    @Transactional
    public EncryptResponse encrypt(EncryptRequest request) {
        log.info("Encrypt requested");
        return auditedOperation.run(OperationType.ENCRYPT, null, null, "ENCRYPT_FAILED",
                (msg, cause) -> new CryptoOperationException("Encrypt failed: " + msg, cause),
                op -> {
                    byte[] plaintext = Base64.getDecoder().decode(request.plaintext());
                    log.debug("[{}] plaintextSize={} bytes", op.getId(), plaintext.length);
                    X509Certificate recipientCert = parseCert(Base64.getDecoder().decode(request.recipientCertificate()));
                    log.debug("[{}] recipient: {}", op.getId(), recipientCert.getSubjectX500Principal().getName());
                    byte[] packed = packWithFilename(plaintext, request.filename());
                    byte[] ciphertext = encryptionProvider.encrypt(packed, recipientCert);
                    saveEncryptDetail(op.getId(), recipientCert.getSubjectX500Principal().getName(),
                            plaintext.length, ciphertext.length, request.filename());
                    log.info("[{}] encrypted: {}→{} bytes", op.getId(), plaintext.length, ciphertext.length);
                    EncryptResponse response = new EncryptResponse(op.getId(),
                            Base64.getEncoder().encodeToString(ciphertext), Instant.now());
                    return AuditedOperation.Outcome.of(response, ciphertext);
                });
    }

    @Transactional
    public DecryptResponse decrypt(DecryptRequest request) {
        log.info("Decrypt requested: alias={}", request.keyAlias());
        return auditedOperation.run(OperationType.DECRYPT, null, request.keyAlias(), "DECRYPT_FAILED",
                (msg, cause) -> new CryptoOperationException("Decrypt failed: " + msg, cause),
                op -> {
                    byte[] ciphertext = Base64.getDecoder().decode(request.ciphertext());
                    log.debug("[{}] ciphertextSize={} bytes", op.getId(), ciphertext.length);
                    PrivateKey privateKey = keystoreManager.getPrivateKey(request.keyAlias());
                    byte[] decrypted = decryptionProvider.decrypt(ciphertext, privateKey);
                    UnpackedContent unpacked = unpackFilename(decrypted);
                    log.info("[{}] decrypted: {}→{} bytes, filename={}", op.getId(), ciphertext.length,
                            unpacked.content().length, unpacked.filename());
                    DecryptResponse response = new DecryptResponse(op.getId(),
                            Base64.getEncoder().encodeToString(unpacked.content()), unpacked.filename(), Instant.now());
                    return AuditedOperation.Outcome.of(response, unpacked.content());
                });
    }

    private X509Certificate parseCert(byte[] derBytes) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(derBytes));
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            throw new CryptoOperationException("Recipient certificate is not valid: " + e.getMessage(), e);
        }
        return cert;
    }

    private void saveEncryptDetail(String opId, String recipientDn, int inputSize, int outputSize, String filename) {
        EncryptionDetail d = new EncryptionDetail();
        d.setOperationId(opId);
        d.setAlgorithm("AES256_GCM");
        d.setRecipientDn(recipientDn);
        d.setInputSize(inputSize);
        d.setOutputSize(outputSize);
        d.setOriginalFilename(filename);
        detailRepository.save(d);
    }

    private static final byte FORMAT_MARKER_PACKED_FILENAME = (byte) 0x01;

    private byte[] packWithFilename(byte[] content, String filename) {
        byte[] nameBytes = (filename != null && !filename.isBlank())
                ? filename.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + nameBytes.length + content.length);
        buf.put(FORMAT_MARKER_PACKED_FILENAME);
        buf.putInt(nameBytes.length);
        buf.put(nameBytes);
        buf.put(content);
        return buf.array();
    }

    private UnpackedContent unpackFilename(byte[] packed) {
        if (packed.length == 0 || packed[0] != FORMAT_MARKER_PACKED_FILENAME) {
            return new UnpackedContent(null, packed);
        }
        if (packed.length < 1 + 4) {
            throw new CryptoOperationException("Decrypted content is malformed (missing filename header)");
        }
        ByteBuffer buf = ByteBuffer.wrap(packed);
        buf.get();
        int nameLen = buf.getInt();
        if (nameLen < 0 || nameLen > packed.length - 1 - 4) {
            throw new CryptoOperationException("Decrypted content is malformed (invalid filename header)");
        }
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String filename = nameLen > 0 ? new String(nameBytes, StandardCharsets.UTF_8) : null;
        byte[] content = new byte[buf.remaining()];
        buf.get(content);
        return new UnpackedContent(filename, content);
    }

    private record UnpackedContent(String filename, byte[] content) {}
}
