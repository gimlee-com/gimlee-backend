package com.gimlee.support.ticket.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReplyToTicketRequestDto(
    @field:NotBlank @field:Size(max = 10000) val body: String
)
