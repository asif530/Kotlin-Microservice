// Step 14 · Scenario F, step 1 of 4 · run against the `catalog` database.
// Chen checks out with 1x Trail Runner 2.0, wide fit (prod-0004) — this is
// the product that deliberately shares a name with prod-0001 (CAT-005).

const result = db.products.findOneAndUpdate(
  { _id: "10000000-0000-4000-8000-000000000004", status: "ACTIVE", stockCount: { $gte: 1 } },
  { $inc: { stockCount: -1 }, $set: { updatedAt: new Date() } }
);
if (result === null) throw new Error("ReserveStock failed for prod-0004 — do NOT proceed to step 15.");

print("Scenario F step 1 done. prod-0004 stock -> " + db.products.findOne({_id:"10000000-0000-4000-8000-000000000004"}).stockCount);
