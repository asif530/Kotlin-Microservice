// Step 22 · verification queries · Mongo half.
// Run with: docker exec -i kotlincrud-mongodb mongosh -u kotlincrud -p kotlincrud --authenticationDatabase admin --quiet < 22_checks.js
// (this file reads both `catalog` and `notifications` via getSiblingDB; read-only throughout)

const catalog = db.getSiblingDB("catalog");
const notif = db.getSiblingDB("notifications");

print("--- Scenario B: all 5 seeded products ---");
catalog.products.find({}, { name: 1, category: 1, unitPrice: 1, stockCount: 1, status: 1 }).sort({ _id: 1 }).forEach(printjson);

print("--- CAT-007 proof: prod-0002 is zero stock but still ACTIVE/visible ---");
printjson(catalog.products.findOne({ _id: "10000000-0000-4000-8000-000000000002" }, { name: 1, stockCount: 1, status: 1 }));

print("--- CAT-008 proof: prod-0005 is DEACTIVATED, excluded from an active-only browse ---");
print("active products count (should be 4 of 5): " + catalog.products.countDocuments({ status: "ACTIVE" }));
print("prod-0005 in an active-only browse (should be 0): " + catalog.products.countDocuments({ status: "ACTIVE", _id: "10000000-0000-4000-8000-000000000005" }));

print("--- CAT-005 proof: two distinct products share the name 'Trail Runner 2.0' ---");
print("count (should be 2): " + catalog.products.countDocuments({ name: "Trail Runner 2.0" }));

print("--- Scenario D+E stock reconciliation: placed then cancelled, stock is back to original ---");
print("prod-0001 stockCount (should be 25): " + catalog.products.findOne({ _id: "10000000-0000-4000-8000-000000000001" }).stockCount);
print("prod-0003 stockCount (should be 40): " + catalog.products.findOne({ _id: "10000000-0000-4000-8000-000000000003" }).stockCount);

print("--- Scenario F: Chen's order was never cancelled, so prod-0004 stays decremented ---");
print("prod-0004 stockCount (should be 14): " + catalog.products.findOne({ _id: "10000000-0000-4000-8000-000000000004" }).stockCount);

print("--- Scenario G proof: Bilal's rejected checkout made no change to prod-0002 ---");
print("prod-0002 stockCount (should still be 0): " + catalog.products.findOne({ _id: "10000000-0000-4000-8000-000000000002" }).stockCount);

print("--- Scenario H guardrail proof (catalog): neither invalid product exists ---");
print("count (should be 0): " + catalog.products.countDocuments({ _id: { $in: ["88888888-0000-4000-8000-000000000001", "88888888-0000-4000-8000-000000000002"] } }));

print("--- Scenario D+E: Alice's notification history, oldest first (NTF-001 then NTF-002) ---");
notif.notifications.find({ accountId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" }).sort({ createdAt: 1 }).forEach(printjson);

print("--- Scenario F: Chen's notification history (NTF-001 only, she never cancelled) ---");
notif.notifications.find({ accountId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc" }).forEach(printjson);

print("--- Scenario G proof: Bilal has zero notifications — a rejected checkout never fires NTF-001 ---");
print("count (should be 0): " + notif.notifications.countDocuments({ accountId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb" }));
