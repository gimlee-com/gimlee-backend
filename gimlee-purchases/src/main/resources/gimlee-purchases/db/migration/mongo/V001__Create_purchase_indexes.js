// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-purchases-purchases collection

const collectionName = "gimlee-purchases-purchases";
const collection = db.getCollection(collectionName);

// Create index on _id (default, but here as a placeholder for the collection)
// Currently no other indexes are hit by repository methods.
