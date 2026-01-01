package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.types.ObjectId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import com.gimlee.auth.model.Principal
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.user.UserVerificationService
import com.gimlee.auth.web.dto.request.VerifyUserRequestDto
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid

@Tag(name = "Verification", description = "Endpoints for user account verification")
@RestController
class VerificationController(
    private val userVerificationService: UserVerificationService
) {
    @Operation(
        summary = "Verify User",
        description = "Verifies the user's account using a code sent to their email. Requires a valid (but possibly unverified) session."
    )
    @ApiResponse(responseCode = "200", description = "User verified successfully")
    @PostMapping(path = ["/auth/verifyUser"])
    @ResponseStatus(HttpStatus.OK)
    fun verifyUser(
        @Valid @RequestBody verificationData: VerifyUserRequestDto,
        response: HttpServletResponse
    ): IdentityVerificationResponse {

        val principal = RequestContextHolder.getRequestAttributes()!!
            .getAttribute("principal", RequestAttributes.SCOPE_REQUEST) as Principal

        return userVerificationService.verifyCode(
            ObjectId(principal.userId),
            verificationData.code
        )
    }
}