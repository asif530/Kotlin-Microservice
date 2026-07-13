-- Identity schema for identity-service.
-- Verbatim from Archive/Development/Database §1.1 and
-- Archive/Development/Database-Dev/postgres/00_identity_schema.sql, which is
-- already applied by hand to the running kotlincrud-postgres `identity`
-- database. This migration must produce the identical result, not a
-- reinterpretation — see application.yml's spring.flyway.baseline-* settings
-- for how that pre-existing dev database is reconciled with this migration.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE roles (
    id    SMALLINT PRIMARY KEY,
    code  VARCHAR(20) NOT NULL UNIQUE            -- 'ADMIN' | 'CUSTOMER'
);

CREATE TABLE accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          CITEXT NOT NULL UNIQUE,       -- ACC-002
    password_hash  TEXT NOT NULL,                -- ACC-001; hashing algorithm is app-layer, not a DB concern
    full_name      VARCHAR(200) NOT NULL,        -- ACC-001, ACC-011
    role_id        SMALLINT NOT NULL REFERENCES roles(id),
    status         VARCHAR(11) NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'DEACTIVATED')),   -- ACC-006
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_role_id ON accounts (role_id);
