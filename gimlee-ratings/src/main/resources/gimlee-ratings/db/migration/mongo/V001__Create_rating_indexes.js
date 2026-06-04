(function() {
    var ratings = db.getCollection("gimlee-ratings-ratings");
    var eligibility = db.getCollection("gimlee-ratings-eligibility");
    var aggregates = db.getCollection("gimlee-ratings-aggregates");

    // --- Ratings ---

    // Latest PUBLISHED ratings RECEIVED by a user, SELLER reputation (public profile).
    ratings.createIndex(
        { "rte": 1, "pa": -1 },
        { name: "idx_ratee_publishedAt_seller", partialFilterExpression: { "rk": "SEL", "st": "PUB" } }
    );

    // Latest ratings WRITTEN BY an author (aggregated-by-author view).
    ratings.createIndex(
        { "rtr": 1, "pa": -1 },
        { name: "idx_rater_publishedAt" }
    );

    // One rating per (rater, ratee, context) — idempotency / anti-duplicate.
    ratings.createIndex(
        { "cid": 1, "rtr": 1, "rte": 1, "ct": 1 },
        { unique: true, name: "idx_context_rater_ratee_unique" }
    );

    // Admin/moderation triage: most-reported first (partial — only rows that have reports).
    ratings.createIndex(
        { "rc": -1, "ca": -1 },
        { name: "idx_reportCount_createdAt", partialFilterExpression: { "rc": { $gt: 0 } } }
    );

    // --- Eligibility ---

    // A rater's actionable + upcoming reviews ("you have N to write", dwell countdown).
    eligibility.createIndex(
        { "rtr": 1, "af": 1 },
        { name: "idx_rater_activeFrom_pending", partialFilterExpression: { "st": "PND" } }
    );

    // Idempotent grant per (context, rater) — unique.
    eligibility.createIndex(
        { "cid": 1, "rtr": 1, "ct": 1 },
        { unique: true, name: "idx_context_rater_unique" }
    );

    // Sweeper support — find PENDING grants past the window to HARD-DELETE.
    eligibility.createIndex(
        { "exp": 1 },
        { name: "idx_pending_expiry", partialFilterExpression: { "st": "PND" } }
    );

    // --- Aggregates ---

    // Per-user, per reputation-kind lookup (seller vs buyer score) — unique.
    aggregates.createIndex(
        { "rte": 1, "rk": 1 },
        { unique: true, name: "idx_ratee_repKind_unique" }
    );
})();
