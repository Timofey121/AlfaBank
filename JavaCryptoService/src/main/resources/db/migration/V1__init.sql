CREATE TABLE crypto_operations (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    type         VARCHAR(32)   NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    app_source   VARCHAR(8)    NOT NULL DEFAULT 'JAVA',
    input_hash   VARCHAR(64),
    output_hash  VARCHAR(64),
    key_alias    VARCHAR(255),
    error_code   VARCHAR(64),
    error_msg    VARCHAR(2048),
    duration_ms  BIGINT,
    created_at   TIMESTAMP     NOT NULL
);

CREATE INDEX idx_ops_type    ON crypto_operations(type);
CREATE INDEX idx_ops_status  ON crypto_operations(status);
CREATE INDEX idx_ops_created ON crypto_operations(created_at);

CREATE TABLE signature_details (
    operation_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    sign_mode       VARCHAR(16),
    signer_subject  VARCHAR(512),
    signer_serial   VARCHAR(128),
    cert_not_before TIMESTAMP,
    cert_not_after  TIMESTAMP,
    is_valid        BOOLEAN,
    FOREIGN KEY (operation_id) REFERENCES crypto_operations(id)
);

CREATE TABLE encryption_details (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    algorithm    VARCHAR(32),
    recipient_dn VARCHAR(512),
    input_size   INTEGER,
    output_size  INTEGER,
    original_filename VARCHAR(255),
    FOREIGN KEY (operation_id) REFERENCES crypto_operations(id)
);

CREATE TABLE fetch_details (
    operation_id VARCHAR(36)    NOT NULL PRIMARY KEY,
    source_url   VARCHAR(2048)  NOT NULL,
    http_status  INTEGER,
    content_type VARCHAR(128),
    size_bytes   BIGINT,
    FOREIGN KEY (operation_id) REFERENCES crypto_operations(id)
);
