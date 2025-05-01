package com.gimlee.mediastore.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service
import com.gimlee.mediastore.exception.MediaRetrievalException
import java.io.IOException

@Service
class MediaService {

    @Value("\${gimlee.media.store.directory}")
    private val mediaStoreDirectory: String? = null

    fun getItem(path: String): InputStreamResource {
        val file = FileSystemResource(mediaStoreDirectory!! + path)
        try {
            return InputStreamResource(file.inputStream)
        } catch (e: IOException) {
            throw MediaRetrievalException("Could not retrieve item", e)
        }

    }
}
