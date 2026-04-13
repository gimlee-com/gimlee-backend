(function() {
    const collection = db.getCollection("gimlee-notifications");

    // Primary query: user's notifications sorted by time (cursor-based pagination)
    collection.createIndex(
        { "uid": 1, "ca": -1, "_id": -1 },
        { name: "idx_userId_createdAt_id" }
    );

    // Unread notifications per user (for unread count and mark-all-read)
    collection.createIndex(
        { "uid": 1, "r": 1, "ca": -1 },
        { name: "idx_userId_read_createdAt", partialFilterExpression: { "r": false } }
    );

    // Category-filtered queries per user
    collection.createIndex(
        { "uid": 1, "cat": 1, "ca": -1, "_id": -1 },
        { name: "idx_userId_category_createdAt_id" }
    );

    // Cleanup job: find notifications older than retention period
    collection.createIndex(
        { "ca": 1 },
        { name: "idx_createdAt_cleanup" }
    );
})();
