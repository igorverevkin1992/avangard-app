package com.avangard.app.core.data.cloud

/** A backup snapshot pulled from Google Drive AppData. */
data class RemoteBackup(
    val bytes: ByteArray,
    val fileId: String,
    /** RFC 3339 timestamp Drive reports for the file. Parsed as an epoch
     *  milli to compare against the local `lastSyncedAt`. */
    val modifiedTimeMs: Long,
) {
    // ByteArray breaks data-class equality; the explicit override keeps the
    // generated equals/hashCode honest if anyone ever compares two snapshots.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteBackup) return false
        return fileId == other.fileId &&
            modifiedTimeMs == other.modifiedTimeMs &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileId.hashCode()
        result = 31 * result + modifiedTimeMs.hashCode()
        return result
    }
}

/** Metadata returned after a successful upload / patch. */
data class RemoteMetadata(
    val fileId: String,
    val modifiedTimeMs: Long,
)
