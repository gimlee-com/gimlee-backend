// Flyway migration script
// Version: 7
// Description: Create indexes for the gimlee-ads-watchlist collection

const collectionName = "gimlee-ads-watchlist";
const collection = db.getCollection(collectionName);

// Create compound unique index on 'uid' (FIELD_USER_ID) and 'aid' (FIELD_AD_ID)
// This supports efficient lookup by user (prefix) and unique (user, ad) pair enforcement
collection.createIndex({ "uid": 1, "aid": 1 }, { unique: true });
