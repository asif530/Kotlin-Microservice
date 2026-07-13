-- Step 07 · Scenario C · run against the `identity` database.
-- Customers self-register (ACC-001..004): email, password, full name
-- required; new accounts are Customer role, immediately ACTIVE, no
-- email-verification step (ACC-004).
--
-- Three customers are seeded because later scenarios need them:
--   Alice — places an order, then cancels it (Scenario D, E)
--   Bilal — attempts a checkout that must be rejected for insufficient
--           stock (Scenario G)
--   Chen  — places an order, then is deactivated by the Administrator, to
--           prove deactivation never touches past order history (Scenario F)

INSERT INTO accounts (id, email, password_hash, full_name, role_id, status) VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice.nguyen@example.test', 'DUMMY-PLACEHOLDER-NOT-A-REAL-HASH', 'Alice Nguyen', 2, 'ACTIVE'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bilal.rahman@example.test', 'DUMMY-PLACEHOLDER-NOT-A-REAL-HASH', 'Bilal Rahman', 2, 'ACTIVE'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'chen.park@example.test',    'DUMMY-PLACEHOLDER-NOT-A-REAL-HASH', 'Chen Park',    2, 'ACTIVE');
