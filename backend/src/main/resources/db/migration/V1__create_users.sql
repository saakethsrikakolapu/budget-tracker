-- "user" is a reserved word in Postgres, so the table is "users".
CREATE TABLE users (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- One account per email, enforced by the database even if two sign-ups race.
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are stored lowercase so the unique constraint is case-insensitive in practice.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);
