(function() {
    var ratings = db.getCollection("gimlee-ratings-ratings");

    // Latest PUBLISHED ratings RECEIVED by a user, BUYER reputation (public profile).
    // This was missing from V001 — only the seller variant was created.
    ratings.createIndex(
        { "rte": 1, "pa": -1 },
        { name: "idx_ratee_publishedAt_buyer", partialFilterExpression: { "rk": "BUY", "st": "PUB" } }
    );
})();
