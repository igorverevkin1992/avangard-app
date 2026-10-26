package com.itermeum.app.core.data.cloud

/** OAuth access tokens for Drive; [invalidate] drops a token the server rejected (401). */
interface TokenProvider {
    suspend fun accessToken(): String
    suspend fun invalidate(token: String)
}

/** Drive answered with a non-2xx status. */
class DriveHttpException(val code: Int, message: String) : java.io.IOException(message)

/** Google rejected even a fresh token: the user must sign in again. */
class AuthRequiredException : Exception("Google sign-in required")
