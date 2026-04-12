package com.gimlee.auth.user

import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.events.UserRegisteredEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class RegistrationService(
    private val userVerificationService: UserVerificationService,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun register(user: User, countryOfResidence: String? = null) {
        val registeredUser = userRepository.save(user)
        userRoleRepository.add(registeredUser.id!!, Role.UNVERIFIED)
        eventPublisher.publishEvent(
            UserRegisteredEvent(
                userId = registeredUser.id.toHexString(),
                countryOfResidence = countryOfResidence
            )
        )
        userVerificationService.sendVerificationCode(registeredUser)
    }
}