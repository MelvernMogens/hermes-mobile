package id.melvern.hermesmobile.core.rpc

import kotlinx.serialization.json.JsonObject

/** Satu frame JSON-RPC 2.0 yang lewat kabel WS. */
sealed interface RpcFrame {
    /** Request client→server. */
    data class Request(val id: String, val method: String, val params: JsonObject?) : RpcFrame
    /** Response server→client untuk request kita. */
    data class Response(val id: String?, val result: JsonObject?, val error: RpcError?) : RpcFrame
    /** Server→client request (agent nanya user: approval/clarify/...). */
    data class ServerRequest(val id: String, val method: String, val params: JsonObject?) : RpcFrame
    /** Notifikasi event. */
    data class Event(val type: String, val sessionId: String, val payload: JsonObject?) : RpcFrame
}

data class RpcError(val code: Int, val message: String, val data: kotlinx.serialization.json.JsonElement? = null)
