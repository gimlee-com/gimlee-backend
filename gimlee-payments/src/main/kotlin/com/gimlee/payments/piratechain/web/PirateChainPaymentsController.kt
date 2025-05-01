package com.gimlee.payments.piratechain.web

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.domain.PirateChainAddressService
import com.gimlee.payments.piratechain.domain.PirateChainPaymentService
import com.gimlee.payments.piratechain.web.dto.AddViewKeyRequest

@RestController
class PirateChainPaymentsController(
    private val pirateChainAddressService: PirateChainAddressService,
    private val pirateChainPaymentService: PirateChainPaymentService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Endpoint for authenticated users to add a Pirate Chain viewing key.
     * The system will attempt to import the key via RPC and associate
     * the derived z-address with the user's account.
     */
    @PostMapping("/payments/piratechain/addresses/view-key")
    @Privileged(role = "USER")
    fun addViewKey(@Valid @RequestBody request: AddViewKeyRequest): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        log.info("Received request to add view key for user ID: {}", userId)
        return try {
            pirateChainAddressService.importAndAssociateViewKey(
                userId = userId,
                viewKey = request.viewKey
            )
            ResponseEntity.ok().build()
        } catch (e: PirateChainRpcClient.PirateChainRpcException) {
            log.warn("RPC failed during addViewKey for user {}: {}", userId, e.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to communicate with the Pirate Chain node. ${e.message}"))
        } catch (e: IllegalStateException) {
            log.warn("State error during addViewKey for user {}: {}", userId, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("Unexpected error during addViewKey for user {}: {}", userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while processing the viewing key."))
        }
    }


    @GetMapping("/payments/piratechain/transactions")
    @Privileged(role = "ADMIN")
    fun getUserTransactions(): ResponseEntity<Any> { // Use ResponseEntity<Any> for flexible success/error responses
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        log.info("Received request to fetch transactions for user ID: {}", userId)

        return try {
            val transactions = pirateChainPaymentService.getUserTransactions(userId)
            ResponseEntity.ok(transactions)
        } catch (e: PirateChainRpcClient.PirateChainRpcException) {
            log.warn("RPC failed during getUserTransactions for user {}: {}", userId, e.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to communicate with the Pirate Chain node. ${e.message}"))
        } catch (e: RuntimeException) {
            log.error("Runtime error during getUserTransactions for user {}: {}", userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "An unexpected error occurred while fetching transactions.")))
        } catch (e: Exception) {
            log.error("Unexpected error during getUserTransactions for user {}: {}", userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while fetching transactions."))
        }
    }
}