-- When the user proved they own their email (clicked the verification link, or reset their
-- password from an emailed link). NULL = not verified yet; existing users start unverified.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- One-time links sent by email: "verify your email" and "reset your password".
-- Only a SHA-256 hash of each token is stored, so someone who reads the database can't use a link.
CREATE TABLE email_tokens (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose);
