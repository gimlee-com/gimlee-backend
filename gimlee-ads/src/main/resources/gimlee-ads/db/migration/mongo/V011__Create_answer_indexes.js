const collectionName = "gimlee-ads-answers";
const collection = db.getCollection(collectionName);

// Primary query: answers for a question, ordered by creation time
collection.createIndex(
    { "qid": 1, "ca": 1 },
    { name: "idx_question_created" }
);
