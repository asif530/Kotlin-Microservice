-- Run against the `orders` database.
-- Verbatim from Archive/Development/Database §3.1 (V1__create_order_schema.sql / V2__seed_order_statuses.sql).

CREATE TABLE order_status (
    id    SMALLINT PRIMARY KEY,
    code  VARCHAR(10) NOT NULL UNIQUE           -- ORD-010
);

CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      UUID NOT NULL,             -- reference to identity-service account; no cross-DB FK
    status_id        SMALLINT NOT NULL REFERENCES order_status(id),
    total_amount     NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),   -- ORD-006
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,                       -- ORD-009
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id_created_at ON orders (customer_id, created_at DESC);  -- ORD-013

CREATE TABLE order_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id               UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id             UUID NOT NULL,       -- reference to catalog-service product; no cross-DB FK
    product_name_snapshot  VARCHAR(200) NOT NULL,                        -- ORD-005
    unit_price_snapshot    NUMERIC(12,2) NOT NULL CHECK (unit_price_snapshot > 0), -- ORD-005
    quantity               INTEGER NOT NULL CHECK (quantity >= 1),       -- ORD-003
    line_total             NUMERIC(12,2) GENERATED ALWAYS AS (unit_price_snapshot * quantity) STORED,
    CONSTRAINT uq_order_product UNIQUE (order_id, product_id)            -- ORD-004
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- V2__seed_order_statuses.sql
INSERT INTO order_status (id, code) VALUES
  (1, 'PLACED'), (2, 'CONFIRMED'), (3, 'SHIPPED'), (4, 'DELIVERED'), (5, 'CANCELLED');
