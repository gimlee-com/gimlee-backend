// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-payments collections

// --- gimlee-payments-transactions ---
(function() {
    const collectionName = "gimlee-payments-transactions";
    const collection = db.getCollection(collectionName);

    // Partial index on 'st' (FIELD_STATUS) for AWAITING_CONFIRMATION (status id: 1)
    // Hit by PirateChainPaymentMonitor
    collection.createIndex(
        { "_id": 1 },
        { partialFilterExpression: { "st": 1 } }
    );
})();

// --- gimlee-payments-events ---
(function() {
    const collectionName = "gimlee-payments-events";
    const collection = db.getCollection(collectionName);

    // No indexes currently hit by repository methods.
})();

// --- gimlee-payments-user-addresses ---
(function() {
    const collectionName = "gimlee-payments-user-addresses";
    const collection = db.getCollection(collectionName);

    // _id is the userId and is indexed by default.
    // No other indexes currently hit by repository methods.
})();

// --- gimlee-payments-incoming-transactions ---
(function() {
    const collectionName = "gimlee-payments-incoming-transactions";
    const collection = db.getCollection(collectionName);

    collection.createIndex({ "txid" : 1 }, { unique: true });
    collection.createIndex({ "uid": 1, "addr": 1, "detTs": -1 });
})();
