package com.lifeos.secondbrain.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface TunnelState {
    data object Off : TunnelState
    data object Starting : TunnelState
    data class Running(val port: Int) : TunnelState
    data class Failed(val reason: String) : TunnelState
}

/** Owns the bundled core lifecycle and keeps the app-only proxy pointed at it. */
class TunnelController {
    private val tunnel = XrayTunnel()
    private val mutex = Mutex()
    private val _state = MutableStateFlow<TunnelState>(TunnelState.Off)
    val state: StateFlow<TunnelState> = _state

    @Volatile private var activeNode: TunnelNode? = null

    suspend fun apply(enabled: Boolean, node: TunnelNode?) {
        mutex.withLock {
            if (!enabled || node == null || !node.isConfigured) {
                if (activeNode != null || tunnel.localPort != 0) {
                    withContext(Dispatchers.IO) { tunnel.stop() }
                }
                activeNode = null
                _state.value = TunnelState.Off
                return
            }
            if (activeNode == node && tunnel.localPort != 0) {
                _state.value = TunnelState.Running(tunnel.localPort)
                return
            }
            _state.value = TunnelState.Starting
            val port = withContext(Dispatchers.IO) { tunnel.freePort().takeIf { it > 0 } ?: DEFAULT_PORT }
            val ok = withContext(Dispatchers.IO) { tunnel.start(node, port) }
            if (ok) {
                activeNode = node
                _state.value = TunnelState.Running(tunnel.localPort)
            } else {
                activeNode = null
                _state.value = TunnelState.Failed(tunnel.lastError ?: "未知错误")
            }
        }
    }

    private companion object {
        const val DEFAULT_PORT = 10808
    }
}
