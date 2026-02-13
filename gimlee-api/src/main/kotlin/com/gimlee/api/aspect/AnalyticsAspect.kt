package com.gimlee.api.aspect

import com.gimlee.api.config.AnalyticsProperties
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.annotation.Analytics
import com.gimlee.common.toMicros
import com.gimlee.events.GenericAnalyticsEvent
import jakarta.servlet.http.HttpServletRequest
import net.openhft.hashing.LongHashFunction
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.context.ApplicationEventPublisher
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

@Aspect
@Component
class AnalyticsAspect(
    private val eventPublisher: ApplicationEventPublisher,
    private val properties: AnalyticsProperties
) {
    private val parser = SpelExpressionParser()
    private val hasher = LongHashFunction.xx3()

    @Before("@annotation(analytics)")
    fun trackAnalytics(joinPoint: JoinPoint, analytics: Analytics) {
        if (!properties.enabled) return

        // 1. Sampling check
        if (analytics.sampleRate < 1.0 && ThreadLocalRandom.current().nextDouble() > analytics.sampleRate) {
            return
        }

        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return

        // 2. Bot score check
        val botScoreHeader = request.getHeader(properties.botScoreHeader)
        val botScore = botScoreHeader?.toDoubleOrNull() ?: 0.0
        if (botScore > properties.botScoreThreshold) return

        // 3. Extract targetId using SpEL
        val targetId = if (analytics.targetId.isNotEmpty()) {
            resolveSpel(joinPoint, analytics.targetId)
        } else null

        // 4. Gather context
        val userId = HttpServletRequestAuthUtil.getPrincipalOrNull()?.userId
        val clientId = request.getHeader(properties.clientIdHeader) ?: generateClientIdFallback(request)
        val userAgent = request.getHeader("User-Agent")
        val referrer = request.getHeader("Referer")

        // 5. Publish event
        val event = GenericAnalyticsEvent(
            type = analytics.type,
            targetId = targetId,
            timestampMicros = Instant.now().toMicros(),
            sampleRate = analytics.sampleRate,
            userId = userId,
            clientId = clientId,
            botScore = if (botScore > 0) botScore else null,
            userAgent = userAgent,
            referrer = referrer
        )
        
        eventPublisher.publishEvent(event)
    }

    private fun resolveSpel(joinPoint: JoinPoint, expression: String): String? {
        val signature = joinPoint.signature as MethodSignature
        val context = StandardEvaluationContext()
        
        val parameterNames = signature.parameterNames
        val args = joinPoint.args
        
        for (i in parameterNames.indices) {
            context.setVariable(parameterNames[i], args[i])
        }
        
        return try {
            parser.parseExpression(expression).getValue(context, String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateClientIdFallback(request: HttpServletRequest): String {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent") ?: "unknown"
        return hasher.hashChars("$ip|$ua").toString()
    }
}
