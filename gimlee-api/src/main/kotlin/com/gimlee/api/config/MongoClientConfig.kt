package com.gimlee.api.config

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MongoClientConfig (
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
) {
    @Bean
    fun mongoDatabase(): MongoDatabase = MongoClients.create(mongoUri).getDatabase("gimlee")
}