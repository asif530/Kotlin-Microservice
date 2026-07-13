// Step 11 · Scenario E, step 1 of 3 · run against the `catalog` database.
// Alice cancels her order while it's still Placed (ORD-011). ORD-012:
// cancelling restores the cancelled line items' quantities back to
// available stock. Deliberately not conditioned on status: "ACTIVE" — see
// Archive/Development/Database §2.3: stock must be restored accurately
// even if the product was deactivated after the order was placed.

db.products.updateOne(
  { _id: "10000000-0000-4000-8000-000000000001" },
  { $inc: { stockCount: 2 }, $set: { updatedAt: new Date() } }
);
db.products.updateOne(
  { _id: "10000000-0000-4000-8000-000000000003" },
  { $inc: { stockCount: 1 }, $set: { updatedAt: new Date() } }
);

print("Scenario E step 1 done. prod-0001 stock -> " + db.products.findOne({_id:"10000000-0000-4000-8000-000000000001"}).stockCount);
print("Scenario E step 1 done. prod-0003 stock -> " + db.products.findOne({_id:"10000000-0000-4000-8000-000000000003"}).stockCount);
