// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-ads-advertisements collection

const collectionName = "gimlee-ads-advertisements";
const collection = db.getCollection(collectionName);

// Create 2dsphere index on 'loc' (FIELD_LOCATION) for geospatial queries
collection.createIndex({ "loc" : "2dsphere" });

// Create ascending index on 'cid' (FIELD_CITY_ID) for city filtering
collection.createIndex({ "cid" : 1 });

// Create ascending index on 'uid' (FIELD_USER_ID) for fetching user's ads
collection.createIndex({ "uid" : 1 });