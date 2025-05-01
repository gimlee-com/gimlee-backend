package com.gimlee.api.auth.service.user

import org.springframework.stereotype.Service
import com.gimlee.auth.model.Role
import com.gimlee.api.auth.domain.User
import com.gimlee.api.auth.persistence.UserRepository
import com.gimlee.api.auth.persistence.UserRoleRepository

@Service
class RegistrationService(
    private val userVerificationService: UserVerificationService,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository
) {

    fun register(user: User) {
        val registeredUser = userRepository.save(user)
        userRoleRepository.add(registeredUser.id!!, Role.UNVERIFIED)
        userVerificationService.sendVerificationCode(registeredUser)
    }
}