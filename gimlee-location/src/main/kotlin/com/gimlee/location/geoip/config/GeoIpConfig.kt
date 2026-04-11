package com.gimlee.location.geoip.config

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.GZIPInputStream

@Configuration
@EnableConfigurationProperties(GeoIpProperties::class)
class GeoIpConfig(private val properties: GeoIpProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun geoIpDatabaseReader(): DatabaseReader? {
        if (!properties.enabled) {
            log.info("GeoIP is disabled via configuration")
            return null
        }

        return try {
            val inputStream = resolveDatabaseStream()
            if (inputStream != null) {
                val reader = DatabaseReader.Builder(inputStream)
                    .withCache(CHMCache())
                    .build()
                log.info("GeoIP database loaded successfully (type: {})", reader.metadata().databaseType())
                reader
            } else {
                log.warn("GeoIP database not available — feature disabled")
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to initialize GeoIP database — feature disabled: {}", e.message)
            null
        }
    }

    private fun resolveDatabaseStream(): InputStream? {
        if (!properties.licenseKey.isNullOrBlank()) {
            return downloadDatabase()
        }
        if (!properties.databasePath.isNullOrBlank()) {
            return loadLocalDatabase(properties.databasePath)
        }
        return loadClasspathDatabase()
    }

    private fun downloadDatabase(): InputStream? {
        val uri = URI("${properties.downloadUrl}?edition_id=${properties.editionId}" +
                "&license_key=${properties.licenseKey}&suffix=tar.gz")

        log.info("Downloading GeoIP database ({})...", properties.editionId)

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(properties.readTimeoutMs))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            log.warn("GeoIP database download failed with status {}", response.statusCode())
            return null
        }

        return extractMmdbFromTarGz(response.body())
    }

    private fun extractMmdbFromTarGz(inputStream: InputStream): InputStream? {
        val gzipStream = GZIPInputStream(BufferedInputStream(inputStream))
        val tarStream = BufferedInputStream(gzipStream)

        // TAR format: 512-byte header blocks followed by file data
        val headerBuffer = ByteArray(512)
        while (true) {
            val bytesRead = tarStream.readNBytes(headerBuffer, 0, 512)
            if (bytesRead < 512) break

            if (headerBuffer.all { it == 0.toByte() }) break

            val fileName = String(headerBuffer, 0, 100).trimEnd('\u0000', ' ')
            val sizeOctal = String(headerBuffer, 124, 12).trimEnd('\u0000', ' ')
            val fileSize = if (sizeOctal.isNotBlank()) sizeOctal.toLong(8) else 0L

            if (fileName.endsWith(".mmdb")) {
                log.info("Extracting {} ({} bytes) from archive", fileName, fileSize)
                val mmdbBytes = tarStream.readNBytes(fileSize.toInt())
                return mmdbBytes.inputStream()
            }

            // Skip file content (aligned to 512-byte blocks)
            val blocksToSkip = (fileSize + 511) / 512 * 512
            tarStream.skipNBytes(blocksToSkip)
        }

        log.warn("No .mmdb file found in the downloaded archive")
        return null
    }

    private fun loadLocalDatabase(path: String): InputStream? {
        val file = Path.of(path)
        if (!Files.exists(file)) {
            log.warn("GeoIP database file not found at: {}", path)
            return null
        }
        log.info("Loading GeoIP database from local file: {}", path)
        return Files.newInputStream(file)
    }

    private fun loadClasspathDatabase(): InputStream? {
        val resource = properties.classpathDatabase
        val stream = javaClass.classLoader.getResourceAsStream(resource)
        if (stream == null) {
            log.warn("GeoIP bundled database not found on classpath: {}", resource)
            return null
        }
        log.info("Loading GeoIP database from bundled classpath resource: {}", resource)
        return stream
    }
}
