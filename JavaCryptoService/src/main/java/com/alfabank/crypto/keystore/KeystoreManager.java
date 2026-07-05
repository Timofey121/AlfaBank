package com.alfabank.crypto.keystore;

import com.alfabank.crypto.config.KeystoreProperties;
import com.alfabank.crypto.exception.CryptoOperationException;
import com.alfabank.crypto.exception.KeyAliasNotFoundException;
import com.alfabank.crypto.exception.KeystoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class KeystoreManager {

    private static final Logger log = LoggerFactory.getLogger(KeystoreManager.class);

    private final KeyStore keyStore;
    private final String keystorePath;
    private final char[] keystorePassword;
    private final ReentrantReadWriteLock keystoreLock;

    public KeystoreManager(KeyStore keyStore, KeystoreProperties properties, ReentrantReadWriteLock keystoreLock) {
        this.keyStore = keyStore;
        this.keystorePath = properties.getPath();
        this.keystorePassword = properties.getPassword().toCharArray();
        this.keystoreLock = keystoreLock;
    }

    public PrivateKey getPrivateKey(String alias) {
        keystoreLock.readLock().lock();
        try {
            if (!keyStore.containsAlias(alias)) throw new KeyAliasNotFoundException(alias);
            PrivateKey key = (PrivateKey) keyStore.getKey(alias, keystorePassword);
            if (key == null) throw new KeyAliasNotFoundException(alias);
            log.debug("Loaded private key for alias '{}'", alias);
            return key;
        } catch (KeyAliasNotFoundException e) {
            log.warn("Private key not found: alias='{}'", alias);
            throw e;
        } catch (Exception e) {
            throw new KeystoreException("Failed to load private key for alias: " + alias, e);
        } finally {
            keystoreLock.readLock().unlock();
        }
    }

    public X509Certificate getCertificate(String alias) {
        keystoreLock.readLock().lock();
        try {
            if (!keyStore.containsAlias(alias)) throw new KeyAliasNotFoundException(alias);
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            if (cert == null) throw new KeyAliasNotFoundException(alias);
            log.debug("Loaded certificate '{}': subject={}", alias, cert.getSubjectX500Principal().getName());
            return cert;
        } catch (KeyAliasNotFoundException e) {
            log.warn("Certificate not found: alias='{}'", alias);
            throw e;
        } catch (Exception e) {
            throw new KeystoreException("Failed to load certificate for alias: " + alias, e);
        } finally {
            keystoreLock.readLock().unlock();
        }
    }

    public PublicKey getPublicKey(String alias) {
        return getCertificate(alias).getPublicKey();
    }

    public void storeKeyEntry(String alias, PrivateKey privateKey, Certificate[] chain) {
        keystoreLock.writeLock().lock();
        try {
            if (keyStore.containsAlias(alias)) {
                throw new CryptoOperationException("Alias already exists in keystore: " + alias);
            }
            keyStore.setKeyEntry(alias, privateKey, keystorePassword, chain);
            persistAtomically();
            log.info("Stored new key entry for alias '{}', persisted to {}", alias, keystorePath);
        } catch (CryptoOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeystoreException("Failed to store key entry for alias: " + alias, e);
        } finally {
            keystoreLock.writeLock().unlock();
        }
    }

    private void persistAtomically() throws Exception {
        Path targetPath = Paths.get(keystorePath);
        Path dir = targetPath.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);

        Path tempFile = Files.createTempFile(dir, "keystore", ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tempFile)) {
                keyStore.store(os, keystorePassword);
            }
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
