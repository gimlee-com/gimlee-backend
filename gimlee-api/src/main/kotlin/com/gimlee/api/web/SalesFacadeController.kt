package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.api.service.UserSummaryAssembler
import com.gimlee.api.web.dto.*
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.toMicros
import com.gimlee.payments.domain.PaymentService
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.domain.PurchaseOutcome
import com.gimlee.purchases.domain.model.PurchaseFilters
import com.gimlee.purchases.domain.model.PurchaseSorting
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.springdoc.core.annotations.ParameterObject
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Sales", description = "Endpoints for sellers to manage their orders")
@RestController
@RequestMapping("/sales/orders")
class SalesFacadeController(
    private val purchaseService: PurchaseService,
    private val adService: AdService,
    private val userService: UserService,
    private val userSummaryAssembler: UserSummaryAssembler,
    private val paymentService: PaymentService,
    private val messageSource: MessageSource
) {
    companion object {
        private const val PAGE_SIZE = 60
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Fetch My Orders",
        description = "Fetches orders for the authenticated seller. Supports filtering by status, " +
                "date range, ad ID, and buyer username search. Supports sorting by date or amount."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of orders")
    @GetMapping("/")
    @Privileged("USER")
    fun getMyOrders(@Valid @ParameterObject request: SalesOrdersRequestDto): Page<SalesOrderDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val sellerId = ObjectId(principal.userId)

        val filters = buildPurchaseFilters(request.status, request.q, request.adId, request.from, request.to) { query ->
            resolveUserIds(query)
        }
        val sorting = PurchaseSorting(by = request.by, direction = request.dir)

        val purchasesPage = purchaseService.getPurchasesForSeller(
            sellerId, filters, sorting, PageRequest.of(request.p, PAGE_SIZE)
        )

        val purchases = purchasesPage.content
        if (purchases.isEmpty()) {
            return PageImpl(emptyList(), purchasesPage.pageable, purchasesPage.totalElements)
        }

        val adIds = purchases.flatMap { it.items.map { item -> item.adId.toHexString() } }.distinct()
        val buyerIds = purchases.map { it.buyerId.toHexString() }.distinct()

        val adsMap = adService.getAds(adIds).associateBy { it.id }
        val buyerSummaries = userSummaryAssembler.assemble(buyerIds)
        val paymentsMap = purchases.associate { it.id to paymentService.getPaymentByPurchaseId(it.id) }

        return purchasesPage.map { purchase ->
            val firstItem = purchase.items.firstOrNull()
            val firstAd = firstItem?.let { adsMap[it.adId.toHexString()] }
            SalesOrderDto(
                id = purchase.id.toHexString(),
                status = purchase.status.name,
                paymentStatus = paymentsMap[purchase.id]?.status?.name,
                createdAt = purchase.createdAt,
                totalAmount = purchase.totalAmount,
                currency = firstItem?.currency?.name ?: "UNKNOWN",
                items = purchase.items.map { item ->
                    SalesOrderItemDto(
                        adId = item.adId.toHexString(),
                        title = adsMap[item.adId.toHexString()]?.title ?: "Unknown Ad",
                        quantity = item.quantity,
                        unitPrice = item.unitPrice
                    )
                },
                buyer = buyerSummaries[purchase.buyerId.toHexString()]
                    ?: UserSummaryDto(username = "Unknown", avatarUrl = null),
                primaryThumbnailPath = firstAd?.mainPhotoPath,
                itemCount = purchase.items.size,
                deliveryAddress = purchase.deliveryAddress?.let {
                    DeliveryAddressSnapshotDto(
                        name = it.name,
                        fullName = it.fullName,
                        street = it.street,
                        city = it.city,
                        postalCode = it.postalCode,
                        country = it.country,
                        phoneNumber = it.phoneNumber
                    )
                }
            )
        }
    }

    @Operation(summary = "Fetch Single Order", description = "Fetches full details for a specific order owned by the seller.")
    @ApiResponse(
        responseCode = "200",
        description = "Detailed order information",
        content = [Content(schema = Schema(implementation = SalesOrderDetailDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Order not found or not owned by the seller. Possible status codes: PURCHASE_ORDER_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{purchaseId}")
    @Privileged("USER")
    fun getOrder(
        @Parameter(description = "Unique ID of the purchase")
        @PathVariable purchaseId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val purchase = purchaseService.getPurchase(ObjectId(purchaseId))

        if (purchase == null || purchase.sellerId.toHexString() != principal.userId) {
            return handleOutcome(PurchaseOutcome.ORDER_NOT_FOUND)
        }

        val adIds = purchase.items.map { it.adId.toHexString() }.distinct()
        val adsMap = adService.getAds(adIds).associateBy { it.id }
        val buyerSummary = userSummaryAssembler.assemble(listOf(purchase.buyerId.toHexString()))
        val payment = paymentService.getPaymentByPurchaseId(purchase.id)
        val cryptoTransactions = paymentService.getTransactionsByPurchaseId(purchase.id)

        val dto = SalesOrderDetailDto(
            id = purchase.id.toHexString(),
            buyer = buyerSummary[purchase.buyerId.toHexString()]
                ?: UserSummaryDto(username = "Unknown", avatarUrl = null),
            items = purchase.items.map { item ->
                val ad = adsMap[item.adId.toHexString()]
                OrderItemDetailDto(
                    adId = item.adId.toHexString(),
                    title = ad?.title ?: "Unknown Ad",
                    thumbnailPath = ad?.mainPhotoPath,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice
                )
            },
            totalAmount = purchase.totalAmount,
            currency = purchase.items.firstOrNull()?.currency?.name ?: "UNKNOWN",
            status = purchase.status.name,
            paymentStatus = payment?.status?.name,
            deliveryAddress = purchase.deliveryAddress?.let {
                DeliveryAddressSnapshotDto(
                    name = it.name,
                    fullName = it.fullName,
                    street = it.street,
                    city = it.city,
                    postalCode = it.postalCode,
                    country = it.country,
                    phoneNumber = it.phoneNumber
                )
            },
            statusHistory = purchase.statusHistory.map {
                StatusChangeDto(status = it.status.name, timestamp = it.timestamp)
            },
            cryptoTransactions = cryptoTransactions,
            createdAt = purchase.createdAt
        )
        return ResponseEntity.ok(dto)
    }

    private fun buildPurchaseFilters(
        statuses: List<com.gimlee.purchases.domain.model.PurchaseStatus>?,
        query: String?,
        adId: String?,
        from: java.time.Instant?,
        to: java.time.Instant?,
        resolveCounterpartyIds: (String) -> List<ObjectId>
    ): PurchaseFilters {
        var buyerIds: List<ObjectId>? = null
        var purchaseId: ObjectId? = null

        if (!query.isNullOrBlank()) {
            if (isObjectIdHex(query)) {
                purchaseId = ObjectId(query)
            } else {
                val resolved = resolveCounterpartyIds(query)
                if (resolved.isEmpty()) {
                    return PurchaseFilters(noResults = true)
                }
                buyerIds = resolved
            }
        }

        val parsedAdId = adId?.let {
            runCatching { ObjectId(it) }.getOrElse { return PurchaseFilters(noResults = true) }
        }

        return PurchaseFilters(
            statuses = statuses,
            purchaseId = purchaseId,
            adId = parsedAdId,
            fromMicros = from?.toMicros(),
            toMicros = to?.toMicros(),
            buyerIds = buyerIds
        )
    }

    private fun resolveUserIds(query: String): List<ObjectId> {
        val users = userService.searchByUsernameContaining(query)
        return users.mapNotNull { it.id }
    }

    private fun isObjectIdHex(value: String): Boolean {
        return value.length == 24 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}
