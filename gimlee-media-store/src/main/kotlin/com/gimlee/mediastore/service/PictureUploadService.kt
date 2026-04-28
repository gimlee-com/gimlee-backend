package com.gimlee.mediastore.service

import com.gimlee.common.UUIDv7
import com.gimlee.mediastore.config.MediaStoreConfig
import com.gimlee.mediastore.domain.MediaItem
import com.gimlee.mediastore.exception.MediaUploadException
import com.gimlee.mediastore.persistence.MediaItemRepository
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

@Service
class PictureUploadService(
    private val mediaItemRepository: MediaItemRepository,
    private val mediaStoreConfig: MediaStoreConfig,
    private val storageService: StorageService,
    private val imageCompressor: ImageCompressor
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

        val targetLargeImageWidth = if (bufferedImage.width < mediaStoreConfig.pictureSizeLg) {
            bufferedImage.width
        } else {
            mediaStoreConfig.pictureSizeLg
        }
        val largeImageBytes = imageCompressor.resize(
            bufferedImage,
            targetLargeImageWidth,
            (targetLargeImageWidth * 1.5).toInt()
        )
        uploadBytes(largeImageBytes, "/$fullFileName")

        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeXs, mediaStoreConfig.xsThumbsPath)
        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeSm, mediaStoreConfig.smThumbsPath)
        uploadThumbnail(bufferedImage, fullFileName, mediaStoreConfig.pictureSizeMd, mediaStoreConfig.mdThumbsPath)
    }

    private fun uploadThumbnail(image: BufferedImage, fileName: String, size: Int, path: String) {
        val imageAspectRatio = image.width.toDouble() / image.height.toDouble()
        val (width, height) = calculateThumbDimensions(size, imageAspectRatio)

        val thumbnailBytes = imageCompressor.createThumbnail(image, width, height)
        uploadBytes(thumbnailBytes, "$path$fileName")
    }

    private fun uploadBytes(bytes: ByteArray, destinationPath: String) {
        val inputStream = ByteArrayInputStream(bytes)
        storageService.upload(
            inputStream,
            bytes.size.toLong(),
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