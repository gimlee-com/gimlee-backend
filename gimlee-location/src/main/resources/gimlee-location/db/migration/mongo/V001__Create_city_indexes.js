// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-location-cities collection

const citiesCollection = db.getCollection("gimlee-location-cities");

// Country code index for filtered search
citiesCollection.createIndex({ "cc": 1 });

// Population index for sorting/boosting queries
citiesCollection.createIndex({ "pop": -1 });


// Flyway migration script
// Description: Create indexes for the gimlee-location-city-names collection

const namesCollection = db.getCollection("gimlee-location-city-names");

// Compound index for language-specific name lookups by city
namesCollection.createIndex({ "cid": 1, "lang": 1 });

// Index for preferred name resolution
namesCollection.createIndex({ "cid": 1, "lang": 1, "pref": 1 });
