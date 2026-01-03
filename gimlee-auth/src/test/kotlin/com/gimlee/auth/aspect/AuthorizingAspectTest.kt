package com.gimlee.auth.aspect

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.exception.AuthorizationException
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class AuthorizingAspectTest : StringSpec({

    val request = mockk<HttpServletRequest>()
    val aspect = AuthorizingAspect()
    
    // Inject mock request into aspect
    val requestField = AuthorizingAspect::class.java.getDeclaredField("request")
    requestField.isAccessible = true
    requestField.set(aspect, request)

    afterTest {
        unmockkAll()
    }

    "should throw AuthorizationException with resource info when role is missing" {
        val priv = mockk<Privileged>()
        every { priv.role } returns "ADMIN"
        
        val principal = Principal(userId = "1", username = "test", roles = listOf(Role.USER))
        
        mockkStatic(RequestContextHolder::class)
        val requestAttributes = mockk<RequestAttributes>()
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
        every { requestAttributes.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal
        
        val joinPoint = mockk<JoinPoint>()
        val signature = mockk<MethodSignature>()
        every { joinPoint.signature } returns signature
        every { signature.toShortString() } returns "TestController.testMethod()"
        
        every { request.method } returns "GET"
        every { request.requestURI } returns "/api/test"

        val exception = shouldThrow<AuthorizationException> {
            aspect.authorize(priv, joinPoint)
        }
        
        exception.message shouldBe "Insufficient privileges. The user doesn't have the required role: ADMIN."
        exception.resource shouldBe "GET /api/test (TestController.testMethod())"
    }
})
