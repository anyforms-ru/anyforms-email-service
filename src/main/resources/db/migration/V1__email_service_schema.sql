CREATE TABLE email_sender (
    type         VARCHAR(32) PRIMARY KEY,
    from_address VARCHAR(320),
    from_name    VARCHAR(255),
    reply_to     VARCHAR(320),
    updated_at   TIMESTAMP(6) WITH TIME ZONE
);

INSERT INTO email_sender (type, updated_at) VALUES ('SYSTEM', now()), ('MARKETING', now());

CREATE TABLE email_template (
    code        VARCHAR(100) PRIMARY KEY,
    description TEXT,
    subject     TEXT                        NOT NULL,
    html        TEXT                        NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE email_message (
    id                  UUID PRIMARY KEY,
    client              VARCHAR(100)                NOT NULL,
    idempotency_key     VARCHAR(255)                NOT NULL,
    request_hash        VARCHAR(64)                 NOT NULL,
    type                VARCHAR(32)                 NOT NULL,
    template_code       VARCHAR(100),
    to_address          VARCHAR(320)                NOT NULL,
    from_name           VARCHAR(255),
    reply_to            VARCHAR(320),
    subject             TEXT                        NOT NULL,
    html                TEXT                        NOT NULL,
    status              VARCHAR(32)                 NOT NULL,
    attempts            INTEGER                     NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    locked_until        TIMESTAMP(6) WITH TIME ZONE,
    last_error          TEXT,
    provider_message_id VARCHAR(255),
    sent_at             TIMESTAMP(6) WITH TIME ZONE,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_email_message_idempotency UNIQUE (client, idempotency_key)
);

CREATE INDEX idx_email_message_due ON email_message (next_attempt_at) WHERE status = 'QUEUED';
CREATE INDEX idx_email_message_stale ON email_message (locked_until) WHERE status = 'SENDING';
CREATE INDEX idx_email_message_created ON email_message (created_at DESC);
CREATE INDEX idx_email_message_sent ON email_message (sent_at) WHERE status = 'SENT';
