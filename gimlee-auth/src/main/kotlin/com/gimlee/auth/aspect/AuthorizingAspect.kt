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

    @Pointcut("@annotation(priv)")
    fun annotatedWithPrivileged(priv: Privileged) {
        // noop
    }

    @Before(value = "annotatedWithPrivileged(priv)", argNames = "priv, joinPoint")
    fun authorize(priv: Privileged, joinPoint: JoinPoint) {

        val userRoles = HttpServletRequestAuthUtil.getPrincipal().roles
        val requiredRole = Role.valueOf(priv.role)

        if (priv.role.isNotEmpty()) {
            userRoles.stream().filter { userRole -> userRole == requiredRole }
                .findAny()
                .orElseThrow {
                    val resource = "${request?.method} ${request?.requestURI} (${joinPoint.signature.toShortString()})"
                    AuthorizationException(
                        "Insufficient privileges. The user doesn't have the required role: ${priv.role}.",
                        resource
                    )
                }
        }
    }
}
