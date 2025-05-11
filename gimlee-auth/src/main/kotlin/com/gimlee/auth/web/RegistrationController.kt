package com.gimlee.auth.web

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
import com.gimlee.common.domain.model.StatusCode
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.validation.Valid

@RestController
class RegistrationController(
    private val registrationService: RegistrationService,
    private val userRepository: UserRepository
) {

    @PostMapping(path = ["/auth/register"])
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody registrationData: RegisterRequestDto): StatusResponseDto {
        registrationService.register(registrationData.toUser())
        return StatusResponseDto.fromStatusCode(StatusCode.SUCCESS)
    }

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