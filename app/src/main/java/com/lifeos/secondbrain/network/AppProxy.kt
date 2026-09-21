package com.lifeos.secondbrain.network

import okhttp3.Authenticator
import okhttp3.Credentials
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** App-only proxy: applies to this app's HTTP stack (Drive + AI) and never to other apps. */
class AppProxy {
    @Volatile private var proxy: Proxy? = null
    @Volatile private var username: String? = null
    @Volatile private var password: String? = null

    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = listOf(proxy ?: Proxy.NO_PROXY)
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
    }

    val authenticator: Authenticator = Authenticator { _, response ->
        val user = username
        if (response.code == 407 && !user.isNullOrBlank()) {
            response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(user, password.orEmpty()))
                .build()
        } else {
            null
        }
    }

    val isEnabled: Boolean get() = proxy != null

    fun update(enabled: Boolean, type: String, host: String, port: Int, username: String?, password: String?) {
        this.username = username?.takeIf { it.isNotBlank() }
        this.password = password
        proxy = if (!enabled || host.isBlank() || port !in 1..65535) {
            null
        } else {
            Proxy(
                if (type.equals("HTTP", ignoreCase = true)) Proxy.Type.HTTP else Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(host, port)
            )
        }
    }
}
