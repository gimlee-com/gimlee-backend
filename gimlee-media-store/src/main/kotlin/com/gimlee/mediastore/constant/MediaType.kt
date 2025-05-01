package com.gimlee.mediastore.constant

enum class MediaType {
    PICTURE,
    VIDEO,
    DOCUMENT;

    companion object {
        fun getMediaType(type: String): MediaType {
            return when (type) {
                "picture" -> PICTURE
                "video" -> VIDEO
                "document" -> DOCUMENT
                else -> DOCUMENT
            }
        }
    }
}
