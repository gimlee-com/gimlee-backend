// Flyway migration script
// Version: 2
// Description: Seed status history on existing purchases and add indexes for filtered queries

const collectionName = "gimlee-purchases-purchases";
const collection = db.getCollection(collectionName);

// Seed status history for existing purchases that don't have it.
// Creates a single entry using the current status and createdAt timestamp.
collection.updateMany(
    { sh: { $exists: false } },
    [
        {
            $set: {
                sh: [
                    { st: "$st", ts: "$ca" }
                ]
            }
        }
    ]
);

// Compound index: seller + status + created date (for filtered seller order lists)
collection.createIndex(
    { sid: 1, st: 1, ca: -1 },
    { name: "idx_sid_st_ca" }
);

// Compound index: buyer + status + created date (for filtered buyer purchase lists)
collection.createIndex(
    { bid: 1, st: 1, ca: -1 },
    { name: "idx_bid_st_ca" }
);

// Index on items.adId for order-count-per-ad aggregation
collection.createIndex(
    { "its.aid": 1 },
    { name: "idx_its_aid" }
);
