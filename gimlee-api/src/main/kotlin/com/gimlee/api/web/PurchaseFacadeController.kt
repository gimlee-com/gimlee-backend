package com.gimlee.api.web

import com.gimlee.api.web.dto.*
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.web.dto.request.PurchaseRequestDto
import com.gimlee.payments.domain.PaymentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Purchases", description = "Endpoints for placing and tracking purchases")
@RestController
@RequestMapping("/purchases")
class PurchaseFacadeController(
    private val purchaseService: PurchaseService,
    private val paymentService: PaymentService,
    private val userService: UserService,
    private val adService: com.gimlee.ads.domain.AdService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PAGE_SIZE = 60
    }

    @Operation(
        summary = "Create a New Purchase",
        description = "This endpoint allows an authenticated user to make a purchase. It acts as a facade, coordinating between PurchaseService and PaymentService."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Purchase created successfully",
        content = [Content(schema = Schema(implementation = PurchaseResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "Ad not found, stock is insufficient, or seller lacks a wallet")
    @ApiResponse(responseCode = "409", description = "Price or currency does not match the actual Ad price")
    @PostMapping
    @Privileged(role = "USER")
    fun purchase(@Valid @RequestBody request: PurchaseRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} making a purchase via facade for {} items", principal.userId, request.items.size)

        return try {
            val purchase = purchaseService.purchase(
                buyerId = ObjectId(principal.userId),
                items = request.items,
                currency = request.currency
            )
            
            val payment = paymentService.getPaymentByPurchaseId(purchase.id)
            
            val response = PurchaseResponseDto(
                purchaseId = purchase.id.toHexString(),
                status = purchase.status.name,
                payment = payment?.let {
                    PaymentDetailsDto(
                        address = it.receivingAddress,
                        amount = it.amount,
                        memo = it.memo,
                        qrCodeUri = "pirate:${it.receivingAddress}?amount=${it.amount}"
                    )
                }
            )
            
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: PurchaseService.AdPriceMismatchException) {
            log.warn("Price mismatch: {}", e.message)
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to "PRICE_MISMATCH",
                "message" to "The price of one or more items has changed.",
                "currentPrices" to e.currentPrices.mapValues { (_, amount) ->
                    mapOf(
                        "amount" to amount.amount,
                        "currency" to amount.currency
                    )
                }
            ))
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid purchase request by user {}: {}", principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            log.warn("Purchase creation state error by user {}: {}", principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("Error initializing a purchase for user {}: {}", principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while making the purchase."))
        }
    }

    @Operation(
        summary = "Get My Purchases",
        description = "Fetches purchase history for the authenticated buyer."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of purchases")
    @GetMapping("/")
    @Privileged(role = "USER")
    fun getMyPurchases(
        @RequestParam(name = "p", defaultValue = "0") page: Int
    ): Page<PurchaseHistoryDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val buyerId = ObjectId(principal.userId)

        val purchasesPage = purchaseService.getPurchasesForBuyer(buyerId, PageRequest.of(page, PAGE_SIZE))

        val purchases = purchasesPage.content
        if (purchases.isEmpty()) {
            return Page.empty(purchasesPage.pageable)
        }

        val adIds = purchases.flatMap { it.items.map { item -> item.adId.toHexString() } }.distinct()
        val sellerIds = purchases.map { it.sellerId.toHexString() }.distinct()

        val adsMap = adService.getAds(adIds).associateBy { it.id }
        val usernamesMap = userService.findUsernamesByIds(sellerIds)
        val paymentsMap = purchases.associate { it.id to paymentService.getPaymentByPurchaseId(it.id) }

        return purchasesPage.map { purchase ->
            PurchaseHistoryDto(
                id = purchase.id.toHexString(),
                status = purchase.status.name,
                paymentStatus = paymentsMap[purchase.id]?.status?.name,
                createdAt = purchase.createdAt,
                totalAmount = purchase.totalAmount,
                currency = purchase.items.firstOrNull()?.currency?.name ?: "UNKNOWN",
                items = purchase.items.map { item ->
                    SalesOrderItemDto(
                        adId = item.adId.toHexString(),
                        title = adsMap[item.adId.toHexString()]?.title ?: "Unknown Ad",
                        quantity = item.quantity,
                        unitPrice = item.unitPrice
                    )
                },
                seller = SellerInfoDto(
                    id = purchase.sellerId.toHexString(),
                    username = usernamesMap[purchase.sellerId.toHexString()] ?: "Unknown"
                )
            )
        }
    }

    @Operation(
        summary = "Get Purchase and Payment Status",
        description = "Poll this endpoint to check the current status of a purchase and its associated payment."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current purchase status",
        content = [Content(schema = Schema(implementation = PurchaseStatusResponseDto::class))]
    )
    @ApiResponse(responseCode = "404", description = "Purchase not found")
    @ApiResponse(responseCode = "403", description = "User is neither the buyer nor the seller")
    @GetMapping("/{purchaseId}/status")
    @Privileged(role = "USER")
    fun getPurchaseStatus(
        @Parameter(description = "Unique ID of the purchase")
        @PathVariable purchaseId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val purchase = purchaseService.getPurchase(ObjectId(purchaseId))
            ?: return ResponseEntity.notFound().build()
        
        if (purchase.buyerId.toHexString() != principal.userId && purchase.sellerId.toHexString() != principal.userId) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val payment = paymentService.getPaymentByPurchaseId(purchase.id)
        
        val response = PurchaseStatusResponseDto(
            purchaseId = purchase.id.toHexString(),
            status = purchase.status.name,
            paymentStatus = payment?.status?.name
        )
        
        return ResponseEntity.ok(response)
    }
}
