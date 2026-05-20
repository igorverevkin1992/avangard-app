package com.avangard.app.core.data.auth

import android.content.Context
import android.content.Intent
import com.avangard.app.R
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Google Sign-In state and Drive AppData
 * access-token issuance. Cached account from GoogleSignIn.getLastSignedInAccount
 * seeds the flow on startup; success/failure of the explicit sign-in launcher
 * pushes new values into the flow so the rest of the app can react.
 *
 * Drive REST calls go through [accessToken]: this returns a short-lived
 * Bearer token for the DRIVE_APPDATA scope. The token isn't cached here —
 * GoogleAuthUtil already caches it internally — but callers should re-request
 * on every API call rather than holding a long-lived reference, so token
 * refresh on the GMS side stays automatic.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions())
    }

    private val _account = MutableStateFlow(GoogleSignIn.getLastSignedInAccount(context))
    val account: StateFlow<GoogleSignInAccount?> = _account.asStateFlow()

    val isSignedIn: Boolean
        get() = _account.value != null

    fun signInIntent(): Intent = signInClient.signInIntent

    /**
     * Convert the Activity result from [signInIntent] into a [GoogleSignInAccount].
     * Throws [ApiException] on cancel / DEVELOPER_ERROR (missing OAuth client) /
     * network failure — the screen-level VM catches and translates to user
     * messaging.
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            _account.value = account
            Result.success(account)
        } catch (e: ApiException) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        signInClient.signOut().await()
        _account.value = null
    }

    /**
     * Returns a fresh OAuth 2.0 access token for the Drive AppData scope.
     * GoogleAuthUtil handles refresh / re-issue internally; the call may
     * throw [com.google.android.gms.auth.UserRecoverableAuthException] when
     * the user needs to re-consent — callers (the sync worker) treat that
     * as a retryable failure.
     */
    suspend fun accessToken(): String {
        val current = _account.value
            ?: throw IllegalStateException("AuthRepository.accessToken() called while signed out")
        val accountObj = current.account
            ?: throw IllegalStateException("GoogleSignInAccount has no Android Account attached")
        return withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, accountObj, OAUTH_SCOPE)
        }
    }

    private fun signInOptions(): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
        // Web client id is optional — only needed if the app forwards an
        // id_token to a backend. Drive AppData via GoogleAuthUtil doesn't
        // require it. Empty placeholder ships in strings.xml; release CI
        // can inject a real value if backend-side verification ever lands.
        val webClientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull()
        if (!webClientId.isNullOrBlank()) {
            builder.requestIdToken(webClientId)
        }
        return builder.build()
    }

    companion object {
        private const val DRIVE_APPDATA_SCOPE =
            "https://www.googleapis.com/auth/drive.appdata"
        private const val OAUTH_SCOPE = "oauth2:$DRIVE_APPDATA_SCOPE"
    }
}
