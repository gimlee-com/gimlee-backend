package com.gimlee.ads.domain

import com.gimlee.auth.model.Role
import com.gimlee.common.domain.model.Currency
import org.springframework.stereotype.Service

/**
 * Domain component for managing and validating currencies allowed for ad listings.
 */
@Service
class AdCurrencyService {

    /**
     * Retrieves the list of currencies a user with the given roles can list items with.
     */
    fun getAllowedCurrencies(userRoles: Collection<Role>): List<Currency> {
        return Currency.entries.filter { it.isSettlement && canListInCurrency(userRoles, it) }
    }

    /**
     * Validates if a user with the given roles can list an item in the specified currency.
     */
    fun canListInCurrency(userRoles: Collection<Role>, currency: Currency): Boolean {
        if (!currency.isSettlement) return false
        val requiredRoleName = currency.requiredRole ?: return true
        return userRoles.any { it.name == requiredRoleName }
    }
}
