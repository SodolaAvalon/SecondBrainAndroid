package com.lifeos.secondbrain

import android.content.Context
import com.lifeos.secondbrain.ai.OpenAiCompatibleProvider
import com.lifeos.secondbrain.data.repository.NoteRepository
import com.lifeos.secondbrain.database.AppDatabase
import com.lifeos.secondbrain.drive.DriveApi
import com.lifeos.secondbrain.drive.GoogleAuthorizationManager
import com.lifeos.secondbrain.identity.GoogleIdentityManager
import com.lifeos.secondbrain.network.AppProxy
import com.lifeos.secondbrain.network.TunnelController
import com.lifeos.secondbrain.security.SecureSecretStore
import com.lifeos.secondbrain.settings.SettingsSnapshot
import com.lifeos.secondbrain.settings.AppSettings
import com.lifeos.secondbrain.sync.SyncEngine
import com.lifeos.secondbrain.sync.SyncScheduler
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val secrets = SecureSecretStore(context)
    val database = AppDatabase.create(context, secrets.databasePassphrase())
    val settings = AppSettings(context)
    val identity = GoogleIdentityManager(context)
    val authorization = GoogleAuthorizationManager(context)
    val proxy = AppProxy()
    val tunnel = TunnelController()
    val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .proxySelector(proxy.selector)
        .proxyAuthenticator(proxy.authenticator)
        .build()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    val drive = DriveApi(authorization, http, json)
    val notes = NoteRepository(database.noteDao())
    val sync = SyncEngine(drive, database.noteDao(), database.pendingOperationDao(), database.syncMetaDao(), settings)
    val syncScheduler = SyncScheduler(context)

    fun aiProvider(snapshot: SettingsSnapshot): OpenAiCompatibleProvider? {
        if (!snapshot.aiEnabled) return null
        val key = secrets.get(SecureSecretStore.AI_API_KEY)?.takeIf { it.isNotBlank() } ?: return null
        if (snapshot.aiModel.isBlank()) return null
        return OpenAiCompatibleProvider(snapshot.aiBaseUrl, key, snapshot.aiModel, http)
    }
}
