// Flyway migration script
// Version: 3
// Description: Create indexes for refresh tokens collection

(function() {
    const collectionName = "gimlee-refreshTokens";
    const collection = db.getCollection(collectionName);

    // Unique index on hashed token for fast lookup during refresh
    collection.createIndex({ "tkn": 1 }, { unique: true });

    // Index on userId for per-user session queries and revoke-all
    collection.createIndex({ "uid": 1 });

    // Partial index on expiresAt for cleanup of non-revoked expired tokens
    collection.createIndex(
        { "exp": 1 },
        { partialFilterExpression: { "rev": false } }
    );

    // Index on familyId for family-based revocation
    collection.createIndex({ "fam": 1 });
})();
