package com.lifeos.secondbrain

import android.app.Application
import com.lifeos.secondbrain.network.TunnelNode
import com.lifeos.secondbrain.network.TunnelState
import com.lifeos.secondbrain.security.SecureSecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SecondBrainApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch {
            container.settings.state.collect { snapshot ->
                val node = TunnelNode(
                    host = snapshot.nodeHost,
                    port = snapshot.nodePort,
                    uuid = snapshot.nodeUuid,
                    publicKey = snapshot.nodePublicKey,
                    shortId = snapshot.nodeShortId,
                    sni = snapshot.nodeSni
                )
                container.tunnel.apply(snapshot.tunnelEnabled, node)
                when {
                    snapshot.tunnelEnabled && container.tunnel.state.value is TunnelState.Running ->
                        container.proxy.update(
                            enabled = true,
                            type = "SOCKS5",
                            host = "127.0.0.1",
                            port = (container.tunnel.state.value as TunnelState.Running).port,
                            username = null,
                            password = null
                        )
                    !snapshot.tunnelEnabled && snapshot.proxyEnabled ->
                        container.proxy.update(
                            enabled = true,
                            type = snapshot.proxyType,
                            host = snapshot.proxyHost,
                            port = snapshot.proxyPort,
                            username = snapshot.proxyUsername,
                            password = container.secrets.get(SecureSecretStore.PROXY_PASSWORD)
                        )
                    else ->
                        container.proxy.update(false, "SOCKS5", "", 0, null, null)
                }
            }
        }
    }
}
