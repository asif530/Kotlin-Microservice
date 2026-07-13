-- Step 12 · Scenario E, step 2 of 3 · run against the `orders` database.
-- ORD-011: a Customer can cancel their own order at any time while it is
-- Placed. Runs after step 11 has restored stock in catalog-service.

UPDATE orders
SET status_id = 5,   -- CANCELLED
    updated_at = now()
WHERE id = '20000000-0000-4000-8000-000000000001'
  AND customer_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'  -- Alice, ORD-011: only her own order
  AND status_id = 1;  -- was PLACED
