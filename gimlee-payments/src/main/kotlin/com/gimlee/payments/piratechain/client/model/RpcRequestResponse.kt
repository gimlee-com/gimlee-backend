package com.gimlee.payments.piratechain.client.model

private const val JSONRPC_VERSION = "1.0"


data class RpcRequest(
    val method: String,
    val params: List<Any>,
    val id: String,
    val jsonrpc: String = JSONRPC_VERSION
)

data class RpcResponse<T>(
    val result: T?,
    val error: Map<String, Any>?,
    val id: String?
)