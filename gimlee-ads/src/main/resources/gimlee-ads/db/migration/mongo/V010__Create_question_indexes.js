const collectionName = "gimlee-ads-questions";
const collection = db.getCollection(collectionName);

// Primary query: public answered questions for an ad, sorted by upvotes
collection.createIndex(
    { "aid": 1, "s": 1, "uc": -1 },
    { name: "idx_aid_status_upvotes" }
);

// Seller query: unanswered questions for their ad (partial index — only PENDING)
collection.createIndex(
    { "aid": 1, "ca": -1 },
    { name: "idx_aid_pending_created", partialFilterExpression: { "s": "P" } }
);

// Rate limiting: count unanswered questions by author per ad
collection.createIndex(
    { "uid": 1, "aid": 1 },
    { name: "idx_author_ad", partialFilterExpression: { "s": "P" } }
);
