package com.gimlee.mediastore.service

import com.gimlee.mediastore.config.StorageProperties
import com.gimlee.mediastore.exception.MediaRetrievalException
import com.gimlee.mediastore.exception.MediaUploadException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

@Service
@ConditionalOnProperty(name = ["gimlee.media.store.storage-type"], havingValue = "S3")
class S3StorageService(
    private val s3Client: S3Client,
    private val storageProperties: StorageProperties
) : StorageService {

    override fun upload(inputStream: InputStream, contentLength: Long, contentType: String, destinationPath: String) {
        try {
            val request = PutObjectRequest.builder()
                .bucket(storageProperties.s3.bucket)
                .key(destinationPath)
                .contentType(contentType)
                .build()
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength))
        } catch (e: Exception) {
            throw MediaUploadException("Failed to upload to S3", e)
        }
    }

    override fun download(path: String): InputStreamResource {
        try {
            val request = GetObjectRequest.builder()
                .bucket(storageProperties.s3.bucket)
                .key(path.removePrefix("/"))
                .build()
            return InputStreamResource(s3Client.getObject(request))
        } catch (e: Exception) {
            throw MediaRetrievalException("Failed to retrieve from S3 for path: $path", e)
        }
    }
}