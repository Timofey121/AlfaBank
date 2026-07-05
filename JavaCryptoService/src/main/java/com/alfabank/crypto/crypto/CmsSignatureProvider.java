package com.alfabank.crypto.crypto;

import com.alfabank.crypto.exception.CryptoOperationException;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

@Component
public class CmsSignatureProvider implements CryptoProvider {

    public byte[] sign(byte[] data, PrivateKey privateKey, X509Certificate cert, boolean attached) {
        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider("BC")
                    .build(privateKey);

            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
                    ).build(signer, cert)
            );

            gen.addCertificates(new JcaCertStore(List.of(cert)));

            CMSTypedData msg = new CMSProcessableByteArray(data);
            CMSSignedData signedData = gen.generate(msg, attached);
            return signedData.getEncoded();
        } catch (Exception e) {
            throw new CryptoOperationException("Signing failed: " + e.getMessage(), e);
        }
    }
}
