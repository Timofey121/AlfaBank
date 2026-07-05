package com.alfabank.crypto.crypto;

import com.alfabank.crypto.exception.CryptoOperationException;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

@Component
public class CmsEncryptionProvider implements CryptoProvider {

    public byte[] encrypt(byte[] plaintext, X509Certificate recipientCert) {
        try {
            CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();
            gen.addRecipientInfoGenerator(
                    new JceKeyTransRecipientInfoGenerator(recipientCert).setProvider("BC")
            );
            CMSEnvelopedData enveloped = gen.generate(
                    new CMSProcessableByteArray(plaintext),
                    new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider("BC").build()
            );
            return enveloped.getEncoded();
        } catch (Exception e) {
            throw new CryptoOperationException("Encryption failed: " + e.getMessage(), e);
        }
    }
}
