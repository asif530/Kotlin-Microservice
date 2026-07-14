# notification-service — ER Diagram

Source: `Archive/Development/Database` §4.1, verbatim schema at `Archive/Development/Database-Dev/mongo/00_notifications_schema.js`. MongoDB, database `notifications`. Single collection, append-only — no relationships within this service; see `combined.md` for its cross-service references.

```mermaid
erDiagram
    NOTIFICATIONS {
        string _id PK
        string accountId "NTF-003 owner, references identity-service accounts"
        string orderId "references order-service orders"
        string type "ORDER_PLACED or ORDER_CANCELLED — NTF-001, NTF-002"
        string message "optional, composed at write time from type, not a live join"
        date createdAt
    }
```
