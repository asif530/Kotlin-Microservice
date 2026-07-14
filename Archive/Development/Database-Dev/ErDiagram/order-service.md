# order-service — ER Diagram

Source: `Archive/Development/Database` §3.1, verbatim schema at `Archive/Development/Database-Dev/postgres/00_orders_schema.sql`. PostgreSQL, database `orders`.

```mermaid
erDiagram
    ORDER_STATUS ||--o{ ORDERS : "has status"
    ORDERS ||--o{ ORDER_ITEMS : "contains"

    ORDER_STATUS {
        smallint id PK
        varchar code UK "PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED — ORD-010"
    }

    ORDERS {
        uuid id PK
        uuid customer_id "references identity-service accounts, no cross-DB FK"
        smallint status_id FK
        numeric total_amount "ORD-006, greater than or equal to 0"
        varchar idempotency_key UK "ORD-009"
        timestamptz created_at
        timestamptz updated_at
    }

    ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        uuid product_id "references catalog-service products, no cross-DB FK"
        varchar product_name_snapshot "ORD-005, captured at order time"
        numeric unit_price_snapshot "ORD-005, greater than 0"
        integer quantity "ORD-003, greater than or equal to 1"
        numeric line_total "generated: unit_price_snapshot times quantity"
    }
```

`order_id, product_id` is also a unique constraint (`uq_order_product`, ORD-004) — one line item per product per order, not representable as a Mermaid relationship, noted here instead.
