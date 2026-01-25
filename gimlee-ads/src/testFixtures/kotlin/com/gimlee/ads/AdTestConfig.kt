package com.gimlee.ads

import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.payments.domain.service.CurrencyConverterService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class AdTestConfig {
    @Bean
    fun currencyConverterService(): CurrencyConverterService = mockk(relaxed = true)

    @Bean
    fun userRoleRepository(): UserRoleRepository = mockk(relaxed = true)
}
