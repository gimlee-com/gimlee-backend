package com.gimlee.auth.aspect

import com.gimlee.auth.annotation.CSRFProtected
import com.gimlee.auth.exception.AuthenticationException
import jakarta.annotation.Resource
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(HIGHEST_PRECEDENCE + 3)
class CSRFAspect {

    @Resource
    private val request: HttpServletRequest? = null

    @Pointcut("@annotation(csrfProtected)")
    fun annotatedWithCSRFProtected(csrfProtected: CSRFProtected) {
        // noop
    }

    @Before(value = "annotatedWithCSRFProtected(csrfProtected)", argNames = "csrfProtected")
    fun verify(csrfProtected: CSRFProtected) {
        if (request?.getHeader("X-CSRF-TOKEN") == null) {
            throw AuthenticationException("CSRF header not present for path: ${request?.contextPath}")
        }
    }
}
