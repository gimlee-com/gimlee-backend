package com.gimlee.user.domain

import com.gimlee.events.UserRegisteredEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UserRegisteredEventListener(
    private val userPreferencesService: UserPreferencesService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleUserRegistered(event: UserRegisteredEvent) {
        val country = event.countryOfResidence ?: return

        try {
            userPreferencesService.setCountryOfResidence(event.userId, country)
            log.info("Set country of residence {} for newly registered user {}", country, event.userId)
        } catch (e: Exception) {
            log.error("Failed to set country of residence for user {}: {}", event.userId, e.message, e)
        }
    }
}
