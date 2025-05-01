package com.gimlee.api.playground.media.data

import org.springframework.context.annotation.Lazy
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import com.gimlee.mediastore.domain.MediaItem
import com.gimlee.mediastore.service.PictureUploadService

@Lazy(true)
@Component
class MediaPopulator(
    private val pictureUploadService: PictureUploadService
) {
    fun populateMedia(): List<MediaItem> {
        val resolver = PathMatchingResourcePatternResolver(this.javaClass.classLoader)
        val resources = resolver.getResources("classpath*:/playground/images/*.jpg")
        return resources.map { mediaResource ->
            pictureUploadService.uploadAndCreateThumbs(mediaResource.inputStream)
        }
    }
}