// Flyway migration script
// Version: 2
// Description: Create indexes for the gimlee-chat-conversations collection

const collectionName = "gimlee-chat-conversations";

// Create the collection if it doesn't exist
if (!db.getCollectionNames().contains(collectionName)) {
    db.createCollection(collectionName);
}

const collection = db.getCollection(collectionName);

// Unique index: one conversation per linked entity (idempotent creation)
collection.createIndex(
    { "lti": 1, "lid": 1 },
    { unique: true, partialFilterExpression: { "lti": { $exists: true } } }
);

// List user's conversations sorted by last activity
collection.createIndex(
    { "pts.uid": 1, "lat": -1 }
);

// Filter active conversations
collection.createIndex(
    { "st": 1 },
    { partialFilterExpression: { "st": "ACT" } }
);
