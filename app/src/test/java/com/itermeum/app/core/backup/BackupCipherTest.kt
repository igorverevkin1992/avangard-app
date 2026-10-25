package com.itermeum.app.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCipherTest {

    @Test
    fun `round trip and magic header`() {
        val plain = "{\"format\":\"itermeum-backup\"}".toByteArray()
        val file = BackupCipher.encrypt(plain, "correct horse".toCharArray())
        assertEquals("IMB1", String(file.copyOfRange(0, 4)))
        assertTrue(BackupCipher.isBackup(file))
        assertArrayEquals(plain, BackupCipher.decrypt(file, "correct horse".toCharArray()))
    }

    @Test
    fun `wrong password fails and foreign files are rejected`() {
        val file = BackupCipher.encrypt("x".toByteArray(), "pw-12345".toCharArray())
        try { BackupCipher.decrypt(file, "other-123".toCharArray()); fail("expected failure") } catch (e: Exception) { /* AEADBadTagException */ }
        assertFalse(BackupCipher.isBackup("{\"schemaVersion\":9}".toByteArray()))
    }

    @Test
    fun `two encryptions of the same data differ (fresh salt and iv)`() {
        val a = BackupCipher.encrypt("same".toByteArray(), "pw-12345".toCharArray())
        val b = BackupCipher.encrypt("same".toByteArray(), "pw-12345".toCharArray())
        assertFalse(a.contentEquals(b))
    }
}
