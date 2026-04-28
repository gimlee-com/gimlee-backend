package com.gimlee.mediastore.service

import com.gimlee.mediastore.config.MediaStoreConfig
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.resizers.configurations.AlphaInterpolation
import net.coobird.thumbnailator.resizers.configurations.Antialiasing
import net.coobird.thumbnailator.resizers.configurations.Dithering
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

@Service
class ThumbnailatorImageCompressor(
    private val mediaStoreConfig: MediaStoreConfig
) : ImageCompressor {

    override fun resize(image: BufferedImage, maxWidth: Int, maxHeight: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        Thumbnails.of(image)
            .size(maxWidth, maxHeight)
            .outputFormat("jpg")
            .outputQuality(mediaStoreConfig.jpegQuality)
            .toOutputStream(outputStream)
        return outputStream.toByteArray()
    }

    override fun createThumbnail(image: BufferedImage, maxWidth: Int, maxHeight: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        Thumbnails.of(image)
            .antialiasing(Antialiasing.OFF)
            .dithering(Dithering.DISABLE)
            .alphaInterpolation(AlphaInterpolation.QUALITY)
            .size(maxWidth, maxHeight)
            .outputFormat("jpg")
            .outputQuality(mediaStoreConfig.jpegQuality)
            .toOutputStream(outputStream)
        return outputStream.toByteArray()
    }
}
