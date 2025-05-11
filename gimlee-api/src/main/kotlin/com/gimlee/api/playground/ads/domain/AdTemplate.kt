package com.gimlee.api.playground.ads.domain

/**
 * Represents a row read from the ads.tsv file.
 */
data class AdTemplate(
    var title: String = "",
    var description: String = ""
)