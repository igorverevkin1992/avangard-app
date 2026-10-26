package com.itermeum.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs [block] off the main thread inside the broadcast's `goAsync` window.
 * Any failure is logged instead of crashing the process: a crashing receiver
 * at night means no alarm in the morning.
 */
fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(tag, "receiver work failed", t)
        } finally {
            pending.finish()
        }
    }
}

/** Credential-encrypted storage (Room, Keystore-backed prefs) is readable only after the first unlock. */
fun Context.isUserUnlocked(): Boolean = (getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked
