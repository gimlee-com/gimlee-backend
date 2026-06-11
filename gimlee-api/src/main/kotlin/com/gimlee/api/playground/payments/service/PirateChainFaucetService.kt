package com.gimlee.api.playground.payments.service

import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.crypto.piratechain.client.model.ZSendManyAmount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PirateChainFaucetService(
    private val pirateChainRpcClient: PirateChainRpcClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCoins(address: String, amount: BigDecimal): String {
        log.info("Sending {} ARRR to {}", amount, address)

        val fromAddress = findFromAddress() ?: throw RuntimeException("No source address with funds found in PirateChain wallet")

        val response = pirateChainRpcClient.zSendMany(
            fromAddress = fromAddress,
            amounts = listOf(ZSendManyAmount(address = address, amount = amount))
        )

        val operationId = response.result ?: throw RuntimeException("Failed to initiate z_sendmany: ${response.error}")
        log.info("z_sendmany initiated from {} to {}, operation ID: {}", fromAddress, address, operationId)
        return operationId
    }

    private fun findFromAddress(): String? {
        // First try to find shielded funds
        val shieldedResponse = pirateChainRpcClient.zListUnspent()
        val shieldedAddress = shieldedResponse.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            ?.maxByOrNull { it.amount }
            ?.address

        if (shieldedAddress != null) {
            return shieldedAddress
        }

        // Fallback to transparent funds (might fail in ARRR if strictly shielded)
        val response = pirateChainRpcClient.listUnspent()

        return response.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            ?.maxByOrNull { it.amount }
            ?.address
    }
}
