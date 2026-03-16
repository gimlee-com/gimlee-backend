package com.gimlee.ads.domain.service

import java.text.Normalizer
import java.util.*
import java.util.regex.Pattern

object CategorySlugUtils {

    private val NON_LATIN = Pattern.compile("[^\\w-]")
    private val WHITESPACE = Pattern.compile("[\\s]")

    fun slugify(input: String): String {
        val withAnd = input.replace("&", " and ")
        val nowhitespace = WHITESPACE.matcher(withAnd).replaceAll("-")
        val withSpecialReplaced = nowhitespace.replace("ł", "l").replace("Ł", "L")
        val normalized = Normalizer.normalize(withSpecialReplaced, Normalizer.Form.NFD)
        val slug = NON_LATIN.matcher(normalized).replaceAll("")
        return slug.lowercase(Locale.ENGLISH)
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}
