// Run against the `catalog` database.
// Verbatim from Archive/Development/Database §2.1.

db.createCollection("products", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "name", "description", "category", "unitPrice", "stockCount", "status", "createdAt", "updatedAt"],
      properties: {
        _id:         { bsonType: "string" },
        name:        { bsonType: "string", minLength: 1 },
        description: { bsonType: "string" },
        category:    { bsonType: "string", minLength: 1 },
        unitPrice:   { bsonType: "decimal", minimum: 0, exclusiveMinimum: true },
        stockCount:  { bsonType: "int", minimum: 0 },
        status:      { enum: ["ACTIVE", "DEACTIVATED"] },
        createdAt:   { bsonType: "date" },
        updatedAt:   { bsonType: "date" }
      }
    }
  },
  validationLevel: "strict",
  validationAction: "error"
});

db.products.createIndex({ status: 1, category: 1 });
db.products.createIndex(
  { name: "text", description: "text" },
  { weights: { name: 5, description: 1 } }
);

// The bootstrap placeholder from db-init/mongo/01-create-databases.js is no
// longer needed now that the real collection exists. Using getCollection(),
// not dot-notation — see Archive/Issues/Shell for why db._bootstrap breaks.
db.getCollection("_bootstrap").drop();

print("catalog schema ready: " + db.getCollectionNames());
