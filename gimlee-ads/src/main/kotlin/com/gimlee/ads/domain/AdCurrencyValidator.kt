package com.gimlee.ads.domain

import com.gimlee.auth.model.Role
import com.gimlee.common.domain.model.Currency
import org.springframework.stereotype.Component

/**
 * Validator for checking if a user has the required roles to use certain currencies for ad listings.
 */
@Component
class AdCurrencyValidator(private val adCurrencyService: AdCurrencyService) {

    /**
     * Validates if a user with the given roles can list an item in the specified currency.
     * Throws [AdService.AdCurrencyRoleException] or [AdService.AdOperationException] if validation fails.
     */
    fun validateUserCanListInCurrency(userRoles: Collection<Role>, currency: Currency) {
        if (!adCurrencyService.canListInCurrency(userRoles, currency)) {
            val requiredRoleName = currency.requiredRole
            if (requiredRoleName != null) {
                val outcome = try {
                    AdOutcome.valueOf("${requiredRoleName}_ROLE_REQUIRED")
                } catch (e: Exception) {
                    AdOutcome.INVALID_OPERATION
                }
                throw AdService.AdCurrencyRoleException(outcome)
            } else {
                throw AdService.AdOperationException(AdOutcome.CURRENCY_NOT_ALLOWED, currency.name)
            }
        }
    }
}
