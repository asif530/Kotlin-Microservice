// Step 08 · Scenario D, step 1 of 3 · run against the `catalog` database.
// Alice checks out with 2x Trail Runner 2.0 (prod-0001) + 1x Wireless
// Earbuds Pro (prod-0003). This is the exact atomic conditional update from
// Archive/Development/Database §2.3 — catalog-service is the sole authority
// on stock (CAT-011) and reserves it before order-service commits the order
// (ORD-007/ORD-008).
//
// Run this BEFORE step 09 (the order row is only ever created if every
// reservation here succeeds).

const results = [
  db.products.findOneAndUpdate(
    { _id: "10000000-0000-4000-8000-000000000001", status: "ACTIVE", stockCount: { $gte: 2 } },
    { $inc: { stockCount: -2 }, $set: { updatedAt: new Date() } }
  ),
  db.products.findOneAndUpdate(
    { _id: "10000000-0000-4000-8000-000000000003", status: "ACTIVE", stockCount: { $gte: 1 } },
    { $inc: { stockCount: -1 }, $set: { updatedAt: new Date() } }
  )
];

results.forEach(r => {
  if (r === null) throw new Error("ReserveStock failed — a line item could not be satisfied; do NOT proceed to step 09 (ORD-007: reject the whole order, no partial reservation).");
});

print("Scenario D step 1 done. prod-0001 stock -> " + db.products.findOne({_id:"10000000-0000-4000-8000-000000000001"}).stockCount);
print("Scenario D step 1 done. prod-0003 stock -> " + db.products.findOne({_id:"10000000-0000-4000-8000-000000000003"}).stockCount);
