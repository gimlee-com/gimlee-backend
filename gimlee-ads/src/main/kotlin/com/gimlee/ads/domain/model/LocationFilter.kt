package com.gimlee.ads.domain.model

import org.springframework.data.geo.Circle

data class LocationFilter(
    val cityIds: List<String>?,
    val circle: Circle?
)