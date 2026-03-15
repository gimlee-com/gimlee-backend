const collectionName = "gimlee-reports";
const collection = db.getCollection(collectionName);

// Unique constraint: one report per user per target (question, answer, etc.)
collection.createIndex(
    { "tid": 1, "rid": 1 },
    { unique: true, name: "idx_target_reporter_unique" }
);
