package com.lifeos.secondbrain.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

data class SettingsSnapshot(
    val vaultFolderId: String? = null,
    val vaultName: String? = null,
    val googleAccount: String? = null,
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "",
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val ignoredFolders: String = AppSettings.DEFAULT_IGNORED_FOLDERS,
    val proxyEnabled: Boolean = false,
    val proxyType: String = "SOCKS5",
    val proxyHost: String = "127.0.0.1",
    val proxyPort: Int = 7890,
    val proxyUsername: String = "",
    val tunnelEnabled: Boolean = false,
    val nodeHost: String = "",
    val nodePort: Int = 443,
    val nodeUuid: String = "",
    val nodePublicKey: String = "",
    val nodeShortId: String = "",
    val nodeSni: String = ""
)

class AppSettings(private val context: Context) {
    private object Keys {
        val vaultId = stringPreferencesKey("vault_folder_id")
        val vaultName = stringPreferencesKey("vault_name")
        val googleAccount = stringPreferencesKey("google_account")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiModel = stringPreferencesKey("ai_model")
        val appearance = stringPreferencesKey("appearance")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val ignoredFolders = stringPreferencesKey("ignored_folders")
        val proxyEnabled = booleanPreferencesKey("proxy_enabled")
        val proxyType = stringPreferencesKey("proxy_type")
        val proxyHost = stringPreferencesKey("proxy_host")
        val proxyPort = intPreferencesKey("proxy_port")
        val proxyUsername = stringPreferencesKey("proxy_username")
        val tunnelEnabled = booleanPreferencesKey("tunnel_enabled")
        val nodeHost = stringPreferencesKey("node_host")
        val nodePort = intPreferencesKey("node_port")
        val nodeUuid = stringPreferencesKey("node_uuid")
        val nodePublicKey = stringPreferencesKey("node_public_key")
        val nodeShortId = stringPreferencesKey("node_short_id")
        val nodeSni = stringPreferencesKey("node_sni")
    }

    val state: Flow<SettingsSnapshot> = context.dataStore.data.map { p ->
        SettingsSnapshot(
            vaultFolderId = p[Keys.vaultId],
            vaultName = p[Keys.vaultName],
            googleAccount = p[Keys.googleAccount],
            aiEnabled = p[Keys.aiEnabled] ?: false,
            aiBaseUrl = p[Keys.aiBaseUrl] ?: "https://api.openai.com/v1",
            aiModel = p[Keys.aiModel] ?: "",
            appearance = runCatching { AppearanceMode.valueOf(p[Keys.appearance] ?: "SYSTEM") }.getOrDefault(AppearanceMode.SYSTEM),
            reduceMotion = p[Keys.reduceMotion] ?: false,
            ignoredFolders = p[Keys.ignoredFolders] ?: DEFAULT_IGNORED_FOLDERS,
            proxyEnabled = p[Keys.proxyEnabled] ?: false,
            proxyType = p[Keys.proxyType] ?: "SOCKS5",
            proxyHost = p[Keys.proxyHost] ?: "127.0.0.1",
            proxyPort = p[Keys.proxyPort] ?: 7890,
            proxyUsername = p[Keys.proxyUsername] ?: "",
            tunnelEnabled = p[Keys.tunnelEnabled] ?: false,
            nodeHost = p[Keys.nodeHost] ?: "",
            nodePort = p[Keys.nodePort] ?: 443,
            nodeUuid = p[Keys.nodeUuid] ?: "",
            nodePublicKey = p[Keys.nodePublicKey] ?: "",
            nodeShortId = p[Keys.nodeShortId] ?: "",
            nodeSni = p[Keys.nodeSni] ?: ""
        )
    }

    suspend fun setVault(id: String, name: String) = context.dataStore.edit {
        it[Keys.vaultId] = id
        it[Keys.vaultName] = name
    }

    suspend fun clearVault() = context.dataStore.edit {
        it.remove(Keys.vaultId)
        it.remove(Keys.vaultName)
        it.remove(Keys.googleAccount)
    }

    suspend fun setGoogleAccount(id: String) = context.dataStore.edit { it[Keys.googleAccount] = id }

    suspend fun setAi(enabled: Boolean, baseUrl: String, model: String) = context.dataStore.edit {
        it[Keys.aiEnabled] = enabled
        it[Keys.aiBaseUrl] = baseUrl.trim().ifBlank { "https://api.openai.com/v1" }
        it[Keys.aiModel] = model.trim()
    }

    suspend fun setAppearance(mode: AppearanceMode) = context.dataStore.edit { it[Keys.appearance] = mode.name }
    suspend fun setReduceMotion(value: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = value }
    suspend fun setIgnoredFolders(raw: String) = context.dataStore.edit { it[Keys.ignoredFolders] = raw.trim() }

    suspend fun setProxy(enabled: Boolean, type: String, host: String, port: Int, username: String) = context.dataStore.edit {
        it[Keys.proxyEnabled] = enabled
        it[Keys.proxyType] = type
        it[Keys.proxyHost] = host
        it[Keys.proxyPort] = port
        it[Keys.proxyUsername] = username
    }

    suspend fun setTunnel(enabled: Boolean, host: String, port: Int, uuid: String, publicKey: String, shortId: String, sni: String) = context.dataStore.edit {
        it[Keys.tunnelEnabled] = enabled
        it[Keys.nodeHost] = host.trim()
        it[Keys.nodePort] = port
        it[Keys.nodeUuid] = uuid.trim()
        it[Keys.nodePublicKey] = publicKey.trim()
        it[Keys.nodeShortId] = shortId.trim()
        it[Keys.nodeSni] = sni.trim()
    }

    companion object {
        const val DEFAULT_IGNORED_FOLDERS = "Templates, 模板, 99 System, _System"
    }
}
