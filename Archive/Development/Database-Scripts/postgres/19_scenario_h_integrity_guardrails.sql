-- Step 19 · Scenario H (Postgres half) · run against `identity` and `orders`.
--
-- *** IMPORTANT: every statement in this file is EXPECTED TO FAIL. ***
-- That failure IS the proof the schema enforces the rule — it is not a bug.
-- Run this file WITHOUT `-v ON_ERROR_STOP=1` (unlike every other script in
-- this sequence) so psql prints each error and keeps going to the next one,
-- e.g.:
--   docker exec -i kotlincrud-postgres psql -U kotlincrud -d identity < 19_scenario_h_integrity_guardrails.sql
-- (this file switches databases itself with \c, see below)

-- G1 — ACC-002: email uniqueness is case-insensitive. Alice already
-- registered as alice.nguyen@example.test (step 07). This must fail with a
-- unique_violation on accounts_email_key.
INSERT INTO accounts (id, email, password_hash, full_name, role_id, status)
VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'ALICE.NGUYEN@EXAMPLE.TEST',
        'DUMMY-PLACEHOLDER-NOT-A-REAL-HASH', 'Alice Impersonator', 2, 'ACTIVE');

\c orders

-- G2 — ORD-004: a product can appear at most once per order. Order
-- 20000000-...-0001 already has a line item for prod-0001 (step 09). This
-- must fail with a unique_violation on uq_order_product.
INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
VALUES ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', '20000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001', 'Trail Runner 2.0', 89.99, 1);

-- G3 — ORD-003: quantity must be at least 1. This must fail with a
-- check_violation on order_items_quantity_check.
INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
VALUES ('ffffffff-ffff-4fff-8fff-ffffffffffff', '20000000-0000-4000-8000-000000000002',
        '10000000-0000-4000-8000-000000000002', 'Insulated Steel Bottle', 24.50, 0);

-- G4 — ORD-009: idempotency_key is unique. 'idem-alice-checkout-0001' was
-- already used by order 20000000-...-0001 (step 09). This must fail with a
-- unique_violation on orders_idempotency_key_key.
INSERT INTO orders (id, customer_id, status_id, total_amount, idempotency_key)
VALUES ('99999999-9999-4999-8999-999999999999', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        1, 24.50, 'idem-alice-checkout-0001');
