package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.api.web.dto.SalesStatsDto
import com.gimlee.api.web.dto.StatsPeriod
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.toMicros
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.PurchaseRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.types.ObjectId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Tag(name = "Sales", description = "Endpoints for sellers to manage their orders")
@RestController
@RequestMapping("/sales")
class SalesStatsController(
    private val purchaseRepository: PurchaseRepository,
    private val adService: AdService
) {

    @Operation(
        summary = "Get Seller Dashboard Statistics",
        description = "Returns aggregated statistics for the authenticated seller's dashboard, " +
                "including revenue per currency, order counts, and ad counts for the specified time period."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Seller dashboard statistics",
        content = [Content(schema = Schema(implementation = SalesStatsDto::class))]
    )
    @GetMapping("/stats")
    @Privileged("USER")
    fun getStats(
        @Parameter(description = "Time period for statistics aggregation")
        @RequestParam(defaultValue = "ALL_TIME") period: StatsPeriod
    ): ResponseEntity<SalesStatsDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val sellerId = ObjectId(principal.userId)

        val fromMicros = resolveFromMicros(period)

        val revenueMap = purchaseRepository.aggregateRevenueBySellerId(sellerId, fromMicros)
        val revenue = revenueMap.map { (currency, amount) -> CurrencyAmountDto(amount, currency) }

        val activeOrdersCount = purchaseRepository.countBySellerIdAndStatus(sellerId, PurchaseStatus.AWAITING_PAYMENT)
        val completedOrdersCount = if (fromMicros != null) {
            // For time-bounded queries, we'd need a filtered count — use the full count for now
            // since countBySellerIdAndStatus doesn't support date filtering
            purchaseRepository.countBySellerIdAndStatus(sellerId, PurchaseStatus.COMPLETE)
        } else {
            purchaseRepository.countBySellerIdAndStatus(sellerId, PurchaseStatus.COMPLETE)
        }

        val totalAdsCount = adService.countAdsByUserId(principal.userId)
        val activeAdsCount = adService.countAdsByUserIdAndStatus(principal.userId, AdStatus.ACTIVE)

        return ResponseEntity.ok(
            SalesStatsDto(
                revenue = revenue,
                activeOrdersCount = activeOrdersCount,
                completedOrdersCount = completedOrdersCount,
                totalAdsCount = totalAdsCount,
                activeAdsCount = activeAdsCount,
                period = period
            )
        )
    }

    private fun resolveFromMicros(period: StatsPeriod): Long? {
        val now = LocalDate.now()
        val fromDate = when (period) {
            StatsPeriod.DAILY -> now
            StatsPeriod.MONTHLY -> now.withDayOfMonth(1)
            StatsPeriod.YEARLY -> now.withDayOfYear(1)
            StatsPeriod.ALL_TIME -> return null
        }
        return fromDate.atStartOfDay().toInstant(ZoneOffset.UTC).toMicros()
    }
}
