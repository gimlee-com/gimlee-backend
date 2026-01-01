package com.gimlee.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.util.Locale

class IetfLanguageTagValidator : ConstraintValidator<IetfLanguageTag, String?> {
    private val isoLanguages = Locale.getISOLanguages().toSet()
    private val isoCountries = Locale.getISOCountries().toSet()
    private val regex = Regex("^[a-z]{2}-[A-Z]{2}$")

    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) {
            return true
        }
        if (!regex.matches(value)) {
            return false
        }
        val parts = value.split("-")
        return isoLanguages.contains(parts[0]) && isoCountries.contains(parts[1])
    }
}
