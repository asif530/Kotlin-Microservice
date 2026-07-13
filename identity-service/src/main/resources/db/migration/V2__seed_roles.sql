-- Reference/lookup data, not fabricated user data — see ARCHITECTURE.md §9
-- ("the standard, defensible use of Flyway seed migrations") and
-- Archive/Development/Database §1.2.
INSERT INTO roles (id, code) VALUES (1, 'ADMIN'), (2, 'CUSTOMER');
