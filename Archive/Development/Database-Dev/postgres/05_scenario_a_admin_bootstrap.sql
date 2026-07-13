-- Step 05 · Scenario A · run against the `identity` database.
-- ACC-010: exactly one Administrator account exists from the moment the
-- system is first stood up, before any customer has registered.
--
-- password_hash below is an obvious placeholder, not a real bcrypt hash —
-- hashing is application-layer (see Archive/Development/Database §1.1) and
-- out of scope for a database-only proof. Never use a literal like this
-- outside a throwaway dev database.

INSERT INTO accounts (id, email, password_hash, full_name, role_id, status)
VALUES (
  '11111111-1111-4111-8111-111111111111',
  'admin@minimart.test',
  'DUMMY-PLACEHOLDER-NOT-A-REAL-HASH',
  'MiniMart Admin',
  1,        -- ADMIN
  'ACTIVE'
);
