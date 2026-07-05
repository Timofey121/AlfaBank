package com.alfabank.crypto.config;

import com.alfabank.crypto.exception.KeystoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Configuration
public class KeystoreConfig {

    private static final Logger log = LoggerFactory.getLogger(KeystoreConfig.class);

    private final KeystoreProperties properties;

    public KeystoreConfig(KeystoreProperties properties) {
        this.properties = properties;
    }

    @Bean
    public KeyStore keyStore() {
        char[] password = properties.getPassword().toCharArray();
        try {
            KeyStore ks = KeyStore.getInstance(properties.getType());
            java.io.File f = new java.io.File(properties.getPath());
            if (f.exists()) {
                try (InputStream is = new FileInputStream(f)) {
                    ks.load(is, password);
                    log.info("Keystore loaded from: {}", properties.getPath());
                }
            } else {
                ks.load(null, password);
                log.warn("Keystore file '{}' not found, starting with empty store", properties.getPath());
            }
            return ks;
        } catch (Exception e) {
            throw new KeystoreException("Failed to initialise keystore: " + properties.getPath(), e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Bean
    public ReentrantReadWriteLock keystoreLock() {
        return new ReentrantReadWriteLock();
    }
}
