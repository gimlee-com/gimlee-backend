package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.orders.domain.OrderService
import com.gimlee.orders.web.dto.request.PlaceOrderRequestDto
import com.gimlee.payments.domain.PaymentService
import com.gimlee.api.web.dto.OrderResponseDto
import com.gimlee.api.web.dto.PaymentDetailsDto
import com.gimlee.api.web.dto.OrderStatusResponseDto
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

@Tag(name = "Orders", description = "Endpoints for placing and tracking orders")
@RestController
@RequestMapping("/orders")
class OrderFacadeController(
    private val orderService: OrderService,
    private val paymentService: PaymentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "Place a New Order",
        description = "This endpoint allows an authenticated user to place an order for a specific advertisement. It acts as a facade, coordinating between OrderService and PaymentService."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Order placed successfully",
        content = [Content(schema = Schema(implementation = OrderResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "Ad not found, stock is insufficient, or seller lacks a wallet")
    @ApiResponse(responseCode = "409", description = "Price or currency does not match the actual Ad price")
    @PostMapping
    @Privileged(role = "USER")
    fun placeOrder(@Valid @RequestBody request: PlaceOrderRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} placing order via facade for ad {}", principal.userId, request.adId)

        return try {
            val order = orderService.placeOrder(
                buyerId = ObjectId(principal.userId),
                adId = ObjectId(request.adId),
                amount = request.amount,
                currency = request.currency
            )
            
            val payment = paymentService.getPaymentByOrderId(order.id)
            
            val response = OrderResponseDto(
                orderId = order.id.toHexString(),
                status = order.status.name,
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
        } catch (e: OrderService.AdPriceMismatchException) {
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
            log.warn("Invalid order request for ad {} by user {}: {}", request.adId, principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            log.warn("Order placement state error for ad {} by user {}: {}", request.adId, principal.userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("Error placing order for user {}: {}", principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while placing the order."))
        }
    }

    @Operation(
        summary = "Get Order and Payment Status",
        description = "Poll this endpoint to check the current status of an order and its associated payment."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current order status",
        content = [Content(schema = Schema(implementation = OrderStatusResponseDto::class))]
    )
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "403", description = "User is neither the buyer nor the seller")
    @GetMapping("/{orderId}/status")
    @Privileged(role = "USER")
    fun getOrderStatus(
        @Parameter(description = "Unique ID of the order")
        @PathVariable orderId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val order = orderService.getOrder(ObjectId(orderId))
            ?: return ResponseEntity.notFound().build()
        
        if (order.buyerId.toHexString() != principal.userId && order.sellerId.toHexString() != principal.userId) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val payment = paymentService.getPaymentByOrderId(order.id)
        
        val response = OrderStatusResponseDto(
            orderId = order.id.toHexString(),
            status = order.status.name,
            paymentStatus = payment?.status?.name
        )
        
        return ResponseEntity.ok(response)
    }
}
