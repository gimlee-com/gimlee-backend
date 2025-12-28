// Flyway migration script
// Version: 2
// Description: Add status index for the gimlee-ads-advertisements collection

const collectionName = "gimlee-ads-advertisements";
const collection = db.getCollection(collectionName);

// Create partial index on 'crt' (FIELD_CREATED_AT) for ACTIVE ads only.
// This is hit by general browsing queries which always filter by status = ACTIVE and sort by creation date.
collection.createIndex(
    { "crt": -1 },
    { partialFilterExpression: { "s": "ACTIVE" } }
);
