CREATE TABLE IF NOT EXISTS crypto_operations (
    id           TEXT    NOT NULL PRIMARY KEY,
    type         TEXT    NOT NULL,
    status       TEXT    NOT NULL,
    app_source   TEXT    NOT NULL DEFAULT 'GO',
    input_hash   TEXT,
    output_hash  TEXT,
    key_alias    TEXT,
    error_code   TEXT,
    error_msg    TEXT,
    duration_ms  INTEGER,
    created_at   TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ops_type    ON crypto_operations(type);
CREATE INDEX IF NOT EXISTS idx_ops_status  ON crypto_operations(status);
CREATE INDEX IF NOT EXISTS idx_ops_created ON crypto_operations(created_at);

CREATE TABLE IF NOT EXISTS signature_details (
    operation_id    TEXT NOT NULL PRIMARY KEY REFERENCES crypto_operations(id),
    sign_mode       TEXT,
    signer_subject  TEXT,
    signer_serial   TEXT,
    cert_not_before TEXT,
    cert_not_after  TEXT,
    is_valid        INTEGER
);

CREATE TABLE IF NOT EXISTS encryption_details (
    operation_id       TEXT NOT NULL PRIMARY KEY REFERENCES crypto_operations(id),
    algorithm          TEXT,
    recipient_dn       TEXT,
    input_size         INTEGER,
    output_size        INTEGER,
    original_filename  TEXT
);

CREATE TABLE IF NOT EXISTS fetch_details (
    operation_id  TEXT NOT NULL PRIMARY KEY REFERENCES crypto_operations(id),
    source_url    TEXT NOT NULL,
    http_status   INTEGER,
    content_type  TEXT,
    size_bytes    INTEGER
);
