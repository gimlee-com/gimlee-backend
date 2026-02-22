// Flyway migration script
// Version: 8
// Description: Create partial indexes for ad price filtering by currency
// Rationale: Support range filtering on price per currency without a massive compound index.
// Note: When adding a new Currency to the enum, a corresponding index MUST be added here or in a new migration.

const collectionName = "gimlee-ads-advertisements";
const collection = db.getCollection(collectionName);

// List of all supported currencies (from Currency enum)
// ARRR, YEC, USDT, USD, PLN, XAU
const currencies = ["ARRR", "YEC", "USDT", "USD", "PLN", "XAU"];

currencies.forEach(currency => {
    // Create partial index on 'p' (FIELD_PRICE) where 'c' (FIELD_CURRENCY) matches
    // This supports queries like: { c: "USD", p: { $gte: 10, $lte: 20 } }
    // Naming convention: idx_p_partial_{currency}
    collection.createIndex(
        { "p": 1 },
        { 
            name: "idx_p_partial_" + currency.toLowerCase(),
            partialFilterExpression: { "c": currency } 
        }
    );
});
