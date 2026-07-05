package com.alfabank.crypto.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

class DigestProviderTest {

    private static DigestProvider digestProvider;

    @BeforeAll
    static void setUp() {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
        digestProvider = new DigestProvider();
    }

    @Test
    void sha256_knownInput_correctHash() {
        String hash = digestProvider.sha256Hex(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void sha256_helloWorld_correctHash() {
        String hash = digestProvider.sha256Hex("Hello, World!".getBytes());
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
    }

    @Test
    void sha256_sameInput_deterministicResult() {
        byte[] data = "deterministic".getBytes();
        String hash1 = digestProvider.sha256Hex(data);
        String hash2 = digestProvider.sha256Hex(data);
        assertEquals(hash1, hash2);
    }

    @Test
    void sha256_differentInputs_differentHashes() {
        String hash1 = digestProvider.sha256Hex("input1".getBytes());
        String hash2 = digestProvider.sha256Hex("input2".getBytes());
        assertNotEquals(hash1, hash2);
    }
}
