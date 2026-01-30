package com.gimlee.mediastore.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.logging.log4j.LogManager
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import com.gimlee.mediastore.service.MediaService
import com.gimlee.mediastore.service.PictureUploadService
import com.gimlee.mediastore.media.web.dto.MediaUploadResponseDto
import com.gimlee.mediastore.domain.MediaStoreOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import java.io.FileNotFoundException
import jakarta.activation.MimetypesFileTypeMap
import jakarta.servlet.http.HttpServletResponse

@Tag(name = "Media", description = "Endpoints for uploading and retrieving media files")
@RestController
class MediaController(
    private val pictureUploadService: PictureUploadService,
    private val mediaService: MediaService,
    private val messageSource: MessageSource
) {
    companion object {
        private val mimetypeFileTypeMap = MimetypesFileTypeMap()
        private val log = LogManager.getLogger()
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @ExceptionHandler(FileNotFoundException::class)
    fun handleFileNotFound(e: FileNotFoundException): ResponseEntity<Any> {
        log.info("File not found. ${e.message}")
        return handleOutcome(MediaStoreOutcome.FILE_NOT_FOUND)
    }

    @Operation(
        summary = "Upload Media",
        description = "Uploads images to the media store. The system generates medium and small thumbnails automatically."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Media uploaded successfully",
        content = [Content(schema = Schema(implementation = MediaUploadResponseDto::class))]
    )
    @PostMapping("/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @Parameter(description = "The files to upload")
        @RequestParam("files[]") files: Array<MultipartFile>
    ): List<MediaUploadResponseDto> {
        return files.map { file ->
            MediaUploadResponseDto.fromMediaItem(pictureUploadService.uploadAndCreateThumbs(file.inputStream))
        }
    }

    @Operation(
        summary = "Get Media File",
        description = "Retrieves a media file (original or thumbnail) from the store."
    )
    @ApiResponse(responseCode = "200", description = "The media file")
    @ApiResponse(
        responseCode = "404",
        description = "File not found. Possible status codes: MEDIA_FILE_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/media")
    fun getMediaFile(
        @Parameter(description = "Path to the media file")
        @RequestParam(name = "p") filePath: String,
        response: HttpServletResponse
    ): InputStreamResource? {
        response.contentType = mimetypeFileTypeMap.getContentType(filePath)

        return mediaService.getItem(filePath)
    }
}