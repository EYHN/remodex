package com.remodex.android.data.model

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class RpcMessage(
    val jsonrpc: String? = "2.0",
    val id: JsonValue? = null,
    val method: String? = null,
    val params: JsonValue? = null,
    val result: JsonValue? = null,
    val error: RpcError? = null
) {
    val isResponse: Boolean get() = result != null || error != null
    val isRequest: Boolean get() = method != null && id != null
    val isNotification: Boolean get() = method != null && id == null

    val requestIdKey: String? get() = when (val i = id) {
        is JsonValue.StringValue -> i.value
        is JsonValue.IntValue -> i.value.toString()
        else -> null
    }

    companion object {
        fun request(id: String, method: String, params: JsonValue? = null) = RpcMessage(
            id = JsonValue.string(id),
            method = method,
            params = params
        )

        fun notification(method: String, params: JsonValue? = null) = RpcMessage(
            method = method,
            params = params
        )

        fun response(id: JsonValue, result: JsonValue) = RpcMessage(
            id = id,
            result = result
        )

        fun errorResponse(id: JsonValue?, error: RpcError) = RpcMessage(
            id = id,
            error = error
        )
    }
}

@Serializable
data class RpcError(
    val code: Int = -32000,
    val message: String = "",
    val data: JsonValue? = null
) {
    val errorCode: String? get() = data?.get("errorCode")?.stringValue
}
