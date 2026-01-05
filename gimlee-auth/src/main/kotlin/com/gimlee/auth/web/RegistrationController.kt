package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.domain.User
import com.gimlee.auth.user.RegistrationService
import com.gimlee.auth.web.dto.request.EmailAvailableRequestDto
import com.gimlee.auth.web.dto.request.RegisterRequestDto
import com.gimlee.auth.web.dto.request.UsernameAvailableRequestDto
import com.gimlee.auth.web.dto.response.AvailabilityStatusResponseDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity

@Tag(name = "Registration", description = "Endpoints for user registration and availability checks")
@RestController
class RegistrationController(
    private val registrationService: RegistrationService,
    private val userRepository: UserRepository,
    private val messageSource: MessageSource
) {

    @Operation(
        summary = "Register User",
        description = "Creates a new user account. The account will initially be unverified. Registration requires a unique username and email."
    )
    @ApiResponse(
        responseCode = "201",
        description = "User created successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping(path = ["/auth/register"])
    fun register(@Valid @RequestBody registrationData: RegisterRequestDto): ResponseEntity<StatusResponseDto> {
        registrationService.register(registrationData.toUser())
        val outcome = CommonOutcome.SUCCESS
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(HttpStatus.CREATED).body(StatusResponseDto.fromOutcome(outcome, message))
    }

    @Operation(
        summary = "Check Username Availability",
        description = "Checks if a username is already taken."
    )
    @ApiResponse(responseCode = "200", description = "Availability status returned")
    @PostMapping(path = ["/auth/register/usernameAvailable"])
    fun checkUsernameAvailability(
        @Valid @RequestBody usernameRequest: UsernameAvailableRequestDto
    ): AvailabilityStatusResponseDto {
        return if (userRepository.findOneByField(User.FIELD_USERNAME, usernameRequest.username) == null) {
            AvailabilityStatusResponseDto.available
        } else {
            AvailabilityStatusResponseDto.notAvailable
        }
    }

    @Operation(
        summary = "Check Email Availability",
        description = "Checks if an email is already associated with an account."
    )
    @ApiResponse(responseCode = "200", description = "Availability status returned")
    @PostMapping(path = ["/auth/register/emailAvailable"])
    fun checkEmailAvailability(
        @Valid @RequestBody emailRequest: EmailAvailableRequestDto
    ): AvailabilityStatusResponseDto {
        return if (userRepository.findOneByField(User.FIELD_EMAIL, emailRequest.email) == null) {
            AvailabilityStatusResponseDto.available
        } else {
            AvailabilityStatusResponseDto.notAvailable
        }
    }
}