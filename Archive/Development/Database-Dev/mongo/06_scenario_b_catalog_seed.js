// Step 06 · Scenario B · run against the `catalog` database.
// Administrator populates the catalog (CAT-001..CAT-009).
//
// prod-0002 is seeded at zero stock — CAT-007: a zero-stock product remains
// visible to customers, just marked out of stock; it cannot be ordered.
// prod-0004 deliberately shares its name with prod-0001 — CAT-005: product
// names don't have to be unique, a product is identified by its own record.
// prod-0005 is seeded ACTIVE and then immediately deactivated in the same
// script — CAT-008: a Deactivated product is fully hidden and unorderable,
// distinct from merely being out of stock.

const now = new Date();

db.products.insertMany([
  {
    _id: "10000000-0000-4000-8000-000000000001",
    name: "Trail Runner 2.0",
    description: "Lightweight trail running shoe, standard fit.",
    category: "Footwear",
    unitPrice: NumberDecimal("89.99"),
    stockCount: NumberInt(25),
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  },
  {
    _id: "10000000-0000-4000-8000-000000000002",
    name: "Insulated Steel Bottle",
    description: "750ml double-wall insulated bottle.",
    category: "Home & Kitchen",
    unitPrice: NumberDecimal("24.50"),
    stockCount: NumberInt(0),          // CAT-007: zero stock, still visible
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  },
  {
    _id: "10000000-0000-4000-8000-000000000003",
    name: "Wireless Earbuds Pro",
    description: "Active noise-cancelling wireless earbuds.",
    category: "Electronics",
    unitPrice: NumberDecimal("129.00"),
    stockCount: NumberInt(40),
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  },
  {
    _id: "10000000-0000-4000-8000-000000000004",
    name: "Trail Runner 2.0",             // CAT-005: same name as prod-0001, different record
    description: "Lightweight trail running shoe, wide fit.",
    category: "Footwear",
    unitPrice: NumberDecimal("94.99"),
    stockCount: NumberInt(15),
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  },
  {
    _id: "10000000-0000-4000-8000-000000000005",
    name: "Discontinued Tent Stakes",
    description: "Aluminum tent stakes, 6-pack.",
    category: "Outdoor",
    unitPrice: NumberDecimal("12.00"),
    stockCount: NumberInt(5),
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  }
]);

// Administrator deactivates prod-0005 (CAT-008) — a business decision,
// distinct from and independent of its remaining stock count.
db.products.updateOne(
  { _id: "10000000-0000-4000-8000-000000000005" },
  { $set: { status: "DEACTIVATED", updatedAt: new Date() } }
);

print("Scenario B done. Products in catalog: " + db.products.countDocuments());
