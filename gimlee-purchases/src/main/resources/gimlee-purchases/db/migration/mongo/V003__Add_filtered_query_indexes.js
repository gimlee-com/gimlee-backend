// Flyway migration script
// Version: 3
// Description: Add compound indexes for amount-sorted queries

const collectionName = "gimlee-purchases-purchases";
const collection = db.getCollection(collectionName);

// Compound index for seller queries sorted by amount
collection.createIndex(
    { "sid": 1, "tamt": -1 },
    { name: "idx_seller_amount", background: true }
);

// Compound index for buyer queries sorted by amount
collection.createIndex(
    { "bid": 1, "tamt": -1 },
    { name: "idx_buyer_amount", background: true }
);
