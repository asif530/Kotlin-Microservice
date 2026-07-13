-- Step 09 · Scenario D, step 2 of 3 · run against the `orders` database.
-- Only runs after step 08 (mongo) has successfully reserved stock for both
-- line items — mirrors the real order-service flow: ReserveStock succeeds
-- first, THEN the order is committed (ORD-007/ORD-008).
--
-- ORD-005: product name and unit price are captured at order time and never
-- change afterward, regardless of later catalog edits.
-- ORD-006: total_amount = sum(unit_price_snapshot * quantity) =
--   (89.99 * 2) + (129.00 * 1) = 308.98
-- ORD-009: idempotency_key guarantees this checkout can be retried safely
-- without creating a second order.

INSERT INTO orders (id, customer_id, status_id, total_amount, idempotency_key)
VALUES (
  '20000000-0000-4000-8000-000000000001',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',  -- Alice
  1,                                        -- PLACED
  308.98,
  'idem-alice-checkout-0001'
);

INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
VALUES
  ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000001', 'Trail Runner 2.0', 89.99, 2),
  ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000003', 'Wireless Earbuds Pro', 129.00, 1);
