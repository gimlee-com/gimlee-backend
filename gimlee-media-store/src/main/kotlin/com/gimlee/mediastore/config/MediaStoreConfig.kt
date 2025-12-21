package com.gimlee.mediastore.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
data class MediaStoreConfig(
    @Value("\${gimlee.media.store.picture.thumbs-xs-path:/thumbs-xs/}") val xsThumbsPath: String,
    @Value("\${gimlee.media.store.picture.thumbs-sm-path:/thumbs-sm/}") val smThumbsPath: String,
    @Value("\${gimlee.media.store.picture.thumbs-md-path:/thumbs-md/}") val mdThumbsPath: String,
    @Value("\${gimlee.media.store.picture.size-xs:160}") val pictureSizeXs: Int,
    @Value("\${gimlee.media.store.picture.size-sm:270}") val pictureSizeSm: Int,
    @Value("\${gimlee.media.store.picture.size-md:570}") val pictureSizeMd: Int,
    @Value("\${gimlee.media.store.picture.size-lg:1600}") val pictureSizeLg: Int,
    @Value("\${gimlee.media.store.picture.jpeg-quality:0.8}") val jpegQuality: Double
)