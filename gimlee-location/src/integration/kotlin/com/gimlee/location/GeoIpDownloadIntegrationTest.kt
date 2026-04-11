package com.gimlee.location

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.web.dto.StatusResponseDto
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class GeoIpDownloadIntegrationTest : BaseIntegrationTest({

    Given("the GeoIP endpoint with a downloaded database via WireMock") {

        When("requesting country detection with a known US IP") {
            val response = restClient.get(
                "/location/geoip/country",
                headers = mapOf("X-Forwarded-For" to "8.8.8.8")
            )

            Then("it should return the US country code from the downloaded database") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<StatusResponseDto>()!!
                body.success shouldBe true
                body.status shouldBe "LOCATION_GEOIP_COUNTRY_DETECTED"
                @Suppress("UNCHECKED_CAST")
                val data = body.data as Map<String, Any>
                data shouldContainKey "countryCode"
                data["countryCode"] shouldBe "US"
            }
        }

        When("requesting country detection with a known Polish IP") {
            val response = restClient.get(
                "/location/geoip/country",
                headers = mapOf("X-Forwarded-For" to "212.77.100.101")
            )

            Then("it should return the PL country code from the downloaded database") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<StatusResponseDto>()!!
                body.success shouldBe true
                body.status shouldBe "LOCATION_GEOIP_COUNTRY_DETECTED"
                @Suppress("UNCHECKED_CAST")
                val data = body.data as Map<String, Any>
                data["countryCode"] shouldBe "PL"
            }
        }
    }
}) {
    companion object {

        @JvmStatic
        @DynamicPropertySource
        fun configureDownload(registry: DynamicPropertyRegistry) {
            val mmdbBytes = requireNotNull(
                GeoIpDownloadIntegrationTest::class.java.classLoader
                    .getResourceAsStream("geoip/2026-04-11/GeoLite2-Country.mmdb")
            ) { "GeoLite2-Country.mmdb not found on classpath" }.use { it.readAllBytes() }
            val tarGzBytes = createTarGz(
                "GeoLite2-Country_20260411/README.txt" to "GeoLite2 Country Database".toByteArray(),
                "GeoLite2-Country_20260411/GeoLite2-Country.mmdb" to mmdbBytes
            )

            wireMockServer.stubFor(
                get(urlPathEqualTo("/geoip_download"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/gzip")
                            .withBody(tarGzBytes)
                    )
            )

            registry.add("gimlee.location.geoip.enabled") { "true" }
            registry.add("gimlee.location.geoip.license-key") { "test-license-key" }
            registry.add("gimlee.location.geoip.download-url") {
                "http://localhost:${wireMockServer.port()}/geoip_download"
            }
            registry.add("gimlee.location.geoip.connect-timeout-ms") { "2000" }
            registry.add("gimlee.location.geoip.read-timeout-ms") { "5000" }
        }

        private fun createTarGz(vararg entries: Pair<String, ByteArray>): ByteArray {
            val tarBytes = ByteArrayOutputStream()

            for ((name, data) in entries) {
                val header = ByteArray(512)

                // File name (offset 0, 100 bytes)
                val nameBytes = name.toByteArray(Charsets.US_ASCII)
                nameBytes.copyInto(header, 0, 0, minOf(nameBytes.size, 100))

                // File mode (offset 100, 8 bytes) — regular file
                "0000644\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 100)

                // Owner UID (offset 108, 8 bytes)
                "0001000\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 108)

                // Group GID (offset 116, 8 bytes)
                "0001000\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 116)

                // File size in octal (offset 124, 12 bytes)
                val sizeOctal = data.size.toString(8).padStart(11, '0') + "\u0000"
                sizeOctal.toByteArray(Charsets.US_ASCII).copyInto(header, 124)

                // Modification time (offset 136, 12 bytes)
                val mtime = (System.currentTimeMillis() / 1000).toString(8).padStart(11, '0') + "\u0000"
                mtime.toByteArray(Charsets.US_ASCII).copyInto(header, 136)

                // Type flag (offset 156) — '0' for regular file
                header[156] = '0'.code.toByte()

                // Checksum placeholder (offset 148, 8 bytes) — fill with spaces for calculation
                for (i in 148..155) header[i] = ' '.code.toByte()
                val checksum = header.sumOf { it.toInt() and 0xFF }
                val checksumOctal = checksum.toString(8).padStart(6, '0') + "\u0000 "
                checksumOctal.toByteArray(Charsets.US_ASCII).copyInto(header, 148)

                tarBytes.write(header)
                tarBytes.write(data)

                // Pad to 512-byte boundary
                val padding = (512 - (data.size % 512)) % 512
                if (padding > 0) tarBytes.write(ByteArray(padding))
            }

            // Two 512-byte zero blocks to mark end of archive
            tarBytes.write(ByteArray(1024))

            val gzipBytes = ByteArrayOutputStream()
            GZIPOutputStream(gzipBytes).use { it.write(tarBytes.toByteArray()) }
            return gzipBytes.toByteArray()
        }
    }
}
