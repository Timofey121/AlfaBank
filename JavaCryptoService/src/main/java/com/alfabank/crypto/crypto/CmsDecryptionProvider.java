package com.alfabank.crypto.crypto;

import com.alfabank.crypto.exception.CryptoOperationException;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Collection;

@Component
public class CmsDecryptionProvider implements CryptoProvider {

    public byte[] decrypt(byte[] ciphertextBytes, PrivateKey privateKey) {
        try {
            CMSEnvelopedData cms = new CMSEnvelopedData(ciphertextBytes);
            RecipientInformationStore recipients = cms.getRecipientInfos();
            Collection<RecipientInformation> recipientList = recipients.getRecipients();

            if (recipientList.isEmpty()) {
                throw new CryptoOperationException("No recipients found in EnvelopedData");
            }

            RecipientInformation recipient = recipientList.iterator().next();
            return recipient.getContent(
                    new JceKeyTransEnvelopedRecipient(privateKey).setProvider("BC")
            );
        } catch (CryptoOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoOperationException("Decryption failed: " + e.getMessage(), e);
        }
    }
}
