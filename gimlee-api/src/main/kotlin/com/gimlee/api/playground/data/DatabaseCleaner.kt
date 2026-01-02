package com.gimlee.api.playground.data

import com.mongodb.client.MongoDatabase
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("local", "dev", "test")
@Component
class DatabaseCleaner(
    private val mongoDatabase: MongoDatabase
) {
    private val log = LoggerFactory.getLogger(DatabaseCleaner::class.java)

    fun clearAll() {
        log.info("Clearing all collections in the database...")
        mongoDatabase.listCollectionNames().forEach { collectionName ->
            if (shouldClear(collectionName)) {
                log.debug("Clearing collection: $collectionName")
                mongoDatabase.getCollection(collectionName).deleteMany(Document())
            } else {
                log.debug("Skipping collection: $collectionName")
            }
        }
        log.info("Database cleared successfully.")
    }

    private fun shouldClear(collectionName: String): Boolean {
        return !collectionName.startsWith("system.") && 
               !collectionName.contains("flyway_schema_history")
    }
}
