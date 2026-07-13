-- Run against the `identity` database.
-- Verbatim from Archive/Development/Database §1.1 (V1__create_identity_schema.sql / V2__seed_roles.sql).

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

-- V2__seed_roles.sql
INSERT INTO roles (id, code) VALUES (1, 'ADMIN'), (2, 'CUSTOMER');
