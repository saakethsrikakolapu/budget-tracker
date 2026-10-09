-- A remembered file format: which column is which, for files whose columns look like this.
-- Saved when the user confirms (or corrects) the preview, so the next upload is automatic.
CREATE TABLE import_formats (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Identifies the file's shape: its header names, or "no header, N columns".
    signature  VARCHAR(2000) NOT NULL,
    -- The ColumnMapping as JSON.
    mapping    TEXT          NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_import_formats_user_signature UNIQUE (user_id, signature)
);
