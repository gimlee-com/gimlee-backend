package com.gimlee.mediastore.service

import com.gimlee.common.UUIDv7
import com.gimlee.mediastore.config.MediaStoreConfig
import com.gimlee.mediastore.domain.MediaItem
import com.gimlee.mediastore.exception.MediaUploadException
import com.gimlee.mediastore.persistence.MediaItemRepository
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.resizers.configurations.AlphaInterpolation
import net.coobird.thumbnailator.resizers.configurations.Antialiasing
import net.coobird.thumbnailator.resizers.configurations.Dithering
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

@Service
class PictureUploadService(
    private val mediaItemRepository: MediaItemRepository,
    private val mediaStoreConfig: MediaStoreConfig,
    private val storageService: StorageService
) {

    fun uploadAndCreateThumbs(fileInputStream: InputStream): MediaItem {
        val extension = "jpg"
        var generatedFilename = UUIDv7.generate().toString()

        while (mediaItemRepository.findByName(generatedFilename) != null) {
            generatedFilename = UUIDv7.generate().toString()
        }

        try {
            saveImage(generatedFilename, extension, fileInputStream)
            val picture = MediaItem(
                filename = generatedFilename,
                extension = extension,
                dateTime = Instant.now(),
                path = "/$generatedFilename.$extension",
                xsThumbPath = "${mediaStoreConfig.xsThumbsPath}$generatedFilename.$extension",
                smThumbPath = "${mediaStoreConfig.smThumbsPath}$generatedFilename.$extension",
                mdThumbPath = "${mediaStoreConfig.mdThumbsPath}$generatedFilename.$extension"
            )
            return mediaItemRepository.save(picture)
        } catch (e: IOException) {
            throw MediaUploadException("Failed to upload file.", e)
        }

    }

    private fun saveImage(fileName: String, extension: String, fileInputStream: InputStream) {
        val bufferedImage = ImageIO.read(fileInputStream)
        fileInputStream.close()

        val fullFileName = "$fileName.$extension"

        // Generate and upload large image
        val targetLargeImageWidth = if (bufferedImage.width < mediaStoreConfig.pictureSizeLg) {
            bufferedImage.width
        } else {
            mediaStoreConfig.pictureSizeLg
        }
        val largeImage = Thumbnails.of(bufferedImage)
            .size(targetLargeImageWidth, (targetLargeImageWidth * 1.5).toInt())
            .outputFormat("jpg")
            .outputQuality(mediaStoreConfig.jpegQuality)
            .asBufferedImage()
        uploadImage(largeImage, "/$fullFileName")

        // Generate and upload thumbnails
        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeXs, mediaStoreConfig.xsThumbsPath)
        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeSm, mediaStoreConfig.smThumbsPath)
        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeMd, mediaStoreConfig.mdThumbsPath)
    }

    private fun uploadThumbnail(image: BufferedImage, fileName: String, size: Int, path: String) {
        val imageAspectRatio = image.width.toDouble() / image.height.toDouble()
        val (width, height) = calculateThumbDimensions(size, imageAspectRatio)

        val thumbnailImage = Thumbnails.of(image)
            .antialiasing(Antialiasing.OFF)
            .dithering(Dithering.DISABLE)
            .alphaInterpolation(AlphaInterpolation.QUALITY)
            .size(width, height)
            .outputQuality(mediaStoreConfig.jpegQuality)
            .asBufferedImage()

        uploadImage(thumbnailImage, "$path$fileName")
    }

    private fun uploadImage(image: BufferedImage, destinationPath: String) {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", outputStream)
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        storageService.upload(
            inputStream,
            outputStream.size().toLong(),
            "image/jpeg",
            destinationPath.removePrefix("/")
        )
    }

    private fun calculateThumbDimensions(size: Int, aspectRatio: Double): Pair<Int, Int> {
        return if (aspectRatio < 1.0) {
            (size / aspectRatio).toInt() to size
        } else size to (size * aspectRatio).toInt()
    }
}