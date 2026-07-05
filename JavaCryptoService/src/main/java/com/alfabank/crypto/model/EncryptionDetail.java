package com.alfabank.crypto.model;

import jakarta.persistence.*;

@Entity
@Table(name = "encryption_details")
public class EncryptionDetail {

    @Id
    @Column(length = 36)
    private String operationId;

    @Column(length = 32)
    private String algorithm;

    @Column(name = "recipient_dn")
    private String recipientDn;

    @Column(name = "input_size")
    private Integer inputSize;

    @Column(name = "output_size")
    private Integer outputSize;

    @Column(name = "original_filename")
    private String originalFilename;

    public EncryptionDetail() {}

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getRecipientDn() { return recipientDn; }
    public void setRecipientDn(String recipientDn) { this.recipientDn = recipientDn; }
    public Integer getInputSize() { return inputSize; }
    public void setInputSize(Integer inputSize) { this.inputSize = inputSize; }
    public Integer getOutputSize() { return outputSize; }
    public void setOutputSize(Integer outputSize) { this.outputSize = outputSize; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
}
