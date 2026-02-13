package com.gimlee.api.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AdDiscoveryProperties::class, AnalyticsProperties::class)
class AdDiscoveryConfig
