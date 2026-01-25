package com.gimlee.payments.crypto.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.crypto.piratechain.domain.PirateChainAddressService
import com.gimlee.payments.crypto.piratechain.domain.PirateChainPaymentService
import com.gimlee.payments.crypto.ycash.client.YcashRpcClient
import com.gimlee.payments.crypto.ycash.domain.YcashAddressService
import com.gimlee.payments.crypto.ycash.domain.YcashPaymentService
import com.gimlee.payments.crypto.web.dto.AddViewKeyRequest
import com.gimlee.payments.crypto.web.dto.CryptoTransactionDto
import com.gimlee.payments.domain.PaymentOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@Tag(name = "Payments - Crypto", description = "Endpoints for cryptocurrency payment integration")
@RestController
class CryptoPaymentsController(
    private val pirateChainAddressService: PirateChainAddressService,
    private val ycashAddressService: YcashAddressService,
    private val pirateChainPaymentService: PirateChainPaymentService,
    private val ycashPaymentService: YcashPaymentService,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    private fun getAddressService(crypto: String) = when(crypto.lowercase()) {
        "piratechain" -> pirateChainAddressService
        "ycash" -> ycashAddressService
        else -> throw IllegalArgumentException("Unsupported cryptocurrency: $crypto")
    }

    private fun getPaymentService(crypto: String) = when(crypto.lowercase()) {
        "piratechain" -> pirateChainPaymentService
        "ycash" -> ycashPaymentService
        else -> throw IllegalArgumentException("Unsupported cryptocurrency: $crypto")
    }

    @Operation(
        summary = "Add a Cryptocurrency View Key",
        description = "Associates a viewing key with the authenticated user's account for the specified cryptocurrency. This allows the system to monitor incoming transactions for this user."
    )
    @ApiResponse(
        responseCode = "200",
        description = "View key added successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid view key or state error. Possible status codes: PAYMENT_INVALID_PAYMENT_DATA, PAYMENT_INVALID_VIEWING_KEY",
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
    @PostMapping("/payments/{crypto}/addresses/view-key")
    @Privileged(role = "USER")
    fun addViewKey(
        @PathVariable crypto: String,
        @Valid @RequestBody request: AddViewKeyRequest
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        log.info("Received request to add {} view key for user ID: {}", crypto, userId)
        return try {
            val addressService = getAddressService(crypto)
            addressService.importAndAssociateViewKey(
                userId = userId,
                viewKey = request.viewKey
            )
            handleOutcome(CommonOutcome.SUCCESS)
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid crypto requested: {}", crypto)
            handleOutcome(PaymentOutcome.INVALID_PAYMENT_DATA)
        } catch (e: IllegalStateException) {
            log.warn("State error during addViewKey for user {} ({}): {}", userId, crypto, e.message)
            handleOutcome(PaymentOutcome.INVALID_PAYMENT_DATA)
        } catch (e: PirateChainRpcClient.PirateChainRpcException) {
            log.warn("Pirate Chain RPC error during addViewKey for user {} ({}): {}", userId, crypto, e.message)
            if (e.errorCode == -5) {
                handleOutcome(PaymentOutcome.INVALID_VIEWING_KEY)
            } else {
                handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
            }
        } catch (e: YcashRpcClient.YcashRpcException) {
            log.warn("Ycash RPC error during addViewKey for user {} ({}): {}", userId, crypto, e.message)
            if (e.errorCode == -5) {
                handleOutcome(PaymentOutcome.INVALID_VIEWING_KEY)
            } else {
                handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
            }
        } catch (e: Exception) {
            log.error("Error during addViewKey for user {} ({}): {}", userId, crypto, e.message, e)
            // Determine if it was a communication error (this is a bit loose but maintains original logic)
            if (e.message?.contains("RPC", ignoreCase = true) == true || e.cause?.message?.contains("RPC", ignoreCase = true) == true) {
                handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
            } else {
                handleOutcome(CommonOutcome.INTERNAL_ERROR)
            }
        }
    }


    @Operation(
        summary = "Get User's Cryptocurrency Transactions",
        description = "Retrieves all incoming transactions for the specified cryptocurrency for the authenticated user. This currently requires the user to have the ADMIN role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of transactions",
        content = [Content(array = ArraySchema(schema = Schema(implementation = CryptoTransactionDto::class)))]
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
    @GetMapping("/payments/{crypto}/transactions")
    @Privileged(role = "ADMIN")
    fun getUserTransactions(@PathVariable crypto: String): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        log.debug("Received request to fetch {} transactions for user ID: {}", crypto, userId)

        return try {
            val paymentService = getPaymentService(crypto)
            val transactions = paymentService.getUserTransactions(userId)
            ResponseEntity.ok(transactions)
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid crypto requested: {}", crypto)
            handleOutcome(PaymentOutcome.INVALID_PAYMENT_DATA)
        } catch (e: Exception) {
            log.error("Error during getUserTransactions for user {} ({}): {}", userId, crypto, e.message, e)
            if (e.message?.contains("RPC", ignoreCase = true) == true || e.cause?.message?.contains("RPC", ignoreCase = true) == true) {
                handleOutcome(PaymentOutcome.NODE_COMMUNICATION_ERROR)
            } else {
                handleOutcome(CommonOutcome.INTERNAL_ERROR)
            }
        }
    }
}
