package com.gimlee.api.cities.search

import org.simmetrics.StringMetric
import org.simmetrics.builders.StringMetricBuilder
import org.simmetrics.metrics.StringMetrics
import org.simmetrics.simplifiers.Simplifiers
import org.springframework.stereotype.Component
import com.gimlee.api.cities.data.City
import com.gimlee.api.cities.data.cityDataById
import com.gimlee.api.cities.data.cityDataUnsorted
import com.gimlee.api.cities.domain.CitySuggestion


val metric: StringMetric = StringMetricBuilder.with(StringMetrics.jaroWinkler())
    .simplify(Simplifiers.chain(listOf(Simplifiers.toLowerCase(), Simplifiers.removeDiacritics())))
    .build()

@Component
class PolandCitySearch: CitySearch {

    companion object {
        private const val MAJOR_CITY_PREFIX = "m. "
    }

    override fun getSuggestions(phrase: String): List<CitySuggestion> {
        return cityDataUnsorted.asSequence()
            .map { city ->
                var score = metric.compare(city.name, phrase)
                if (!city.district.isNullOrEmpty()) {
                    val districtScore = metric.compare(city.district, phrase)
                    score = maxOf(districtScore, score)
                }
                if (city.adm2.startsWith(MAJOR_CITY_PREFIX)) {
                    score += 0.01f
                }
                CitySuggestion(city, score)
            }
            .sortedByDescending { it.score }
            .take(5)
            .filter { it.score > 0.7f }
            .toList()
    }

    override fun getCityById(id: String): City? {
        return cityDataById[id];
    }
}
