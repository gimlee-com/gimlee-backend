package com.gimlee.location.geoip

import com.gimlee.common.getClientIpAddress
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.location.cities.domain.LocationOutcome
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "GeoIP", description = "Endpoints for IP-based geolocation")
@RestController
@RequestMapping("/location/geoip")
class GeoIpController(
    private val geoIpService: GeoIpService,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Detect Country by IP",
        description = "Detects the country of the requesting client based on their IP address. " +
                "Returns an ISO 3166-1 alpha-2 country code. Useful for pre-selecting country defaults in the UI."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Country detected successfully. Response data contains the country code.",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Country could not be determined from the IP address. Possible status codes: LOCATION_GEOIP_COUNTRY_UNKNOWN",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "503",
        description = "GeoIP service is unavailable. Possible status codes: LOCATION_GEOIP_UNAVAILABLE",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/country")
    fun detectCountry(request: HttpServletRequest): ResponseEntity<Any> {
        if (!geoIpService.isAvailable) {
            return handleOutcome(LocationOutcome.GEOIP_UNAVAILABLE)
        }

        val clientIp = getClientIpAddress(request)
        log.debug("GeoIP country lookup for IP: {}", clientIp)

        val countryCode = geoIpService.resolveCountry(clientIp)

        return if (countryCode != null) {
            handleOutcome(LocationOutcome.GEOIP_COUNTRY_DETECTED, mapOf("countryCode" to countryCode))
        } else {
            handleOutcome(LocationOutcome.GEOIP_COUNTRY_UNKNOWN)
        }
    }
}
