package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.web.dto.request.PurchaseRequestDto
import com.gimlee.payments.domain.PaymentService
import com.gimlee.api.web.dto.PurchaseResponseDto
import com.gimlee.api.web.dto.PaymentDetailsDto
import com.gimlee.api.web.dto.PurchaseStatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Purchases", description = "Endpoints for placing and tracking purchases")
@RestController
@RequestMapping("/purchases")
class PurchaseFacadeController(
    private val purchaseService: PurchaseService,
    private val paymentService: PaymentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        log.info("User {} making a purchase via facade for ad {}", principal.userId, request.adId)

        return try {
            val purchase = purchaseService.purchase(
                buyerId = ObjectId(principal.userId),
                adId = ObjectId(request.adId),
                amount = request.amount,
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
            log.warn("Price mismatch for ad {}: {}", request.adId, e.message)
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to "PRICE_MISMATCH",
                "message" to "The price of this item has changed.",
                "currentPrice" to mapOf(
                    "amount" to e.currentPrice.amount,
                    "currency" to e.currentPrice.currency
                )
            ))
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid purchase request for ad {} by user {}: {}", request.adId, principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            log.warn("Purchase creation state error for ad {} by user {}: {}", request.adId, principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("Error initializing a purchase for user {}: {}", principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while making the purchase."))
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
