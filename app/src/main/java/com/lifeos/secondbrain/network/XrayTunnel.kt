package com.lifeos.secondbrain.network

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

data class TunnelNode(
    val host: String,
    val port: Int,
    val uuid: String,
    val publicKey: String,
    val shortId: String,
    val sni: String
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535 && uuid.isNotBlank() &&
            publicKey.isNotBlank() && sni.isNotBlank()
}

/** Runs the bundled Xray core in-process with a local SOCKS inbound. App-only, no system VPN. */
class XrayTunnel {
    @Volatile var localPort: Int = 0
        private set
    @Volatile var lastError: String? = null
        private set

    fun isRunning(): Boolean = runCatching {
        val response = LibXray.invoke(invokeRequest("getXrayState", null))
        JSONObject(response).optJSONObject("data")?.optBoolean("running") ?: false
    }.getOrDefault(false)

    fun freePort(): Int = runCatching {
        val response = LibXray.invoke(invokeRequest("getFreePorts", JSONObject().put("count", 1)))
        JSONObject(response).optJSONObject("data")?.optJSONArray("ports")?.optInt(0, 0) ?: 0
    }.getOrDefault(0)

    fun start(node: TunnelNode, port: Int): Boolean {
        stop()
        val request = invokeRequest("runXray", JSONObject().put("xrayJson", buildConfig(node, port)))
        return runCatching {
            val json = JSONObject(LibXray.invoke(request))
            if (json.optBoolean("success")) {
                localPort = port
                lastError = null
                true
            } else {
                localPort = 0
                lastError = json.optString("error").ifBlank { "Xray 启动失败" }
                false
            }
        }.getOrElse { error ->
            localPort = 0
            lastError = error.message ?: error.javaClass.simpleName
            false
        }
    }

    fun stop() {
        runCatching { LibXray.invoke(invokeRequest("stopXray", null)) }
        localPort = 0
    }

    private fun invokeRequest(method: String, payload: JSONObject?): String {
        val request = JSONObject().put("apiVersion", 3).put("method", method)
        if (payload != null) request.put("payload", payload)
        return request.toString()
    }

    private fun buildConfig(node: TunnelNode, port: Int): String {
        val user = JSONObject()
            .put("id", node.uuid)
            .put("encryption", "none")
            .put("flow", "xtls-rprx-vision")
        val vnext = JSONObject()
            .put("address", node.host)
            .put("port", node.port)
            .put("users", JSONArray().put(user))
        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put(
                "streamSettings",
                JSONObject()
                    .put("network", "tcp")
                    .put("security", "reality")
                    .put(
                        "realitySettings",
                        JSONObject()
                            .put("serverName", node.sni)
                            .put("fingerprint", "chrome")
                            .put("publicKey", node.publicKey)
                            .put("shortId", node.shortId)
                            .put("spiderX", "")
                    )
            )
        val inbound = JSONObject()
            .put("listen", "127.0.0.1")
            .put("port", port)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", false))
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(outbound))
            .toString()
    }
}
