package com.gimlee.mediastore.service

import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service

@Service
class MediaService(private val storageService: StorageService) {

    fun getItem(path: String): InputStreamResource {
        return storageService.download(path)
    }
}
