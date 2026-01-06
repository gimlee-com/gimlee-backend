package com.gimlee.payments.crypto.piratechain.config

import com.gimlee.payments.crypto.piratechain.client.PirateChainRpcClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class PirateWalletHealthIndicator(
    private val rpcClient: PirateChainRpcClient
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val info = rpcClient.getInfo()
            Health.up().withDetail("blocks", info.result?.blocks).build()
        } catch (e: Exception) {
            Health.down().withException(e).build()
        }
    }
}