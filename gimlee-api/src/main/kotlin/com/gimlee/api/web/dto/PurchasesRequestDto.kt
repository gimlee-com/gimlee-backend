package com.gimlee.api.web.dto

import com.gimlee.purchases.domain.model.PurchaseSortDirection
import com.gimlee.purchases.domain.model.PurchaseSortField
import com.gimlee.purchases.domain.model.PurchaseStatus
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Request parameters for filtering, sorting, and paginating purchase history")
data class PurchasesRequestDto(
    @field:Parameter(description = "Page number (0-indexed)")
    val p: Int = 0,
    @field:Parameter(description = "Filter by purchase status(es)")
    val status: List<PurchaseStatus>? = null,
    @field:Parameter(description = "Search by order ID or seller username")
    val q: String? = null,
    @field:Parameter(description = "Filter purchases created from this timestamp (inclusive)")
    val from: Instant? = null,
    @field:Parameter(description = "Filter purchases created until this timestamp (inclusive)")
    val to: Instant? = null,
    @field:Parameter(description = "Sort by field")
    val by: PurchaseSortField = PurchaseSortField.DATE,
    @field:Parameter(description = "Sort direction")
    val dir: PurchaseSortDirection = PurchaseSortDirection.DESC
)
