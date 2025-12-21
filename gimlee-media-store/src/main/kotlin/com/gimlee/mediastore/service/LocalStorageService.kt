package com.gimlee.mediastore.service

import com.gimlee.mediastore.config.StorageProperties
import com.gimlee.mediastore.exception.MediaRetrievalException
import com.gimlee.mediastore.exception.MediaUploadException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
@ConditionalOnProperty(name = ["gimlee.media.store.storage-type"], havingValue = "LOCAL", matchIfMissing = true)
class LocalStorageService(
    storageProperties: StorageProperties
) : StorageService {

    private val rootLocation = Paths.get(storageProperties.local.directory)

    init {
        if (Files.notExists(rootLocation)) {
            Files.createDirectories(rootLocation)
        }
    }

    override fun upload(inputStream: InputStream, contentLength: Long, contentType: String, destinationPath: String) {
        try {
            val destinationFile = this.rootLocation.resolve(destinationPath).normalize()
            Files.createDirectories(destinationFile.parent)
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            throw MediaUploadException("Failed to store file $destinationPath", e)
        }
    }

    override fun download(path: String): InputStreamResource {
        try {
            val file = rootLocation.resolve(path.removePrefix("/")).normalize()
            val resource = FileSystemResource(file)
            if (resource.exists() || resource.isReadable) {
                return InputStreamResource(resource.inputStream)
            } else {
                throw MediaRetrievalException("Could not read file: $path", Exception())
            }
        } catch (e: Exception) {
            throw MediaRetrievalException("Could not retrieve file: $path", e)
        }
    }
}