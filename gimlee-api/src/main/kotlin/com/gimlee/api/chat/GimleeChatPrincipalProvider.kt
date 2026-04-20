package com.gimlee.api.chat

import com.gimlee.chat.domain.model.ChatPrincipalProvider
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import org.springframework.stereotype.Component

@Component
class GimleeChatPrincipalProvider : ChatPrincipalProvider {
    override fun getUserId(): String = HttpServletRequestAuthUtil.getPrincipal().userId
    override fun getUsername(): String = HttpServletRequestAuthUtil.getPrincipal().username
}
