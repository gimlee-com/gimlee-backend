// Add version field to all existing advertisement documents for optimistic locking.
db.getCollection("gimlee-ads-advertisements").updateMany(
    { "v": { $exists: false } },
    { $set: { "v": NumberLong(0) } }
);
