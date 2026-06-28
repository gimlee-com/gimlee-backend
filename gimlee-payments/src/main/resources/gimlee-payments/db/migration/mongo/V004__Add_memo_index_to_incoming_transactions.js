// Flyway migration script
// Version: 2
// Description: Add index for searching transactions by memo in gimlee-payments-incoming-transactions

(function() {
    const collectionName = "gimlee-payments-incoming-transactions";
    const collection = db.getCollection(collectionName);

    // Index on 'm' (FIELD_MEMO) for faster lookups by memo.
    // Use sparse: true because not all transactions have a memo.
    collection.createIndex({ "m": 1 }, { sparse: true });
})();
