package com.gimlee.ads.infrastructure

import com.gimlee.ads.domain.service.TaxonomyDownloader
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
class HttpTaxonomyDownloader : TaxonomyDownloader {

    override fun download(url: String): List<String> {
        val httpClient = HttpClients.createDefault()
        val httpGet = HttpGet(url)

        return httpClient.execute(httpGet).use { response ->
            if (response.code != 200) {
                throw RuntimeException("Failed to download taxonomy from $url: ${response.code}")
            }
            val entity = response.entity
            BufferedReader(InputStreamReader(entity.content)).use { reader ->
                val lines = reader.readLines()
                EntityUtils.consume(entity)
                lines
            }
        }
    }
}
