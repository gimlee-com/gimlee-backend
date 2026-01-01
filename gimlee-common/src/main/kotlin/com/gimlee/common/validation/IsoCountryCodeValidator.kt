package com.gimlee.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.util.Locale

class IsoCountryCodeValidator : ConstraintValidator<IsoCountryCode, String?> {
    private val isoCountries = Locale.getISOCountries().toSet()

    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) {
            return true
        }
        return isoCountries.contains(value)
    }
}
