package com.lifeos.secondbrain.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleAuthorizationManager(private val context: Context) : AccessTokenProvider {
    private val client get() = Identity.getAuthorizationClient(context)
    @Volatile private var cachedToken: String? = null

    fun request(onResult: (AuthorizationResult) -> Unit, onError: (Throwable) -> Unit) {
        client.authorize(request())
            .addOnSuccessListener(onResult)
            .addOnFailureListener(onError)
    }

    fun parseResult(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    fun cache(result: AuthorizationResult) {
        cachedToken = result.accessToken
    }

    fun clearLocalToken() {
        cachedToken = null
    }

    override fun invalidate() {
        cachedToken = null
    }

    override suspend fun accessToken(): String {
        cachedToken?.let { return it }
        return suspendCancellableCoroutine { cont ->
            client.authorize(request())
                .addOnSuccessListener { result ->
                    val token = result.accessToken
                    if (result.hasResolution() || token.isNullOrBlank()) cont.resumeWithException(AuthorizationRequiredException())
                    else {
                        cachedToken = token
                        cont.resume(token)
                    }
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    private fun request(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive")))
        .build()
}

class AuthorizationRequiredException : Exception("Google Drive authorization requires user interaction")
