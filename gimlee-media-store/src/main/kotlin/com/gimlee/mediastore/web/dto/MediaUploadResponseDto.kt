package com.gimlee.mediastore.media.web.dto

import com.gimlee.mediastore.domain.MediaItem
import java.time.Instant

data class MediaUploadResponseDto(
    val id: String,
    val filename: String,
    val extension: String,
    val dateTime: Instant,
    val path: String,
    val xsThumbPath: String,
    val smThumbPath: String,
    val mdThumbPath: String
) {
    companion object {
        fun fromMediaItem(mediaItem: MediaItem) = MediaUploadResponseDto(
            mediaItem.id!!,
            mediaItem.filename,
            mediaItem.extension,
            mediaItem.dateTime,
            mediaItem.path,
            mediaItem.xsThumbPath ?: "",
            mediaItem.smThumbPath ?: "",
            mediaItem.mdThumbPath ?: ""
        )
    }
}