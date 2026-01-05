package com.gimlee.payments.piratechain.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
import com.gimlee.payments.piratechain.web.dto.PirateChainTransactionDto
import com.gimlee.payments.domain.PaymentOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@Tag(name = "Payments - Pirate Chain", description = "Endpoints for Pirate Chain payment integration")
@RestController
class PirateChainPaymentsController(
    private val pirateChainAddressService: PirateChainAddressService,
    private val pirateChainPaymentService: PirateChainPaymentService,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Add a Pirate Chain View Key",
        description = "Associates a Pirate Chain viewing key with the authenticated user's account. This allows the system to monitor incoming transactions for this user. The system will attempt to import the key into the Pirate Chain node."
    )
    @ApiResponse(
        responseCode = "200",
        description = "View key added successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid view key or state error. Possible status codes: PAYMENT_INVALID_PAYMENT_DATA",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "503",
        description = "RPC node communication failure. Possible status codes: PAYMENT_NODE_COMMUNICATION_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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
            handleOutcome(CommonOutcome.SUCCESS)
        } catch (e: PirateChainRpcClient.PirateChainRpcException) {
            log.warn("RPC failed during addViewKey for user {}: {}", userId, e.message)
            handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
        } catch (e: IllegalStateException) {
            log.warn("State error during addViewKey for user {}: {}", userId, e.message)
            handleOutcome(PaymentOutcome.INVALID_PAYMENT_DATA)
        } catch (e: Exception) {
            log.error("Unexpected error during addViewKey for user {}: {}", userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }


    @Operation(
        summary = "Get User's Pirate Chain Transactions",
        description = "Retrieves all incoming Pirate Chain transactions for the authenticated user. This currently requires the user to have the ADMIN role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of Pirate Chain transactions",
        content = [Content(array = ArraySchema(schema = Schema(implementation = PirateChainTransactionDto::class)))]
    )
    @ApiResponse(
        responseCode = "503",
        description = "RPC node communication failure. Possible status codes: PAYMENT_NODE_COMMUNICATION_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/payments/piratechain/transactions")
    @Privileged(role = "ADMIN")
    fun getUserTransactions(): ResponseEntity<Any> { // Use ResponseEntity<Any> for flexible success/error responses
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        log.debug("Received request to fetch transactions for user ID: {}", userId)

        return try {
            val transactions = pirateChainPaymentService.getUserTransactions(userId)
            ResponseEntity.ok(transactions)
        } catch (e: PirateChainRpcClient.PirateChainRpcException) {
            log.warn("RPC failed during getUserTransactions for user {}: {}", userId, e.message)
            handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
        } catch (e: RuntimeException) {
            log.error("Runtime error during getUserTransactions for user {}: {}", userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        } catch (e: Exception) {
            log.error("Unexpected error during getUserTransactions for user {}: {}", userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }
}