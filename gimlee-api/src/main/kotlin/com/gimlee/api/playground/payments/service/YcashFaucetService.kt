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

        val fromAddress = findFromAddress() ?: throw RuntimeException("No source address with funds found in Ycash wallet")

        val response = ycashRpcClient.zSendMany(
            fromAddress = fromAddress,
            amounts = listOf(ZSendManyAmount(address = address, amount = amount))
        )

        val operationId = response.result ?: throw RuntimeException("Failed to initiate z_sendmany: ${response.error}")
        log.info("z_sendmany initiated from {} to {}, operation ID: {}", fromAddress, address, operationId)
        return operationId
    }

    private fun findFromAddress(): String? {
        // We look for any UTXO that is spendable (min 1 confirmation).
        // listUnspent automatically handles coinbase maturity (funds won't appear here until they have 100 confirmations).
        val response = ycashRpcClient.listUnspent()

        return response.result
            ?.filter { it.spendable && it.amount > BigDecimal.ZERO }
            // We pick the address with the largest single UTXO to avoid fragmentation,
            // but you could also just pick the first one.
            ?.maxByOrNull { it.amount }
            ?.address
    }
}
