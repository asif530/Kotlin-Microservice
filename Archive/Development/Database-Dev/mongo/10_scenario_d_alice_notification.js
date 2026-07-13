// Step 10 · Scenario D, step 3 of 3 · run against the `notifications` database.
// NTF-001: when an order is successfully placed, an "order placed"
// notification is recorded for that order's buyer. Runs after step 09
// (the order must actually exist and commit first).

db.notifications.insertOne({
  _id: "40000000-0000-4000-8000-000000000001",
  accountId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",  // Alice
  orderId: "20000000-0000-4000-8000-000000000001",
  type: "ORDER_PLACED",
  message: "Your order has been placed.",
  createdAt: new Date()
});

print("Scenario D step 3 done. Notifications for Alice: " + db.notifications.countDocuments({accountId:"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"}));
