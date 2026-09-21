package com.lifeos.secondbrain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lifeos.secondbrain.AppContainer
import com.lifeos.secondbrain.ai.OpenAiCompatibleProvider
import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.SyncState
import com.lifeos.secondbrain.security.SecureSecretStore
import com.lifeos.secondbrain.settings.AppearanceMode
import com.lifeos.secondbrain.settings.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val c: AppContainer) : ViewModel() {
    val notes = c.notes.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tasks = c.notes.tasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTasks = c.notes.allTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ideas = c.notes.ideas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plans = c.notes.plans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val writings = c.notes.writings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val learnings = c.notes.learnings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val projects = c.notes.projects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rawNotes = c.notes.raw().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sync: StateFlow<SyncState> = c.sync.state
    val settings = c.settings.state.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsSnapshot())
    val tunnelState = c.tunnel.state
    val searchResults = MutableStateFlow<List<LifeNote>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val aiKeyConfigured = MutableStateFlow(c.secrets.has(SecureSecretStore.AI_API_KEY))
    val proxyPasswordConfigured = MutableStateFlow(c.secrets.has(SecureSecretStore.PROXY_PASSWORD))
    val captureInProgress = MutableStateFlow(false)

    fun initialize() {
        viewModelScope.launch {
            // Read DataStore directly instead of relying on stateIn's initial default value.
            // Otherwise a warm app restart can incorrectly look like no Vault has been configured.
            val s = c.settings.state.first()
            if (s.vaultFolderId != null) {
                c.syncScheduler.enqueuePendingSync()
                c.sync.incrementalSync(s.vaultFolderId)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val vault = settings.value.vaultFolderId ?: return@launch
            c.sync.incrementalSync(vault)
        }
    }

    fun acceptVault(id: String, name: String) {
        viewModelScope.launch {
            c.settings.setVault(id, name)
            c.sync.fullScan(id)
        }
    }

    fun discoverVault() {
        viewModelScope.launch {
            runCatching { c.sync.discoverDefaultVault() }
                .onSuccess { vault ->
                    if (vault == null) message.value = "没有找到名为 Obsidion 的文件夹。"
                    else acceptVault(vault.id, vault.name)
                }
                .onFailure { message.value = "需要先完成 Google Drive 授权。" }
        }
    }

    fun complete(note: LifeNote) {
        viewModelScope.launch {
            val queued = runCatching { c.sync.completeTask(note.fileId) }.getOrElse {
                message.value = "这条任务暂时还没同步，稍后会自动重试。"
                return@launch
            }
            if (queued) {
                c.syncScheduler.enqueuePendingSync()
                message.value = "已完成，联网后会同步到 Drive。"
            }
        }
    }

    fun capture(text: String, voice: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (c.settings.state.first().vaultFolderId == null) {
                message.value = "这条记录暂时无法保存，请先连接 Vault。"
                return@launch
            }
            onSaved()
            captureInProgress.value = true
            val result = try {
                runCatching { if (voice) c.sync.captureVoice(text) else c.sync.captureText(text) }
                    .getOrElse {
                        message.value = "这条记录暂时无法保存，请稍后重试。"
                        return@launch
                    }
            } finally {
                captureInProgress.value = false
            }
            if (result.queued) {
                c.syncScheduler.enqueuePendingSync()
                message.value = "原话已安全进入待同步队列。"
                return@launch
            }
            message.value = "已经放进你的档案。"
            val provider = c.aiProvider(settings.value) ?: return@launch
            val sourceId = result.remoteFileId ?: return@launch
            message.value = "正在整理你刚才说的东西……"
            runCatching {
                val organized = provider.organize(text)
                c.sync.writeAiItems(sourceId, organized)
            }.onSuccess {
                message.value = "已经整理好了。"
                settings.value.vaultFolderId?.let { c.sync.incrementalSync(it) }
            }.onFailure {
                message.value = "原话已经保存，整理可以稍后再做。"
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch { searchResults.value = if (query.isBlank()) emptyList() else c.notes.search(query) }
    }

    fun saveAiSettings(enabled: Boolean, baseUrl: String, model: String, apiKey: String?) {
        viewModelScope.launch {
            c.settings.setAi(enabled, baseUrl, model)
            if (!apiKey.isNullOrBlank()) c.secrets.put(SecureSecretStore.AI_API_KEY, apiKey.trim())
            aiKeyConfigured.value = c.secrets.has(SecureSecretStore.AI_API_KEY)
            message.value = "AI 设置已保存。"
        }
    }

    fun testAiConnection(baseUrl: String, model: String, apiKey: String?) {
        viewModelScope.launch {
            val key = apiKey?.takeIf { it.isNotBlank() }
                ?: c.secrets.get(SecureSecretStore.AI_API_KEY)
            if (key.isNullOrBlank() || model.isBlank() || baseUrl.isBlank()) {
                message.value = "请先填写 Base URL、Model 和 API Key。"
                return@launch
            }
            message.value = "正在测试 AI 连接……"
            val provider = OpenAiCompatibleProvider(baseUrl.trim(), key.trim(), model.trim(), c.http)
            runCatching {
                provider.organize("这是连接测试。不要创建任何项目，只返回 {\"items\":[]}。")
            }.onSuccess {
                message.value = "AI 连接正常。"
            }.onFailure {
                message.value = "AI 暂时连不上；现有档案和原始记录不会受影响。"
            }
        }
    }

    fun clearAiKey() {
        c.secrets.remove(SecureSecretStore.AI_API_KEY)
        aiKeyConfigured.value = false
        message.value = "AI Key 已清除。"
    }

    fun setAppearance(mode: AppearanceMode) {
        viewModelScope.launch { c.settings.setAppearance(mode) }
    }

    fun setReduceMotion(value: Boolean) {
        viewModelScope.launch { c.settings.setReduceMotion(value) }
    }

    fun saveIgnoredFolders(raw: String) {
        viewModelScope.launch {
            c.settings.setIgnoredFolders(raw)
            val vault = c.settings.state.first().vaultFolderId
            if (vault == null) {
                message.value = "忽略列表已保存。"
                return@launch
            }
            message.value = "已保存；正在按新规则重建档案……"
            c.sync.resetLocalIndex()
            c.sync.incrementalSync(vault)
            message.value = "已按新的忽略规则重建档案。"
        }
    }

    fun saveProxySettings(enabled: Boolean, type: String, host: String, port: String, username: String, password: String?) {
        viewModelScope.launch {
            val parsedPort = port.trim().toIntOrNull() ?: 0
            if (enabled && (host.isBlank() || parsedPort !in 1..65535)) {
                message.value = "代理主机或端口无效。"
                return@launch
            }
            c.settings.setProxy(enabled, type, host.trim(), parsedPort, username.trim())
            if (!password.isNullOrBlank()) c.secrets.put(SecureSecretStore.PROXY_PASSWORD, password.trim())
            proxyPasswordConfigured.value = c.secrets.has(SecureSecretStore.PROXY_PASSWORD)
            message.value = if (enabled) "代理已启用，仅作用于本 App。" else "代理已关闭。"
        }
    }

    fun testProxy() {
        viewModelScope.launch {
            if (!c.settings.state.first().proxyEnabled) {
                message.value = "请先保存并启用代理，再测试。"
                return@launch
            }
            message.value = "正在通过代理连接……"
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val request = okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/about?fields=user")
                        .build()
                    c.http.newCall(request).execute().use { response -> response.code }
                }
            }
            message.value = result.fold(
                onSuccess = { code -> if (code in 200..499) "代理连接正常，Drive API 可达（HTTP $code）。" else "代理返回异常状态码：$code" },
                onFailure = { error -> "代理连不上：${error.javaClass.simpleName} ${error.message.orEmpty().take(90)}" }
            )
        }
    }

    fun saveTunnelSettings(enabled: Boolean, host: String, port: String, uuid: String, publicKey: String, shortId: String, sni: String) {
        viewModelScope.launch {
            val parsedPort = port.trim().toIntOrNull() ?: 443
            if (enabled && (host.isBlank() || uuid.isBlank() || publicKey.isBlank() || sni.isBlank() || parsedPort !in 1..65535)) {
                message.value = "请填写完整节点信息（主机 / 端口 / UUID / PublicKey / SNI）。"
                return@launch
            }
            c.settings.setTunnel(enabled, host, parsedPort, uuid, publicKey, shortId, sni)
            message.value = if (enabled) "已保存，正在启动内置内核……" else "内置内核已关闭。"
        }
    }

    fun clearProxyPassword() {
        c.secrets.remove(SecureSecretStore.PROXY_PASSWORD)
        proxyPasswordConfigured.value = false
        message.value = "代理密码已清除。"
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            c.sync.resetLocalIndex()
            message.value = "本地缓存已清除；下次同步会从 Drive 重建。"
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            c.settings.clearVault()
            c.authorization.clearLocalToken()
            message.value = "已断开本地 Drive 连接信息。"
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(c) as T
    }
}
