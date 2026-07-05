package com.alfabank.crypto.crypto;

import com.alfabank.crypto.exception.CryptoOperationException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Collection;

@Component
public class CmsVerificationProvider implements CryptoProvider {

    private static final Logger log = LoggerFactory.getLogger(CmsVerificationProvider.class);

    public record VerifyResult(boolean valid, X509CertificateHolder signerCert) {}

    public VerifyResult verify(byte[] signatureBytes, byte[] originalData, boolean attached) {
        try {
            CMSSignedData cms;
            if (attached) {
                cms = new CMSSignedData(signatureBytes);
            } else {
                if (originalData == null || originalData.length == 0) {
                    throw new CryptoOperationException("Original data required for DETACHED verification");
                }
                cms = new CMSSignedData(new CMSProcessableByteArray(originalData), signatureBytes);
            }
            Store<X509CertificateHolder> certStore = cms.getCertificates();
            SignerInformationStore signers = cms.getSignerInfos();
            Collection<SignerInformation> signerInfos = signers.getSigners();
            if (signerInfos.size() != 1) {
                log.warn("Expected exactly one signer in CMS SignedData, found {}", signerInfos.size());
                return new VerifyResult(false, null);
            }
            SignerInformation si = signerInfos.iterator().next();
            Collection<X509CertificateHolder> matches = certStore.getMatches(si.getSID());
            if (matches.isEmpty()) return new VerifyResult(false, null);
            X509CertificateHolder certHolder = matches.iterator().next();
            try {
                new JcaX509CertificateConverter().setProvider("BC")
                        .getCertificate(certHolder)
                        .checkValidity();
            } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                log.warn("Signer certificate is not time-valid: {}", e.getMessage());
                return new VerifyResult(false, certHolder);
            } catch (CertificateException e) {
                log.warn("Signer certificate could not be validated: {}", e.getMessage());
                return new VerifyResult(false, certHolder);
            }
            boolean valid;
            try {
                valid = si.verify(
                        new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(certHolder)
                );
            } catch (CMSException e) {
                log.warn("Signature verification failed (mismatch): {}", e.getMessage());
                return new VerifyResult(false, certHolder);
            }
            return new VerifyResult(valid, certHolder);
        } catch (CryptoOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoOperationException("Verification failed: " + e.getMessage(), e);
        }
    }
}
