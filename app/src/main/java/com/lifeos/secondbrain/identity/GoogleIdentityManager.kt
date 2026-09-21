package com.lifeos.secondbrain.identity

import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lifeos.secondbrain.BuildConfig
import java.security.SecureRandom

/** Google account identity via Credential Manager. Drive OAuth consent is intentionally handled separately. */
class GoogleIdentityManager(context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(activityContext: Context): GoogleIdentity {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) throw IdentityConfigurationException("GOOGLE_WEB_CLIENT_ID 未配置")
        return runCatching { request(activityContext, clientId, authorizedOnly = true) }
            .recoverCatching { error ->
                android.util.Log.w("SecondBrainAuth", "authorized-only attempt failed: ${error.javaClass.name}: ${error.message}", error)
                if (error is NoCredentialException) request(activityContext, clientId, authorizedOnly = false) else throw error
            }.getOrThrow()
    }

    private suspend fun request(context: Context, clientId: String, authorizedOnly: Boolean): GoogleIdentity {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(authorizedOnly)
            .setNonce(nonce())
            .build()
        val result = credentialManager.getCredential(
            context = MutableContextWrapper(context),
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val custom = result.credential as? CustomCredential ?: error("Google 身份凭据类型不受支持")
        require(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "不是 Google ID Token 凭据" }
        val credential = GoogleIdTokenCredential.createFrom(custom.data)
        return GoogleIdentity(credential.id, credential.displayName)
    }

    private fun nonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}

data class GoogleIdentity(val id: String, val displayName: String?)
class IdentityConfigurationException(message: String) : Exception(message)
