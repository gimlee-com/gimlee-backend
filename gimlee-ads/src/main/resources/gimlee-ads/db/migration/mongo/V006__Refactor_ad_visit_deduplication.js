// 1. Create deduplication collection and TTL index
db["gimlee-ads-ad-visit-dedup"].createIndex(
    { "exp": 1 },
    { expireAfterSeconds: 86400 } // Keep deduplication data for 24 hours
);

// 2. Update visits collection: Remove legacy visit hashes to save space
db["gimlee-ads-ad-visits"].updateMany(
    {},
    { $unset: { "vh": "" } }
);

// 3. Fix TTL index in visits collection
// The old index on "d" was ineffective because "d" is an Integer.
db["gimlee-ads-ad-visits"].dropIndex("d_1");

// Create new TTL index on the "exp" Date field
db["gimlee-ads-ad-visits"].createIndex(
    { "exp": 1 },
    { expireAfterSeconds: 31536000 } // 365 days
);
