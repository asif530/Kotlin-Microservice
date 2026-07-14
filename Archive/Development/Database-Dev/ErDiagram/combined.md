# MiniMart — Combined ER Diagram (all four services)

Source: `Archive/Development/Database` (all sections) and its §5 "Cross-service data duplication register". Each service still owns its own physical database — **no relationship below crossing a service boundary is a real foreign key**; every one is a plain ID column enforced at the application layer (via gRPC), per the database-per-service principle in `Archive/Development/Database` §0. That distinction is called out in each cross-service relationship label below, and is why this combined view is a companion to, not a replacement for, the four per-service diagrams in this directory.

```mermaid
erDiagram
    %% identity-service (PostgreSQL, db `identity`)
    ROLES ||--o{ ACCOUNTS : "has"

    %% order-service (PostgreSQL, db `orders`)
    ORDER_STATUS ||--o{ ORDERS : "has status"
    ORDERS ||--o{ ORDER_ITEMS : "contains"

    %% Cross-service references — reference-only columns, no DB-level FK (§0, §5)
    ACCOUNTS ||--o{ ORDERS : "places (cross-service, no FK)"
    ACCOUNTS ||--o{ NOTIFICATIONS : "owns (cross-service, no FK)"
    ORDERS ||--o{ NOTIFICATIONS : "triggers (cross-service, no FK)"
    PRODUCTS ||--o{ ORDER_ITEMS : "ordered as (cross-service, no FK; name/price snapshotted — ORD-005)"

    ROLES {
        smallint id PK
        varchar code UK "ADMIN or CUSTOMER"
    }

    ACCOUNTS {
        uuid id PK
        citext email UK "ACC-002"
        text password_hash
        varchar full_name
        smallint role_id FK
        varchar status "ACTIVE or DEACTIVATED"
        timestamptz created_at
        timestamptz updated_at
    }

    %% catalog-service (MongoDB, db `catalog`) — no relationships within its own service
    PRODUCTS {
        string _id PK
        string name
        string description
        string category
        decimal128 unitPrice
        int stockCount
        string status "ACTIVE or DEACTIVATED"
        date createdAt
        date updatedAt
    }

    ORDER_STATUS {
        smallint id PK
        varchar code UK "PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED"
    }

    ORDERS {
        uuid id PK
        uuid customer_id "references ACCOUNTS, cross-service, no FK"
        smallint status_id FK
        numeric total_amount
        varchar idempotency_key UK
        timestamptz created_at
        timestamptz updated_at
    }

    ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        uuid product_id "references PRODUCTS, cross-service, no FK"
        varchar product_name_snapshot "snapshotted at order time, ORD-005"
        numeric unit_price_snapshot "snapshotted at order time, ORD-005"
        integer quantity
        numeric line_total "generated"
    }

    %% notification-service (MongoDB, db `notifications`) — no relationships within its own service
    NOTIFICATIONS {
        string _id PK
        string accountId "references ACCOUNTS, cross-service, no FK"
        string orderId "references ORDERS, cross-service, no FK"
        string type "ORDER_PLACED or ORDER_CANCELLED"
        string message
        date createdAt
    }
```

Per `Archive/Development/Database` §5: no account email/name, no product category/description, and no order total are ever copied across a service boundary — the only fields duplicated anywhere are `product_name_snapshot`/`unit_price_snapshot` on `order_items` (required verbatim by ORD-005) and the plain ID references drawn above.
