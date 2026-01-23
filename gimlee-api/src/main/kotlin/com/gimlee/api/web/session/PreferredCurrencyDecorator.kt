package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.user.domain.UserPreferencesService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class PreferredCurrencyDecorator(private val userPreferencesService: UserPreferencesService) : SessionDecorator {
    override val name: String = "preferredCurrency"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()

        val currency = if (!principal.isEmptyOrNull()) {
            userPreferencesService.getUserPreferences(principal!!.userId).preferredCurrency
        } else {
            userPreferencesService.getDefaultCurrency()
        }

        response.data["preferredCurrency"] = currency
    }
}
