package com.alfabank.crypto.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "signature_details")
public class SignatureDetail {

    @Id
    @Column(length = 36)
    private String operationId;

    @Column(name = "sign_mode", length = 16)
    private String signMode;

    @Column(name = "signer_subject")
    private String signerSubject;

    @Column(name = "signer_serial")
    private String signerSerial;

    @Column(name = "cert_not_before")
    private Instant certNotBefore;

    @Column(name = "cert_not_after")
    private Instant certNotAfter;

    @Column(name = "is_valid")
    private Boolean isValid;

    public SignatureDetail() {}

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getSignMode() { return signMode; }
    public void setSignMode(String signMode) { this.signMode = signMode; }
    public String getSignerSubject() { return signerSubject; }
    public void setSignerSubject(String signerSubject) { this.signerSubject = signerSubject; }
    public String getSignerSerial() { return signerSerial; }
    public void setSignerSerial(String signerSerial) { this.signerSerial = signerSerial; }
    public Instant getCertNotBefore() { return certNotBefore; }
    public void setCertNotBefore(Instant certNotBefore) { this.certNotBefore = certNotBefore; }
    public Instant getCertNotAfter() { return certNotAfter; }
    public void setCertNotAfter(Instant certNotAfter) { this.certNotAfter = certNotAfter; }
    public Boolean getIsValid() { return isValid; }
    public void setIsValid(Boolean isValid) { this.isValid = isValid; }
}
