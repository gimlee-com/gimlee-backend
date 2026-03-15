const collectionName = "gimlee-ads-upvotes";
const collection = db.getCollection(collectionName);

// Unique constraint: one upvote per user per question
collection.createIndex(
    { "qid": 1, "uid": 1 },
    { unique: true, name: "idx_question_user_unique" }
);
