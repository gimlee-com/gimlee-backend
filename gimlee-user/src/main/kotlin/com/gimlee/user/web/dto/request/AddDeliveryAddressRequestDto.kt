package com.gimlee.user.web.dto.request

import com.gimlee.common.validation.IsoCountryCode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddDeliveryAddressRequestDto(
    @field:NotBlank(message = "Name cannot be blank.")
    @field:Size(max = 50, message = "Name cannot exceed 50 characters.")
    val name: String,

    @field:NotBlank(message = "Full name cannot be blank.")
    @field:Size(max = 100, message = "Full name cannot exceed 100 characters.")
    val fullName: String,

    @field:NotBlank(message = "Street cannot be blank.")
    @field:Size(max = 255, message = "Street cannot exceed 255 characters.")
    val street: String,

    @field:NotBlank(message = "City cannot be blank.")
    @field:Size(max = 100, message = "City cannot exceed 100 characters.")
    val city: String,

    @field:NotBlank(message = "Postal code cannot be blank.")
    @field:Size(max = 20, message = "Postal code cannot exceed 20 characters.")
    val postalCode: String,

    @field:IsoCountryCode
    @field:NotBlank(message = "Country cannot be blank.")
    val country: String,

    @field:NotBlank(message = "Phone number cannot be blank.")
    @field:Size(max = 20, message = "Phone number cannot exceed 20 characters.")
    val phoneNumber: String,

    val isDefault: Boolean = false
)
