package com.gimlee.orders.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.orders.domain.OrderService
import com.gimlee.orders.web.dto.request.PlaceOrderRequestDto
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @Privileged(role = "USER")
    fun placeOrder(@Valid @RequestBody request: PlaceOrderRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} attempting to place order for ad {}", principal.userId, request.adId)

        return try {
            val order = orderService.placeOrder(
                buyerId = ObjectId(principal.userId),
                adId = ObjectId(request.adId),
                amount = request.amount
            )
            ResponseEntity.status(HttpStatus.CREATED).body(order)
        } catch (e: Exception) {
            log.error("Error placing order for user {}: {}", principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }
}
