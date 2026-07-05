package com.alfabank.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "crypto.keystore")
public class KeystoreProperties {
    private String path;
    private String password;
    private String type = "PKCS12";

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
