package com.gimlee.auth.util

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import com.gimlee.auth.model.Principal

class HttpServletRequestAuthUtil {
    companion object {
        fun getPrincipal() = RequestContextHolder.getRequestAttributes()!!.getAttribute(
            "principal",
            RequestAttributes.SCOPE_REQUEST
        ) as Principal

        fun getPrincipalOrNull() = RequestContextHolder.getRequestAttributes()?.getAttribute(
            "principal",
            RequestAttributes.SCOPE_REQUEST
        ) as? Principal
    }
}