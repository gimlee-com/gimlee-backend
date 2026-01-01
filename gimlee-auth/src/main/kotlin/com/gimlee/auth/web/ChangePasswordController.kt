package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.auth.user.ChangePasswordService
import com.gimlee.auth.web.dto.request.ChangePasswordRequestDto
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.validation.Valid

@Tag(name = "Authentication")
@RestController
class ChangePasswordController(
    private val changePasswordService: ChangePasswordService
) {
    @Operation(
        summary = "Change Password",
        description = "Allows an authenticated user to change their password."
    )
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @PostMapping(path = ["/auth/changePassword"])
    fun changePassword(
        @Valid @RequestBody changePasswordRequest: ChangePasswordRequestDto
    ): StatusResponseDto {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val changePasswordStatus = changePasswordService.changePassword(
            userId,
            changePasswordRequest.oldPassword,
            changePasswordRequest.newPassword
        )

        return StatusResponseDto.fromStatusCode(changePasswordStatus)
    }
}
