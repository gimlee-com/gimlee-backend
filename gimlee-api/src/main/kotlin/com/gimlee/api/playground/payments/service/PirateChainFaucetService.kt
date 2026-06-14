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

        // For regtest, shielding directly to the address is the most reliable way to distribute funds
        // especially when the wallet's existing shielded funds might be in a bad state.
        if (address.startsWith("z")) {
            try {
                log.info("Attempting direct shielding to {}", address)
                val shieldResponse = pirateChainRpcClient.zShieldCoinbase("*", address)
                val opId = shieldResponse.result?.opid
                if (opId != null) {
                    log.info("Direct shielding initiated to {}, operation ID: {}", address, opId)
                    return opId
                }
            } catch (e: Exception) {
                log.warn("Direct shielding failed, falling back to z_sendmany: {}", e.message)
            }
        }

        val fromAddress = findFromAddress() ?: throw RuntimeException("No source address with funds found in PirateChain wallet")

        val response = pirateChainRpcClient.zSendMany(
            fromAddress = fromAddress,
            amounts = listOf(ZSendManyAmount(address = address, amount = amount)),
            minConf = 1
        )

        val operationId = response.result ?: throw RuntimeException("Failed to initiate z_sendmany: ${response.error}")
        log.info("z_sendmany initiated from {} to {}, operation ID: {}", fromAddress, address, operationId)
        return operationId
    }

    private fun findFromAddress(): String? {
        // First try to find shielded funds
        val shieldedResponse = pirateChainRpcClient.zListUnspent(minConfs = 0)
        val shieldedAddress = shieldedResponse.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            ?.maxByOrNull { it.amount }
            ?.address

        if (shieldedAddress != null) {
            return shieldedAddress
        }

        // Fallback to transparent funds (might fail in ARRR if strictly shielded)
        val response = pirateChainRpcClient.listUnspent(minConfs = 0)

        return response.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            ?.maxByOrNull { it.amount }
            ?.address
    }
}
