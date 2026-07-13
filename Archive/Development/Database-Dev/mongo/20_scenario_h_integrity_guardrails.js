// Step 20 · Scenario H (Mongo half) · run against the `catalog` database.
//
// *** Both inserts below are EXPECTED TO FAIL against the $jsonSchema
// validator from step 03 (00_catalog_schema.js). *** That failure IS the
// proof the schema enforces the rule. Each is wrapped in try/catch so the
// script reports PASS/FAIL for both cases instead of stopping at the first
// one — mongosh does not keep executing a piped script past an uncaught
// exception the way psql's default error handling does.

function expectValidationFailure(label, doc) {
  try {
    db.products.insertOne(doc);
    print("FAIL — " + label + ": insert unexpectedly SUCCEEDED (validator did not reject it).");
  } catch (e) {
    print("PASS — " + label + ": insert was rejected as expected. " + e.codeName + ": " + (e.errInfo ? JSON.stringify(e.errInfo.details) : e.message));
  }
}

const now = new Date();

// G5 — CAT-002: unit price must be strictly greater than zero.
expectValidationFailure("CAT-002 (unitPrice must be > 0)", {
  _id: "88888888-0000-4000-8000-000000000001",
  name: "Free Sample Widget",
  description: "Should be rejected — zero price.",
  category: "Test",
  unitPrice: NumberDecimal("0.00"),
  stockCount: NumberInt(10),
  status: "ACTIVE",
  createdAt: now,
  updatedAt: now
});

// G6 — CAT-003: stock count can never go below zero.
expectValidationFailure("CAT-003 (stockCount must be >= 0)", {
  _id: "88888888-0000-4000-8000-000000000002",
  name: "Oversold Widget",
  description: "Should be rejected — negative stock.",
  category: "Test",
  unitPrice: NumberDecimal("9.99"),
  stockCount: NumberInt(-5),
  status: "ACTIVE",
  createdAt: now,
  updatedAt: now
});

print("Scenario H (mongo) done.");
