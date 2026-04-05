package com.gimlee.location.countries

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.concurrent.TimeUnit

@Service
class CountryService {

    private val countriesCache = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build<String, List<CountryDto>>()

    fun getCountries(locale: Locale): List<CountryDto> {
        val cacheKey = locale.toLanguageTag()
        return countriesCache.get(cacheKey) { buildCountryList(locale) }!!
    }

    private fun buildCountryList(locale: Locale): List<CountryDto> {
        return Locale.getISOCountries()
            .map { code ->
                val countryLocale = Locale.Builder().setRegion(code).build()
                CountryDto(
                    code = code,
                    name = countryLocale.getDisplayCountry(locale)
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
    }
}
