package com.gimlee.ads.domain.service

interface TaxonomyDownloader {
    fun download(url: String): List<String>
}

