package com.gimlee.mediastore.web

import org.apache.logging.log4j.LogManager
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import com.gimlee.mediastore.service.MediaService
import com.gimlee.mediastore.service.PictureUploadService
import com.gimlee.mediastore.media.web.dto.MediaUploadResponseDto
import java.io.FileNotFoundException
import jakarta.activation.MimetypesFileTypeMap
import jakarta.servlet.http.HttpServletResponse

@RestController
class MediaController(
    private val pictureUploadService: PictureUploadService,
    private val mediaService: MediaService
) {
    companion object {
        private val mimetypeFileTypeMap = MimetypesFileTypeMap()
        private val log = LogManager.getLogger()
    }

    @ExceptionHandler(FileNotFoundException::class)
    fun handleFileNotFound(e: FileNotFoundException, response: HttpServletResponse) {
        log.info("File not found. ${e.message}")
        response.status = HttpStatus.NOT_FOUND.value()
    }

    @PostMapping("/media/upload")
    fun upload(
        @RequestParam("files[]") file: Array<MultipartFile>
    ) = MediaUploadResponseDto.fromMediaItem(pictureUploadService.uploadAndCreateThumbs(file.first().inputStream))

    @GetMapping("/media")
    fun getMediaFile(
        @RequestParam(name = "p") filePath: String,
        response: HttpServletResponse
    ): InputStreamResource? {
        response.contentType = mimetypeFileTypeMap.getContentType(filePath)

        return mediaService.getItem(filePath)
    }
}