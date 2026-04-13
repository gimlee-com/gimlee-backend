package com.gimlee.api.config

import com.gimlee.auth.domain.UserRole
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.notifications.domain.AdminUserProvider
import org.springframework.stereotype.Component

@Component
class DefaultAdminUserProvider(
    private val userRoleRepository: UserRoleRepository
) : AdminUserProvider {

    override fun getAdminUserIds(): List<String> =
        userRoleRepository.findAllByField(UserRole.FIELD_ROLE, Role.ADMIN)
            .map { it.userId.toHexString() }
}
