package com.gimlee.auth.aspect

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.events.UserActivityEvent
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Aspect
@Component
class UserActivityAspect(
    private val eventPublisher: ApplicationEventPublisher
) {

    @Pointcut("@annotation(priv)")
    fun annotatedWithPrivileged(priv: Privileged) {
        // noop
    }

    @Before(value = "annotatedWithPrivileged(priv)", argNames = "priv")
    fun trackActivity(priv: Privileged) {
        HttpServletRequestAuthUtil.getPrincipalOrNull()?.let { principal ->
            eventPublisher.publishEvent(UserActivityEvent(principal.userId))
        }
    }
}
