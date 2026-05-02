package com.gimlee.location.cities.geonames.download

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

@Component
class GeoNamesDownloader(private val properties: GeoNamesProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Downloads a plain (non-ZIP) file from GeoNames.
     */
    fun downloadPlainFile(fileName: String, targetDir: Path): Path {
        val url = "${properties.baseUrl}/$fileName"
        log.info("Downloading GeoNames plain file: {}", url)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(properties.downloadTimeout)
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw GeoNamesDownloadException(
                "Failed to download $url — HTTP ${response.statusCode()}"
            )
        }

        Files.createDirectories(targetDir)
        val targetFile = targetDir.resolve(fileName)
        Files.copy(response.body(), targetFile, StandardCopyOption.REPLACE_EXISTING)
        log.info("Downloaded {} ({} bytes)", fileName, Files.size(targetFile))
        return targetFile
    }

    fun downloadAndExtract(fileName: String, targetDir: Path): Path {
        val url = "${properties.baseUrl}/$fileName"
        log.info("Downloading GeoNames file: {}", url)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(properties.downloadTimeout)
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw GeoNamesDownloadException(
                "Failed to download $url — HTTP ${response.statusCode()}"
            )
        }

        Files.createDirectories(targetDir)

        // Derive the expected data file name (e.g., "alternateNamesV2.zip" → "alternateNamesV2.txt")
        val expectedBaseName = fileName.substringBeforeLast('.')

        // Extract all entries; some GeoNames ZIPs contain multiple files
        val extractedFiles = mutableListOf<Path>()
        ZipInputStream(BufferedInputStream(response.body())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val targetFile = targetDir.resolve(entry.name)
                    Files.copy(zip, targetFile, StandardCopyOption.REPLACE_EXISTING)
                    log.info("Extracted {} ({} bytes)", entry.name, Files.size(targetFile))
                    extractedFiles.add(targetFile)
                }
                entry = zip.nextEntry
            }
        }

        if (extractedFiles.isEmpty()) {
            throw GeoNamesDownloadException("ZIP archive $fileName is empty")
        }

        // Return the file matching the expected base name, or fall back to the largest file
        return extractedFiles.firstOrNull { it.fileName.toString().startsWith(expectedBaseName) }
            ?: extractedFiles.maxBy { Files.size(it) }
    }
}

class GeoNamesDownloadException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
