package com.gimlee.mediastore.service

import java.awt.image.BufferedImage

/**
 * Abstraction for image compression and resizing.
 *
 * Both methods preserve aspect ratio, fitting the result within the specified
 * bounding box without upscaling beyond the original dimensions.
 * The output is JPEG-encoded bytes.
 */
interface ImageCompressor {

    fun resize(image: BufferedImage, maxWidth: Int, maxHeight: Int): ByteArray

    fun createThumbnail(image: BufferedImage, maxWidth: Int, maxHeight: Int): ByteArray
}
