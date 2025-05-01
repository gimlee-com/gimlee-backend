package com.gimlee.auth.filter

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.logging.log4j.LogManager
import org.springframework.http.HttpStatus
import org.springframework.util.AntPathMatcher
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.util.JwtTokenVerifier
import com.gimlee.auth.util.getClientIpAddress
import com.gimlee.auth.util.getJwtCookie
import java.io.UnsupportedEncodingException

class JWTFilter(
    private val jwtTokenVerifier: JwtTokenVerifier,
    private val unsecuredPathPatterns: Array<String>
) : OncePerRequestFilter() {

    companion object {
        private val pathMatcher = AntPathMatcher()
        private val log = LogManager.getLogger()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val jwt = getJwtCookie(request).value
        val jwtData = if (jwt.isNotEmpty()) getJwtData(request, jwt, response) else null

        when {
            isPathUnsecured(request.servletPath) -> {
                RequestContextHolder.getRequestAttributes()!!.setAttribute(
                    "principal",
                    if (jwtData != null) {
                        Principal(
                            userId = jwtData.subject,
                            username = jwtData.getClaim("username").asString(),
                            roles = jwtData.getClaim("roles").asList(Role::class.java))
                    } else {
                        Principal("", "", emptyList())
                    },
                    SCOPE_REQUEST
                )
                filterChain.doFilter(request, response)
            }
            jwtData != null -> {
                RequestContextHolder.getRequestAttributes()!!.setAttribute(
                    "principal",
                    Principal(
                        userId = jwtData.subject,
                        username = jwtData.getClaim("username").asString(),
                        roles = jwtData.getClaim("roles").asList(Role::class.java)),
                    SCOPE_REQUEST
                )
                filterChain.doFilter(request, response)
            }
            else -> {
                log.warn(
                    "Unauthenticated access to endpoint ${request.requestURI} from IP: ${getClientIpAddress(request)}"
                )
                response.status = HttpStatus.UNAUTHORIZED.value()
            }
        }
    }

    private fun getJwtData(
        request: HttpServletRequest,
        jwt: String,
        response: HttpServletResponse
    ): DecodedJWT? {
        try {
            jwtTokenVerifier.verifyToken(request)
            return JWT.decode(jwt)
        } catch (e: Exception) {
            when (e) {
                is UnsupportedEncodingException,
                is JWTVerificationException -> {
                    log.warn("Could not decode / verify JWT")
                }
                else -> {
                    log.error("Unknown exception", e)
                    response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            }
        }
        return null
    }

    override fun destroy() {

    }

    private fun isPathUnsecured(path: String): Boolean {
        return unsecuredPathPatterns.any { unsecuredPath -> pathMatcher.match(unsecuredPath, path) }
    }
}
