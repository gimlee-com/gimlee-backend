// Flyway migration script
// Version: 2
// Description: Create indexes for user bans collection

// --- gimlee-userBans ---
(function() {
    const collectionName = "gimlee-userBans";
    const collection = db.getCollection(collectionName);

    // Active ban lookup by user (used by BannedUserCache loader)
    collection.createIndex({ "uid": 1, "act": 1 });

    // Ban history by user sorted by date
    collection.createIndex({ "uid": 1, "ba": -1 });

    // Expired active bans (used by BanExpiryJob)
    collection.createIndex(
        { "bu": 1 },
        { partialFilterExpression: { "act": true, "bu": { $type: "long" } } }
    );
})();
