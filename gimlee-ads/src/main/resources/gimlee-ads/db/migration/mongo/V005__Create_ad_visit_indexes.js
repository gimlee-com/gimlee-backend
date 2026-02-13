db["gimlee-ads-ad-visits"].createIndex(
    { "aid": 1, "d": 1 },
    { unique: true }
);

db["gimlee-ads-ad-visits"].createIndex(
    { "d": 1 },
    { expireAfterSeconds: 31536000 } // 365 days
);
