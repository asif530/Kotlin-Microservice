-- Reference/lookup data, not fabricated order data — see ARCHITECTURE.md §9
-- ("the standard, defensible use of Flyway seed migrations") and
-- Archive/Development/Database §3.2. ORD-010: only PLACED and CANCELLED are
-- ever actually assigned by any process in this system's current scope, but
-- all five values are seeded since ORD-010 fixes the complete status list.
INSERT INTO order_status (id, code) VALUES
  (1, 'PLACED'), (2, 'CONFIRMED'), (3, 'SHIPPED'), (4, 'DELIVERED'), (5, 'CANCELLED');
