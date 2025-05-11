package com.gimlee.auth.user

import org.springframework.stereotype.Service
import com.gimlee.auth.model.Role
import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository

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