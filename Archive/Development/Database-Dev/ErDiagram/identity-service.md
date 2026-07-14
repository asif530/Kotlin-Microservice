# identity-service — ER Diagram

Source: `Archive/Development/Database` §1.1, verbatim schema at `Archive/Development/Database-Dev/postgres/00_identity_schema.sql`. PostgreSQL, database `identity`.

```mermaid
erDiagram
    ROLES ||--o{ ACCOUNTS : "has"

    ROLES {
        smallint id PK
        varchar code UK "ADMIN or CUSTOMER"
    }

    ACCOUNTS {
        uuid id PK
        citext email UK "case-insensitive, ACC-002"
        text password_hash "ACC-001"
        varchar full_name "ACC-001, ACC-011"
        smallint role_id FK
        varchar status "ACTIVE or DEACTIVATED, ACC-006"
        timestamptz created_at
        timestamptz updated_at
    }
```
