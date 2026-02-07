// Flyway migration script
// Version: 4
// Description: Add category index for the gimlee-ads-advertisements collection

const collectionName = "gimlee-ads-advertisements";
const collection = db.getCollection(collectionName);

// Create compound index on 'cats' (FIELD_CATEGORY_IDS) and 'crt' (FIELD_CREATED_AT) for ACTIVE ads.
// This supports filtering by category while maintaining sort by creation date.
collection.createIndex(
    { "cats": 1, "crt": -1 },
    { partialFilterExpression: { "s": "ACTIVE" } }
);
