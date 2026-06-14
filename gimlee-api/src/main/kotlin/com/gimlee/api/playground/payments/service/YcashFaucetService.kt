package com.gimlee.api.playground.payments.service

import com.gimlee.payments.crypto.ycash.client.YcashRpcClient
import com.gimlee.payments.crypto.ycash.client.model.ZSendManyAmount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class YcashFaucetService(
    private val ycashRpcClient: YcashRpcClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCoins(address: String, amount: BigDecimal): String {
        log.info("Sending {} YEC to {}", amount, address)

        // For regtest, shielding directly to the address is a reliable way to distribute funds
        if (address.startsWith("y")) {
            try {
                log.info("Attempting direct shielding to {}", address)
                val shieldResponse = ycashRpcClient.zShieldCoinbase("*", address)
                val opId = shieldResponse.result?.opid
                if (opId != null) {
                    log.info("Direct shielding initiated to {}, operation ID: {}", address, opId)
                    return opId
                }
            } catch (e: Exception) {
                log.warn("Direct shielding failed, falling back to z_sendmany: {}", e.message)
            }
        }

        val fromAddress = findFromAddress() ?: throw RuntimeException("No source address with funds found in Ycash wallet")

        val response = ycashRpcClient.zSendMany(
            fromAddress = fromAddress,
            amounts = listOf(ZSendManyAmount(address = address, amount = amount)),
            minConf = 1
        )

        val operationId = response.result ?: throw RuntimeException("Failed to initiate z_sendmany: ${response.error}")
        log.info("z_sendmany initiated from {} to {}, operation ID: {}", fromAddress, address, operationId)
        return operationId
    }

    private fun findFromAddress(): String? {
        // We look for any UTXO that is spendable (min 0 confirmation).
        // listUnspent automatically handles coinbase maturity (funds won't appear here until they have 100 confirmations).
        val response = ycashRpcClient.listUnspent(minConfs = 0)

        return response.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            // We pick the address with the largest single UTXO to avoid fragmentation,
            // but you could also just pick the first one.
            ?.maxByOrNull { it.amount }
            ?.address
    }
}
