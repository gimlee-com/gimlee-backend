package com.gimlee.user.domain

import com.gimlee.events.UserActivityEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UserActivityEventListener(
    private val userPresenceService: UserPresenceService
) {

    @EventListener
    fun handleUserActivity(event: UserActivityEvent) {
        userPresenceService.trackActivity(event.userId, event.timestamp)
    }
}
