(function() {
    // --- gimlee-support-tickets ---
    const tickets = db.getCollection("gimlee-support-tickets");

    // Status + createdAt for filtered listing
    tickets.createIndex(
        { "st": 1, "ca": -1 },
        { name: "idx_status_createdAt" }
    );

    // Priority ordering
    tickets.createIndex(
        { "po": -1, "ca": -1 },
        { name: "idx_priorityOrder_createdAt" }
    );

    // Assignee lookup
    tickets.createIndex(
        { "aid": 1, "st": 1 },
        { name: "idx_assignee_status", partialFilterExpression: { "aid": { $type: "objectId" } } }
    );

    // Creator's tickets
    tickets.createIndex(
        { "cid": 1, "ca": -1 },
        { name: "idx_creator_createdAt" }
    );

    // Last message sorting
    tickets.createIndex(
        { "lma": -1 },
        { name: "idx_lastMessageAt", partialFilterExpression: { "lma": { $type: "long" } } }
    );

    // Resolved stats
    tickets.createIndex(
        { "ra": -1 },
        { name: "idx_resolvedAt", partialFilterExpression: { "st": "R" } }
    );

    // --- gimlee-support-messages ---
    const messages = db.getCollection("gimlee-support-messages");

    // Messages by ticket (always queried together)
    messages.createIndex(
        { "tid": 1, "ca": 1 },
        { name: "idx_ticket_createdAt" }
    );
})();
