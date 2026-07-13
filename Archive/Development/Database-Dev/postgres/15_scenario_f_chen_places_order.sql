-- Step 15 · Scenario F, step 2 of 4 · run against the `orders` database.
-- Runs after step 14 has reserved stock for Chen's order.
-- ORD-006: total_amount = 94.99 * 1 = 94.99

INSERT INTO orders (id, customer_id, status_id, total_amount, idempotency_key)
VALUES (
  '20000000-0000-4000-8000-000000000002',
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',  -- Chen
  1,                                        -- PLACED
  94.99,
  'idem-chen-checkout-0001'
);

INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
VALUES (
  '30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002',
  '10000000-0000-4000-8000-000000000004', 'Trail Runner 2.0', 94.99, 1
);
