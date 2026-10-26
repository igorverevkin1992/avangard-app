package com.itermeum.app.core.data.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.itermeum.app.core.data.cloud.AuthRequiredException
import com.itermeum.app.core.data.cloud.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Google Sign-In for the Drive AppData scope only (the single third-party SDK, section 13). */
@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) : TokenProvider {

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope(DRIVE_APPDATA_SCOPE)).build(),
        )
    }

    private val _account = MutableStateFlow(GoogleSignIn.getLastSignedInAccount(context))
    val account: StateFlow<GoogleSignInAccount?> = _account.asStateFlow()
    val isSignedIn: Boolean get() = _account.value != null

    fun signInIntent(): Intent = signInClient.signInIntent

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> = try {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        _account.value = account
        Result.success(account)
    } catch (e: ApiException) {
        Log.w(TAG, "sign-in failed: ${e.statusCode}", e)
        Result.failure(e)
    }

    suspend fun signOut() { runCatching { signInClient.signOut().await() }; _account.value = null }

    override suspend fun accessToken(): String {
        val acc = _account.value?.account ?: throw AuthRequiredException()
        return withContext(Dispatchers.IO) { GoogleAuthUtil.getToken(context, acc, OAUTH_SCOPE) }
    }

    /** GoogleAuthUtil caches tokens; a token Drive rejected must be cleared before asking again. */
    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val OAUTH_SCOPE = "oauth2:$DRIVE_APPDATA_SCOPE"
    }
}
