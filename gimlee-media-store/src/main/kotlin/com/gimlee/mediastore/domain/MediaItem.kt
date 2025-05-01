package com.gimlee.mediastore.domain

import java.time.Instant

data class MediaItem(
    val id: String? = null,
    val filename: String,
    val extension: String,
    val dateTime: Instant,
    val path: String,
    val xsThumbPath: String? = null,
    val smThumbPath: String? = null,
    val mdThumbPath: String? = null
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_FILENAME = "f"
        const val FIELD_EXTENSION = "e"
        const val FIELD_TIMESTAMP = "ts"
        const val FIELD_PATH = "p"
        const val FIELD_XS_THUMB_PATH = "xs_p"
        const val FIELD_SM_THUMB_PATH = "sm_p"
        const val FIELD_MD_THUMB_PATH = "md_p"
    }
}