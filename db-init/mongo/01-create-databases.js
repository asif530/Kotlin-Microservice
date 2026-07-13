// MongoDB only materializes a database once it holds at least one collection,
// so `catalog` (catalog-service) and `notifications` (notification-service)
// are bootstrapped here with a throwaway marker collection each. The real
// `products` / `notifications` collections (with their $jsonSchema
// validators, per Archive/Development/Database §2.1/§4.1) are created by each
// service's own Flamingock migrations, not here.
db = db.getSiblingDB('catalog');
db.createCollection('_bootstrap');
db.getCollection('_bootstrap').insertOne({ initializedAt: new Date() });

db = db.getSiblingDB('notifications');
db.createCollection('_bootstrap');
db.getCollection('_bootstrap').insertOne({ initializedAt: new Date() });
