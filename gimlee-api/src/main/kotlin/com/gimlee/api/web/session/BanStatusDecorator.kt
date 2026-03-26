package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import com.gimlee.auth.domain.BanService
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class BanStatusDecorator(private val banService: BanService) : SessionDecorator {
    override val name: String = "banStatus"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        if (principal.isEmptyOrNull()) {
            response.data["banned"] = false
            return
        }

        val activeBan = banService.getActiveBan(principal!!.userId)
        if (activeBan != null) {
            response.data["banned"] = true
            response.data["banReason"] = activeBan.reason
            response.data["bannedAt"] = activeBan.bannedAt
            response.data["bannedUntil"] = activeBan.bannedUntil
        } else {
            response.data["banned"] = false
        }
    }
}
