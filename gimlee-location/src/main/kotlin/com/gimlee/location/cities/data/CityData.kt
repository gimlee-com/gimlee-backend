package com.gimlee.location.cities.data

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import com.fasterxml.jackson.dataformat.csv.CsvSchema

private fun getCities(): List<City> {
    val citiesResource = (object {}).javaClass.classLoader.getResourceAsStream("cities/cities.tsv")

    val mapper = CsvMapper()
    mapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);

    return mapper
        .readerFor(City::class.java)
        .with(CsvSchema.emptySchema().withHeader().withColumnSeparator('\t'))
        .readValues<City>(citiesResource)
        .readAll()
}

val cityDataUnsorted = getCities()
val cityDataById = cityDataUnsorted.associateBy { it.id }