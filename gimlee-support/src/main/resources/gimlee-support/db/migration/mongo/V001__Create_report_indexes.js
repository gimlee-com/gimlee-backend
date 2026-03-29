(function() {
    // Drop legacy collection (alpha — no backward compatibility needed)
    if (db.getCollectionNames().indexOf("gimlee-reports") !== -1) {
        db.getCollection("gimlee-reports").drop();
    }

    const collection = db.getCollection("gimlee-support-reports");

    // Unique constraint: one report per user per target
    collection.createIndex(
        { "tid": 1, "rid": 1 },
        { unique: true, name: "idx_target_reporter_unique" }
    );

    // Status + createdAt for filtered listing
    collection.createIndex(
        { "st": 1, "ca": -1 },
        { name: "idx_status_createdAt" }
    );

    // Assignee lookup
    collection.createIndex(
        { "aid": 1, "st": 1 },
        { name: "idx_assignee_status", partialFilterExpression: { "aid": { $type: "objectId" } } }
    );

    // Target grouping (for sibling queries and siblingCount updates)
    collection.createIndex(
        { "tt": 1, "tid": 1 },
        { name: "idx_targetType_targetId" }
    );

    // Sibling count sorting
    collection.createIndex(
        { "sc": -1, "ca": -1 },
        { name: "idx_siblingCount_createdAt" }
    );

    // Target title for search
    collection.createIndex(
        { "ttl": 1 },
        { name: "idx_targetTitle", partialFilterExpression: { "ttl": { $type: "string" } } }
    );

    // Reporter's own reports
    collection.createIndex(
        { "rid": 1, "ca": -1 },
        { name: "idx_reporter_createdAt" }
    );

    // UpdatedAt for sorting
    collection.createIndex(
        { "ua": -1 },
        { name: "idx_updatedAt" }
    );

    // Resolved reports (for stats)
    collection.createIndex(
        { "ra": -1 },
        { name: "idx_resolvedAt", partialFilterExpression: { "ra": { $type: "long" } } }
    );
})();
