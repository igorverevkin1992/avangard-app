package com.itermeum.app.core.data.cloud

import android.content.Context
import com.itermeum.app.core.crypto.FieldCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Backup password (Keystore-encrypted at rest) and the last sync markers. */
@Singleton
class BackupSettings @Inject constructor(@ApplicationContext context: Context, private val cipher: FieldCipher) {
    private val prefs = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)

    var password: String?
        get() = prefs.getString("pw", null)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
        set(v) = prefs.edit().apply { if (v == null) remove("pw") else putString("pw", cipher.encrypt(v)) }.apply()

    var lastUploadAt: Long
        get() = prefs.getLong("last_upload", 0L)
        set(v) = prefs.edit().putLong("last_upload", v).apply()

    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(v) = prefs.edit().putString("last_error", v).apply()

    val enabled: Boolean get() = password != null
}
