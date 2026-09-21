package com.lifeos.secondbrain

import android.content.IntentSender
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lifeos.secondbrain.ui.AppRoot
import com.lifeos.secondbrain.ui.AppViewModel
import com.lifeos.secondbrain.ui.theme.SecondBrainTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as SecondBrainApp).container
    private val vm by viewModels<AppViewModel> { AppViewModel.Factory(container) }

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            runCatching { container.authorization.parseResult(result.data) }
                .onSuccess { auth -> container.authorization.cache(auth); vm.discoverVault() }
                .onFailure { vm.message.value = "Google Drive 授权没有完成。" }
        } else {
            vm.message.value = "Google Drive 授权已取消。"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            SecondBrainTheme(settings.appearance) { AppRoot(vm, onAuthorizeDrive = ::connectGoogle) }
        }
        vm.initialize()
    }

    private fun connectGoogle() {
        lifecycleScope.launch {
            runCatching { container.identity.signIn(this@MainActivity) }
                .onSuccess { identity ->
                    container.settings.setGoogleAccount(identity.id)
                    authorizeDrive()
                }
                .onFailure { error ->
                    android.util.Log.w("SecondBrainAuth", "Google sign-in failed: ${error.javaClass.name}: ${error.message}", error)
                    vm.message.value = if (error is com.lifeos.secondbrain.identity.IdentityConfigurationException) {
                        "需要先在 GOOGLE_SETUP.md 中配置 Google Web Client ID。"
                    } else {
                        "Google 账号登录没有完成。"
                    }
                }
        }
    }

    private fun authorizeDrive() {
        container.authorization.request(
            onResult = { result ->
                if (result.hasResolution()) {
                    val sender: IntentSender = result.pendingIntent!!.intentSender
                    authLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    container.authorization.cache(result)
                    vm.discoverVault()
                }
            },
            onError = { vm.message.value = "Google Drive 连接失败，重新连接即可。" }
        )
    }
}
