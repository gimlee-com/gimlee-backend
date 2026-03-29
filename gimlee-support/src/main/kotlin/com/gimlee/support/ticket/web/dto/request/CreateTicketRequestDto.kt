package com.gimlee.support.ticket.web.dto.request

import com.gimlee.support.ticket.domain.model.TicketCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateTicketRequestDto(
    @field:NotBlank @field:Size(max = 200) val subject: String,
    @field:NotNull val category: TicketCategory,
    @field:NotBlank @field:Size(max = 10000) val body: String
)
