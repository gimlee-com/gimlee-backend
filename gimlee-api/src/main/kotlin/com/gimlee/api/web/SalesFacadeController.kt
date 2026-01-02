package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.api.web.dto.*
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.payments.domain.PaymentService
import com.gimlee.purchases.domain.PurchaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
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
    private val paymentService: PaymentService
) {
    companion object {
        private const val PAGE_SIZE = 60
    }

    @Operation(summary = "Fetch My Orders", description = "Fetches orders for the authenticated seller.")
    @ApiResponse(responseCode = "200", description = "Paged list of orders")
    @GetMapping("/")
    @Privileged("USER")
    fun getMyOrders(
        @RequestParam(name = "p", defaultValue = "0") page: Int
    ): Page<SalesOrderDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val sellerId = ObjectId(principal.userId)
        
        val purchasesPage = purchaseService.getPurchasesForSeller(sellerId, PageRequest.of(page, PAGE_SIZE))
        
        val purchases = purchasesPage.content
        if (purchases.isEmpty()) {
            return Page.empty(purchasesPage.pageable)
        }

        val adIds = purchases.flatMap { it.items.map { item -> item.adId.toHexString() } }.distinct()
        val buyerIds = purchases.map { it.buyerId.toHexString() }.distinct()

        val adsMap = adService.getAds(adIds).associateBy { it.id }
        val usernamesMap = userService.findUsernamesByIds(buyerIds)
        val paymentsMap = purchases.associate { it.id to paymentService.getPaymentByPurchaseId(it.id) }

        return purchasesPage.map { purchase ->
            SalesOrderDto(
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
                buyer = BuyerInfoDto(
                    id = purchase.buyerId.toHexString(),
                    username = usernamesMap[purchase.buyerId.toHexString()] ?: "Unknown"
                )
            )
        }
    }

    @Operation(summary = "Fetch Single Order", description = "Fetches full details for a specific order owned by the seller.")
    @ApiResponse(responseCode = "200", description = "Detailed order information")
    @ApiResponse(responseCode = "404", description = "Order not found or not owned by the seller")
    @GetMapping("/{purchaseId}")
    @Privileged("USER")
    fun getOrder(
        @Parameter(description = "Unique ID of the purchase")
        @PathVariable purchaseId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val purchase = purchaseService.getPurchase(ObjectId(purchaseId))

        if (purchase == null || purchase.sellerId.toHexString() != principal.userId) {
            return ResponseEntity.status(404).body(mapOf("error" to "Order not found."))
        }

        val adIds = purchase.items.map { it.adId.toHexString() }.distinct()
        val adsMap = adService.getAds(adIds).associateBy { it.id }
        val buyer = userService.findById(purchase.buyerId.toHexString())
        val payment = paymentService.getPaymentByPurchaseId(purchase.id)

        val dto = SalesOrderDto(
            id = purchase.id.toHexString(),
            status = purchase.status.name,
            paymentStatus = payment?.status?.name,
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
            buyer = BuyerInfoDto(
                id = purchase.buyerId.toHexString(),
                username = buyer?.username ?: "Unknown"
            )
        )
        return ResponseEntity.ok(dto)
    }
}
