// Step 16 · Scenario F, step 3 of 4 · run against the `notifications` database.
// NTF-001 for Chen's order. Runs after step 15.

db.notifications.insertOne({
  _id: "40000000-0000-4000-8000-000000000003",
  accountId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",  // Chen
  orderId: "20000000-0000-4000-8000-000000000002",
  type: "ORDER_PLACED",
  message: "Your order has been placed.",
  createdAt: new Date()
});

print("Scenario F step 3 done. Notifications for Chen: " + db.notifications.countDocuments({accountId:"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}));
