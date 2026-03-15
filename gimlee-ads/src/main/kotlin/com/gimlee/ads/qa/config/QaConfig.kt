package com.gimlee.ads.qa.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(QaProperties::class)
class QaConfig
