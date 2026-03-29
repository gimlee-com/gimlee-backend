package com.gimlee.auth.aspect
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.exception.AuthenticationException
import com.gimlee.auth.exception.AuthorizationException
import com.gimlee.auth.model.Role
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import jakarta.annotation.Resource
import jakarta.servlet.http.HttpServletRequest

@Aspect
@Component
@Order(HIGHEST_PRECEDENCE + 2)
class AuthorizingAspect {

    @Resource
    private val request: HttpServletRequest? = null

    private val roleHierarchy: Map<Role, Int> = mapOf(
        Role.ADMIN to 100,
        Role.SUPPORT to 80,
        Role.PUBLISHER to 60,
        Role.PIRATE to 50,
        Role.YCASH to 50,
        Role.USER to 40,
        Role.UNVERIFIED to 10
    )

    @Pointcut("@annotation(priv)")
    fun annotatedWithPrivileged(priv: Privileged) {
        // noop
    }

    @Before(value = "annotatedWithPrivileged(priv)", argNames = "priv, joinPoint")
    fun authorize(priv: Privileged, joinPoint: JoinPoint) {

        val userRoles = HttpServletRequestAuthUtil.getPrincipal().roles

        if (priv.role.isNotEmpty()) {
            val requiredRole = Role.valueOf(priv.role)
            val requiredLevel = roleHierarchy[requiredRole] ?: 0
            val userMaxLevel = userRoles.maxOfOrNull { roleHierarchy[it] ?: 0 } ?: 0

            if (userMaxLevel < requiredLevel) {
                val resource = "${request?.method} ${request?.requestURI} (${joinPoint.signature.toShortString()})"
                throw AuthorizationException(
                    "Insufficient privileges. The user doesn't have the required role: ${priv.role}.",
                    resource
                )
            }
        }
    }
}
