// Step 13 · Scenario E, step 3 of 3 · run against the `notifications` database.
// NTF-002: when an order is cancelled, an "order cancelled" notification is
// recorded for that order's buyer.

db.notifications.insertOne({
  _id: "40000000-0000-4000-8000-000000000002",
  accountId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",  // Alice
  orderId: "20000000-0000-4000-8000-000000000001",
  type: "ORDER_CANCELLED",
  message: "Your order has been cancelled.",
  createdAt: new Date()
});

print("Scenario E step 3 done. Notifications for Alice: " + db.notifications.countDocuments({accountId:"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"}));
