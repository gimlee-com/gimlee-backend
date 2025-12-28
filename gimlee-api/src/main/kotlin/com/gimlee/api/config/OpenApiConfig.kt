package com.gimlee.api.config

import com.gimlee.auth.annotation.Privileged
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.HandlerMethod

@Configuration
class OpenApiConfig(
    @Value("\${gimlee.auth.rest.unsecured-paths}")
    private val unsecuredPathPatterns: Array<String>
) {

    private val pathMatcher = AntPathMatcher()

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        return OpenAPI()
            .info(Info().title("Gimlee API").version("1.0"))
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                io.swagger.v3.oas.models.Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }

    @Bean
    fun operationCustomizer(): OperationCustomizer {
        return OperationCustomizer { operation, handlerMethod ->
            val paths = getMethodPaths(handlerMethod)
            val isUnsecured = paths.any { path -> 
                unsecuredPathPatterns.any { pattern -> pathMatcher.match(pattern, path) }
            }

            if (isUnsecured) {
                operation.security = emptyList()
            }

            val privilegedAnnotation = handlerMethod.getMethodAnnotation(Privileged::class.java)
            val description = StringBuilder(operation.description ?: "")
            
            if (isUnsecured) {
                description.append("\n\n**Security:** `Unsecured`")
            } else {
                description.append("\n\n**Security:** `JWT (Bearer)`")
                if (privilegedAnnotation != null) {
                    val role = privilegedAnnotation.role
                    description.append("\n\n**Required Role:** `$role`")
                } else {
                    description.append("\n\n**Required Role:** `Authenticated User` (Default)")
                }
            }
            
            operation.description = description.toString().trim()

            operation
        }
    }

    private fun getMethodPaths(handlerMethod: HandlerMethod): List<String> {
        val classMapping = handlerMethod.beanType.getAnnotation(RequestMapping::class.java)?.let {
            it.path.ifEmpty { it.value }.toList()
        } ?: listOf("")

        val methodPaths = when {
            handlerMethod.hasMethodAnnotation(RequestMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(RequestMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            handlerMethod.hasMethodAnnotation(GetMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(GetMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            handlerMethod.hasMethodAnnotation(PostMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(PostMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            handlerMethod.hasMethodAnnotation(PutMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(PutMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            handlerMethod.hasMethodAnnotation(DeleteMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(DeleteMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            handlerMethod.hasMethodAnnotation(PatchMapping::class.java) -> {
                val ann = handlerMethod.getMethodAnnotation(PatchMapping::class.java)!!
                ann.path.ifEmpty { ann.value }.toList()
            }
            else -> listOf("")
        }

        return classMapping.flatMap { cp ->
            methodPaths.map { mp ->
                "/" + (cp.trim('/') + "/" + mp.trim('/')).trim('/')
            }
        }.map { it.replace("//", "/") }
    }
}
