package com.gimlee.mediastore.service

import org.springframework.core.io.InputStreamResource
import java.io.InputStream

interface StorageService {
    fun upload(inputStream: InputStream, contentLength: Long, contentType: String, destinationPath: String)
    fun download(path: String): InputStreamResource
}