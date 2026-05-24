package com.avangard.app.feature.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.avangard.app.core.data.auth.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SignInState(
    val signingIn: Boolean = false,
    val errorCode: Int? = null,
    val errorCodeName: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val account: StateFlow<GoogleSignInAccount?> = auth.account

    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun signInIntent(): Intent {
        _state.value = SignInState(signingIn = true, errorCode = null)
        return auth.signInIntent()
    }

    fun onSignInResult(data: Intent?) {
        val result = auth.handleSignInResult(data)
        _state.value = if (result.isSuccess) {
            SignInState(signingIn = false)
        } else {
            val ex = result.exceptionOrNull() as? ApiException
            val code = ex?.statusCode
            SignInState(
                signingIn = false,
                errorCode = code,
                errorCodeName = code?.let { GoogleSignInStatusCodes.getStatusCodeString(it) },
                errorMessage = ex?.status?.statusMessage ?: ex?.localizedMessage,
            )
        }
    }

    fun onSignInCancelled() {
        _state.value = SignInState(signingIn = false)
    }
}
