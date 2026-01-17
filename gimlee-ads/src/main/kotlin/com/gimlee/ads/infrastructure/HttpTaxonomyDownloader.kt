package com.gimlee.ads.infrastructure

import com.gimlee.ads.domain.service.TaxonomyDownloader
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class HttpTaxonomyDownloader : TaxonomyDownloader {

    private val client = HttpClient.newHttpClient()

    override fun download(url: String): List<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to download taxonomy from $url: ${response.statusCode()}")
        }

        return BufferedReader(InputStreamReader(response.body())).use { reader ->
            reader.readLines()
        }
    }
}

