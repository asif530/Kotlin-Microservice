// Run against the `notifications` database.
// Verbatim from Archive/Development/Database §4.1.

db.createCollection("notifications", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "accountId", "orderId", "type", "createdAt"],
      properties: {
        _id:       { bsonType: "string" },
        accountId: { bsonType: "string" },
        orderId:   { bsonType: "string" },
        type:      { enum: ["ORDER_PLACED", "ORDER_CANCELLED"] },
        message:   { bsonType: "string" },
        createdAt: { bsonType: "date" }
      }
    }
  },
  validationLevel: "strict",
  validationAction: "error"
});

db.notifications.createIndex({ accountId: 1, createdAt: -1 });

// See Archive/Issues/Shell — must use getCollection(), not db._bootstrap.
db.getCollection("_bootstrap").drop();

print("notifications schema ready: " + db.getCollectionNames());
