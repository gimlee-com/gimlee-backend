package com.gimlee.payments.crypto.client

import com.gimlee.payments.client.model.RpcResponse
import com.gimlee.payments.crypto.client.model.Address
import com.gimlee.payments.crypto.client.model.RawReceivedTransaction

interface CryptoClient {
    fun importViewingKey(viewKey: String): RpcResponse<Address>
    fun getReceivedByAddress(address: String, minConfirmations: Int): RpcResponse<List<RawReceivedTransaction>>
}
