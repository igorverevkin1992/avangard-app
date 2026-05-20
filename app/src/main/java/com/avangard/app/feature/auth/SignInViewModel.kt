package com.avangard.app.feature.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.avangard.app.core.data.auth.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SignInState(
    val signingIn: Boolean = false,
    val errorCode: Int? = null,
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
            SignInState(signingIn = false, errorCode = null)
        } else {
            val code = (result.exceptionOrNull() as? ApiException)?.statusCode
            SignInState(signingIn = false, errorCode = code)
        }
    }

    fun onSignInCancelled() {
        _state.value = SignInState(signingIn = false, errorCode = null)
    }
}
