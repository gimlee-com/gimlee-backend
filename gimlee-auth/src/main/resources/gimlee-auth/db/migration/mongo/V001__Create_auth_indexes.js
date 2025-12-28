// Flyway migration script
// Version: 1
// Description: Create indexes for auth collections

// --- gimlee-userRoles ---
(function() {
    const collectionName = "gimlee-userRoles";
    const collection = db.getCollection(collectionName);

    collection.createIndex({ "userId": 1, "role": 1 }, { unique: true });
})();

// --- gimlee-users ---
(function() {
    const collectionName = "gimlee-users";
    const collection = db.getCollection(collectionName);

    collection.createIndex({ "username" : 1 }, { unique: true });
    collection.createIndex({ "email" : 1 }, { unique: true });
})();

// --- gimlee-userVerificationCodes ---
(function() {
    const collectionName = "gimlee-userVerificationCodes";
    const collection = db.getCollection(collectionName);

    // userId is covered by this compound index as it's the prefix.
    collection.createIndex({ "userId": 1, "verificationCode": 1, "issuedAt": 1 });
})();
