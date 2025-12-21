package com.gimlee.mediastore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "gimlee.media.store")
data class StorageProperties(
    val storageType: StorageType = StorageType.LOCAL,
    val local: Local = Local(),
    val s3: S3 = S3()
) {
    data class Local(
        val directory: String = "/tmp/media"
    )

    data class S3(
        val endpoint: URI? = null,
        val region: String = "eu-central-1",
        val accessKey: String = "",
        val secretKey: String = "",
        val bucket: String = "gimlee-media"
    )
}