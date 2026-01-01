package com.gimlee.common.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IetfLanguageTagValidator::class])
annotation class IetfLanguageTag(
    val message: String = "Invalid IETF language tag (e.g., en-US).",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
