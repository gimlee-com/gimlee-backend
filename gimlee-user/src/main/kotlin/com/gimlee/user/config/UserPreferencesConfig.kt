package com.gimlee.user.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(UserPreferencesProperties::class)
class UserPreferencesConfig
