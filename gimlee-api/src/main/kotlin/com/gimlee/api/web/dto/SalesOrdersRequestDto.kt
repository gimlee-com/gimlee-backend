package com.gimlee.api.web.dto

import com.gimlee.purchases.domain.model.PurchaseSortDirection
import com.gimlee.purchases.domain.model.PurchaseSortField
import com.gimlee.purchases.domain.model.PurchaseStatus
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Request parameters for filtering, sorting, and paginating sales orders")
data class SalesOrdersRequestDto(
    @field:Parameter(description = "Page number (0-indexed)")
    val p: Int = 0,
    @field:Parameter(description = "Filter by purchase status(es)")
    val status: List<PurchaseStatus>? = null,
    @field:Parameter(description = "Search by order ID or buyer username")
    val q: String? = null,
    @field:Parameter(description = "Filter by ad ID")
    val adId: String? = null,
    @field:Parameter(description = "Filter orders created from this timestamp (inclusive)")
    val from: Instant? = null,
    @field:Parameter(description = "Filter orders created until this timestamp (inclusive)")
    val to: Instant? = null,
    @field:Parameter(description = "Sort by field")
    val by: PurchaseSortField = PurchaseSortField.DATE,
    @field:Parameter(description = "Sort direction")
    val dir: PurchaseSortDirection = PurchaseSortDirection.DESC
)
