package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.web.dto.response.UserProfileDto
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class UserProfileDecorator(private val profileService: ProfileService) : SessionDecorator {
    override val name: String = "userProfile"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        if (!principal.isEmptyOrNull()) {
            val profile = profileService.getProfile(principal!!.userId)
            response.data["userProfile"] = profile?.let { UserProfileDto.fromDomain(it) }
        } else {
            response.data["userProfile"] = null
        }
    }
}
