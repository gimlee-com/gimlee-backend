package com.gimlee.mediastore.web

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.mediastore.media.web.dto.MediaUploadResponseDto
import com.gimlee.mediastore.MediaStoreTestApplication
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@SpringBootTest(
    classes = [MediaStoreTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MediaControllerIntegrationTest(
    private val restTemplate: TestRestTemplate
) : BaseIntegrationTest({

    Given("the media upload endpoint") {
        val url = "/media/upload"

        When("uploading multiple files via multipart/form-data") {
            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            // We need some real bytes for ImageIO to read it as an image
            // I'll use a small 1x1 pixel JPEG if possible, or just a dummy file if PictureUploadService allows it.
            // Actually PictureUploadService uses ImageIO.read(fileInputStream), so it MUST be a valid image.
            
            val imageResource = ClassPathResource("test-image.jpg")
            body.add("files[]", imageResource)

            val requestEntity = HttpEntity(body, headers)
            val response = restTemplate.postForEntity(url, requestEntity, Array<MediaUploadResponseDto>::class.java)

            Then("it should return 200 OK and the list of uploaded media") {
                response.statusCode.value() shouldBe 200
                val bodyResponse = response.body!!
                bodyResponse shouldHaveSize 1
                bodyResponse[0].path shouldNotBe null
            }
        }

        When("uploading a single file as raw request body with image/jpeg") {
            val headers = HttpHeaders()
            headers.contentType = MediaType.IMAGE_JPEG

            val imageResource = ClassPathResource("test-image.jpg")
            val bytes = imageResource.inputStream.readAllBytes()

            val requestEntity = HttpEntity(bytes, headers)
            val response = restTemplate.postForEntity("$url/single", requestEntity, MediaUploadResponseDto::class.java)

            Then("it should return 200 OK") {
                response.statusCode.value() shouldBe 200
            }
        }

        When("uploading a single file as raw request body with image/jpg") {
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType("image/jpg")

            val imageResource = ClassPathResource("test-image.jpg")
            val bytes = imageResource.inputStream.readAllBytes()

            val requestEntity = HttpEntity(bytes, headers)
            val response = restTemplate.postForEntity("$url/single", requestEntity, MediaUploadResponseDto::class.java)

            Then("it should return 200 OK") {
                response.statusCode.value() shouldBe 200
            }
        }

        When("uploading a single file as raw request body with image/png") {
            val headers = HttpHeaders()
            headers.contentType = MediaType.IMAGE_PNG

            val imageResource = ClassPathResource("test-image.jpg") // It's a jpg but we'll send it as png for testing the mapping
            val bytes = imageResource.inputStream.readAllBytes()

            val requestEntity = HttpEntity(bytes, headers)
            val response = restTemplate.postForEntity("$url/single", requestEntity, MediaUploadResponseDto::class.java)

            Then("it should return 200 OK") {
                response.statusCode.value() shouldBe 200
            }
        }
    }
})
