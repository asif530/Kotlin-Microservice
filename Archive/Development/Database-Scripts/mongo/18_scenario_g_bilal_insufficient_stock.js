// Step 18 · Scenario G · run against the `catalog` database.
// Bilal tries to buy 5x Insulated Steel Bottle (prod-0002), which has been
// at zero stock since Scenario B (CAT-007: still visible while browsing,
// just marked out of stock). GEN-001 / ORD-007: the reservation must fail
// and nothing else may happen — no stock change, no order, no notification.
// This IS the proof: the atomic conditional update finds no matching
// document and returns null. Nothing downstream of this script runs.

const result = db.products.findOneAndUpdate(
  { _id: "10000000-0000-4000-8000-000000000002", status: "ACTIVE", stockCount: { $gte: 5 } },
  { $inc: { stockCount: -5 }, $set: { updatedAt: new Date() } }
);

if (result !== null) {
  throw new Error("UNEXPECTED: reservation succeeded — this should have failed. Stock was not actually zero, or the query is wrong.");
}

const unchanged = db.products.findOne({ _id: "10000000-0000-4000-8000-000000000002" });
print("Scenario G done (expected outcome). findOneAndUpdate returned null — no reservation made.");
print("prod-0002 stockCount is still: " + unchanged.stockCount + " (unchanged, proves ORD-007: no partial/incorrect deduction on a rejected checkout).");
