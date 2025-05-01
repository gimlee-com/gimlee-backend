package com.gimlee.mediastore.service

import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.resizers.configurations.AlphaInterpolation
import net.coobird.thumbnailator.resizers.configurations.Antialiasing
import net.coobird.thumbnailator.resizers.configurations.Dithering
import org.springframework.data.util.Pair
import org.springframework.stereotype.Service
import com.gimlee.common.UUIDv7
import com.gimlee.mediastore.config.MediaStoreConfig
import com.gimlee.mediastore.persistence.MediaItemRepository
import com.gimlee.mediastore.domain.MediaItem
import com.gimlee.mediastore.exception.MediaUploadException
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

@Service
class PictureUploadService(
    private val mediaItemRepository: MediaItemRepository,
    private val mediaStoreConfig: MediaStoreConfig
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

    @Throws(IOException::class)
    private fun saveImage(fileName: String, extension: String?, fileInputStream: InputStream) {

        val mediaStoreDirectory = mediaStoreConfig.mediaStoreDirectory
        val largeFile = File("$mediaStoreDirectory/", "$fileName.$extension")
        val thumbnailXSFile = File(mediaStoreDirectory + mediaStoreConfig.xsThumbsPath, "$fileName.$extension")
        val thumbnailSMFile = File(mediaStoreDirectory + mediaStoreConfig.smThumbsPath, "$fileName.$extension")
        val thumbnailMDFile = File(mediaStoreDirectory + mediaStoreConfig.mdThumbsPath, "$fileName.$extension")

        if (!largeFile.exists()) {
            largeFile.parentFile.mkdirs()
            thumbnailXSFile.parentFile.mkdirs()
            thumbnailSMFile.parentFile.mkdirs()
            thumbnailMDFile.parentFile.mkdirs()
        }
        val bufferedImage = ImageIO.read(fileInputStream)
        fileInputStream.close()

        val targetLargeImageWidth =
            if (bufferedImage.width < mediaStoreConfig.pictureSizeLg) {
                bufferedImage.width
            } else {
                mediaStoreConfig.pictureSizeLg
            }

        Thumbnails.of(bufferedImage)
            .size(targetLargeImageWidth.toInt(), (targetLargeImageWidth * 1.5).toInt())
            .outputFormat("jpg")
            .outputQuality(mediaStoreConfig.jpegQuality)
            .toFile(largeFile.path)
        saveThumbnail(bufferedImage, largeFile.path, targetLargeImageWidth)
        saveThumbnail(bufferedImage, thumbnailXSFile.path, mediaStoreConfig.pictureSizeXs)
        saveThumbnail(bufferedImage, thumbnailSMFile.path, mediaStoreConfig.pictureSizeSm)
        saveThumbnail(bufferedImage, thumbnailMDFile.path, mediaStoreConfig.pictureSizeMd)
    }

    @Throws(IOException::class)
    private fun saveThumbnail(bufferedImage: BufferedImage, path: String, size: Int) {
        val imageAspectRatio = bufferedImage.width.toDouble() / bufferedImage.height.toDouble()
        val dimensions = calculateThumbDimensions(size, imageAspectRatio)
        Thumbnails.of(bufferedImage)
            .antialiasing(Antialiasing.OFF)
            .dithering(Dithering.DISABLE)
            .alphaInterpolation(AlphaInterpolation.QUALITY)
            .size(dimensions.first.toInt(), dimensions.second.toInt())
            .outputQuality(mediaStoreConfig.jpegQuality)
            .toFile(path)

    }

    private fun calculateThumbDimensions(size: Int, aspectRatio: Double): Pair<Int, Int> {
        return if (aspectRatio < 1.0) {
            Pair.of((size / aspectRatio).toInt(), size)
        } else Pair.of(size, (size * aspectRatio).toInt())
    }
}