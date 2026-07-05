package com.alfabank.crypto.service;

import com.alfabank.crypto.dto.request.GenerateKeystoreRequest;
import com.alfabank.crypto.dto.response.GenerateKeystoreResponse;
import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.keystore.KeystoreManager;
import com.alfabank.crypto.model.OperationType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

@Service
public class KeyGenerationService {

    private static final Logger log = LoggerFactory.getLogger(KeyGenerationService.class);

    private final KeystoreManager keystoreManager;
    private final AuditedOperation auditedOperation;

    public KeyGenerationService(KeystoreManager keystoreManager, AuditedOperation auditedOperation) {
        this.keystoreManager = keystoreManager;
        this.auditedOperation = auditedOperation;
    }

    public GenerateKeystoreResponse generate(GenerateKeystoreRequest request) {
        String alias = request.alias();
        return auditedOperation.run(OperationType.KEY_GENERATION, null, alias, "KEY_GENERATION_FAILED",
                (msg, cause) -> new CryptoOperationException("Key generation failed: " + msg, cause),
                op -> {
                    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC");
                    gen.initialize(2048, new SecureRandom());
                    KeyPair keyPair = gen.generateKeyPair();

                    X500Name subject = new X500Name("CN=" + request.cn() + ",O=CryptoService,C=RU");
                    Date notBefore = new Date();
                    Date notAfter = new Date(notBefore.getTime() + (long) request.validityDays() * 86_400_000L);
                    BigInteger serial = new BigInteger(160, new SecureRandom());

                    X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                            subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
                    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                            .setProvider("BC").build(keyPair.getPrivate());
                    X509Certificate cert = new JcaX509CertificateConverter()
                            .setProvider("BC").getCertificate(builder.build(signer));

                    keystoreManager.storeKeyEntry(alias, keyPair.getPrivate(), new Certificate[]{cert});
                    log.info("Generated RSA-2048 keypair for alias '{}'", alias);

                    byte[] encodedCert = cert.getEncoded();
                    GenerateKeystoreResponse response = new GenerateKeystoreResponse(
                            alias,
                            cert.getSubjectX500Principal().getName(),
                            cert.getSerialNumber().toString(16),
                            cert.getNotBefore().toInstant(),
                            cert.getNotAfter().toInstant(),
                            Base64.getEncoder().encodeToString(encodedCert)
                    );
                    return AuditedOperation.Outcome.of(response, encodedCert);
                });
    }
}
