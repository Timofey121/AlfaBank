package com.alfabank.crypto.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.security.Security;

@Configuration
public class BouncyCastleConfig {

    private static final Logger log = LoggerFactory.getLogger(BouncyCastleConfig.class);

    @PostConstruct
    public void registerProvider() {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
            log.info("BouncyCastle provider registered at position 1");
        } else {
            log.debug("BouncyCastle provider already registered, skipping");
        }
    }
}
