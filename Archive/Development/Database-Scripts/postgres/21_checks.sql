-- Step 21 · verification queries · Postgres half.
-- Run with: docker exec -i kotlincrud-postgres psql -U kotlincrud -d identity -v ON_ERROR_STOP=1 < 21_checks.sql
-- (this file switches to `orders` and back with \c; read-only throughout)

\echo '--- Scenario A: the one Administrator (ACC-010) ---'
SELECT a.email, a.full_name, r.code AS role, a.status
FROM accounts a JOIN roles r ON r.id = a.role_id
WHERE r.code = 'ADMIN';

\echo '--- Scenario C: registered customers (ACC-001..004) ---'
SELECT email, full_name, status FROM accounts WHERE role_id = 2 ORDER BY email;

\echo '--- ACC-002 proof: case-insensitive lookup still finds Alice ---'
SELECT id, email FROM accounts WHERE email = 'ALICE.NGUYEN@EXAMPLE.TEST';

\echo '--- Scenario F identity-side: Chen is DEACTIVATED (ACC-006/008) ---'
SELECT email, full_name, status FROM accounts WHERE id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';

\echo '--- Scenario H guardrail proof (identity): still exactly one Alice, no impersonator row ---'
SELECT count(*) AS should_be_1 FROM accounts WHERE email = 'alice.nguyen@example.test';

\c orders

\echo '--- Scenario D: Alice''s order + captured line-item snapshots (ORD-005) ---'
SELECT o.id, o.customer_id, s.code AS status, o.total_amount, o.idempotency_key
FROM orders o JOIN order_status s ON s.id = o.status_id
WHERE o.id = '20000000-0000-4000-8000-000000000001';

SELECT product_name_snapshot, unit_price_snapshot, quantity, line_total
FROM order_items WHERE order_id = '20000000-0000-4000-8000-000000000001'
ORDER BY product_name_snapshot;

\echo '--- ORD-006 proof: total_amount equals sum(line_total) ---'
SELECT
  (SELECT total_amount FROM orders WHERE id = '20000000-0000-4000-8000-000000000001') AS stored_total,
  (SELECT sum(line_total) FROM order_items WHERE order_id = '20000000-0000-4000-8000-000000000001') AS computed_total;

\echo '--- Scenario E: Alice''s order is now CANCELLED (ORD-010/011) ---'
SELECT o.id, s.code AS status
FROM orders o JOIN order_status s ON s.id = o.status_id
WHERE o.id = '20000000-0000-4000-8000-000000000001';

\echo '--- Scenario F orders-side: Chen''s order is untouched by her deactivation (ORD-014) ---'
SELECT o.id, o.customer_id, s.code AS status, o.total_amount
FROM orders o JOIN order_status s ON s.id = o.status_id
WHERE o.id = '20000000-0000-4000-8000-000000000002';

\echo '--- Scenario H guardrail proof (orders): no duplicate line item, no duplicate idempotency key ---'
SELECT count(*) AS should_be_2 FROM order_items WHERE order_id = '20000000-0000-4000-8000-000000000001';
SELECT count(*) AS should_be_1 FROM orders WHERE idempotency_key = 'idem-alice-checkout-0001';
