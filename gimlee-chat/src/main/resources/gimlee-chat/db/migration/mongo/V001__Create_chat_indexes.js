// Flyway migration script
// Version: 1
// Description: Create indexes for the gimlee-chat-messages collection

const collectionName = "gimlee-chat-messages";
const collection = db.getCollection(collectionName);

// Index for fetching messages by chatId and timestamp (descending for latest)
collection.createIndex({ "cid" : 1, "ts" : -1 });

// Index for paging by chatId and _id
collection.createIndex({ "cid" : 1, "_id" : -1 });
