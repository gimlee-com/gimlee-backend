// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-media-items collection

const collectionName = "gimlee-media-items";
const collection = db.getCollection(collectionName);

// Create unique index on 'f' (FIELD_FILENAME)
collection.createIndex({ "f" : 1 }, { unique: true });

// Note: No other indexes are currently hit by repository methods.
