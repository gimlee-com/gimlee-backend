package com.gimlee.ratings.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MarkdownContentValidator(
    @Value("\${gimlee.ratings.max-body-length:5000}") private val maxBodyLength: Int,
    @Value("\${gimlee.ratings.max-title-length:200}") private val maxTitleLength: Int
) {

    companion object {
        private val DISALLOWED_PATTERNS = listOf(
            Regex("<script[^>]*>", RegexOption.IGNORE_CASE),
            Regex("</script>", RegexOption.IGNORE_CASE),
            Regex("<iframe[^>]*>", RegexOption.IGNORE_CASE),
            Regex("</iframe>", RegexOption.IGNORE_CASE),
            Regex("<object[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<embed[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<form[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<input[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<textarea[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<style[^>]*>", RegexOption.IGNORE_CASE),
            Regex("</style>", RegexOption.IGNORE_CASE),
            Regex("<link[^>]*>", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]*>", RegexOption.IGNORE_CASE),
            Regex("\\bon\\w+\\s*=", RegexOption.IGNORE_CASE),
            Regex("javascript\\s*:", RegexOption.IGNORE_CASE),
            Regex("data\\s*:", RegexOption.IGNORE_CASE),
            Regex("vbscript\\s*:", RegexOption.IGNORE_CASE),
        )

        private val ALLOWED_TAGS = setOf(
            "p", "br", "strong", "b", "em", "i", "u", "s", "del",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "blockquote", "pre", "code",
            "a", "img", "hr", "table", "thead", "tbody", "tr", "th", "td",
            "span", "div", "sub", "sup"
        )
    }

    fun validateBody(content: String?): ValidationResult {
        if (content == null) return ValidationResult.valid()
        if (content.length > maxBodyLength) {
            return ValidationResult.invalid("Body exceeds maximum length of $maxBodyLength characters")
        }
        return validateSanitization(content)
    }

    fun validateTitle(content: String?): ValidationResult {
        if (content == null) return ValidationResult.valid()
        if (content.length > maxTitleLength) {
            return ValidationResult.invalid("Title exceeds maximum length of $maxTitleLength characters")
        }
        return validateSanitization(content)
    }

    fun validateResponse(content: String): ValidationResult {
        if (content.length > maxBodyLength) {
            return ValidationResult.invalid("Response exceeds maximum length of $maxBodyLength characters")
        }
        return validateSanitization(content)
    }

    private fun validateSanitization(content: String): ValidationResult {
        for (pattern in DISALLOWED_PATTERNS) {
            if (pattern.containsMatchIn(content)) {
                return ValidationResult.invalid("Content contains disallowed markup")
            }
        }
        return ValidationResult.valid()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    ) {
        companion object {
            fun valid() = ValidationResult(true)
            fun invalid(reason: String) = ValidationResult(false, reason)
        }
    }
}
