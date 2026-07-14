# catalog-service — ER Diagram

Source: `Archive/Development/Database` §2.1, verbatim schema at `Archive/Development/Database-Dev/mongo/00_catalog_schema.js`. MongoDB, database `catalog`. Single collection — no relationships to draw within this service; see `combined.md` for its cross-service references.

```mermaid
erDiagram
    PRODUCTS {
        string _id PK "UUID, matches proto's string product_id"
        string name "CAT-001, CAT-005 not unique"
        string description "CAT-001"
        string category "CAT-004, plain indexed string, not a reference collection"
        decimal128 unitPrice "CAT-002, greater than 0"
        int stockCount "CAT-003, greater than or equal to 0"
        string status "ACTIVE or DEACTIVATED, CAT-008"
        date createdAt
        date updatedAt
    }
```
