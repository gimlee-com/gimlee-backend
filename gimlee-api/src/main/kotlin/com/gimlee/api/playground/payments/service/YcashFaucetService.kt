package com.gimlee.api.playground.payments.service

import com.gimlee.payments.ycash.client.YcashRpcClient
import com.gimlee.payments.ycash.client.model.ZSendManyAmount
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
        val response = ycashRpcClient.listAddressGroupings()
        // result is List<List<List<Any>>> -> [group][address_info][address, balance, account]
        return response.result?.flatten()
            ?.filter { it.size >= 2 && (it[1] as? Number)?.toDouble() ?: 0.0 > 0.0 }
            ?.mapNotNull { it[0] as? String }
            ?.firstOrNull()
    }
}
