package com.gimlee.common.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.bson.UuidRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

@Configuration
class MongoClientConfig (
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
) {
    @Bean
    fun mongoDatabaseFactory(): MongoDatabaseFactory {
        val connectionString = ConnectionString(mongoUri)
        val databaseName = connectionString.database ?: "gimlee"
        val settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build()
        return SimpleMongoClientDatabaseFactory(MongoClients.create(settings), databaseName)
    }

    @Bean
    fun mongoDatabase(mongoDatabaseFactory: MongoDatabaseFactory): MongoDatabase {
        return mongoDatabaseFactory.mongoDatabase
    }
}