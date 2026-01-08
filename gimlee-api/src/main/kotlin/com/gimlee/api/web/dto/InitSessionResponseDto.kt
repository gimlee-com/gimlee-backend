package com.gimlee.api.web.dto

import com.fasterxml.jackson.annotation.JsonAnyGetter
import io.swagger.v3.oas.annotations.media.Schema

data class InitSessionResponseDto(
    @get:JsonAnyGetter
    @get:Schema(hidden = true)
    val data: MutableMap<String, Any?> = mutableMapOf()
)
