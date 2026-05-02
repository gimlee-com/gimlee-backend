package com.gimlee.location.cities.geonames.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GeoNamesProperties::class)
class GeoNamesConfig
