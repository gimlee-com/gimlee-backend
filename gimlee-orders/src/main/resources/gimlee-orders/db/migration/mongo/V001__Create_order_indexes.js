// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-orders-orders collection

const collectionName = "gimlee-orders-orders";
const collection = db.getCollection(collectionName);

// Create index on _id (default, but here as a placeholder for the collection)
// Currently no other indexes are hit by repository methods.
