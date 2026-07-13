-- Step 17 · Scenario F, step 4 of 4 · run against the `identity` database.
-- ACC-008: only an Administrator can deactivate an account. ACC-007: a
-- Deactivated account can't log in or place new orders, but deactivation
-- never alters or hides that account's past order history (ORD-014) —
-- Chen's order from step 15/16 stays exactly as it was. Nothing in this
-- statement touches the `orders` database at all; that's the point — see
-- the check queries for proof the order is untouched.

UPDATE accounts
SET status = 'DEACTIVATED',
    updated_at = now()
WHERE id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';  -- Chen
