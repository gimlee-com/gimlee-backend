package com.gimlee.common.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.bson.UuidRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MongoClientConfig (
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
) {
    @Bean
    fun mongoDatabase(): MongoDatabase {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(mongoUri))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build()
        return MongoClients.create(settings).getDatabase("gimlee")
    }
}