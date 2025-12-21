package com.gimlee.mediastore.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfiguration {

    @Configuration
    @ConditionalOnProperty(name = ["gimlee.media.store.storage-type"], havingValue = "S3")
    class S3Configuration(private val storageProperties: StorageProperties) {
        @Bean
        fun s3Client(): S3Client {
            val s3Props = storageProperties.s3
            val credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3Props.accessKey, s3Props.secretKey)
            )

            val clientBuilder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(s3Props.region))

            s3Props.endpoint?.let {
                clientBuilder.endpointOverride(it)
            }

            return clientBuilder.build()
        }
    }
}