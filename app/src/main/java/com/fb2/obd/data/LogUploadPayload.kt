package com.fb2.obd.data

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * GitHub Contents API payload helper. Car HUs often sit at tens of bytes/s
 * (the HU recording showed 35–286 B/s). A 160 KB CSV times out; gzip first.
 */
object LogUploadPayload {
    /** Gzip CSVs larger than this so a slow HU can finish a PUT. */
    const val GZIP_AFTER_BYTES = 32_000

    fun prepare(fileName: String, bytes: ByteArray): Pair<String, ByteArray> {
        if (bytes.size <= GZIP_AFTER_BYTES) return fileName to bytes
        val gz = gzip(bytes)
        return if (gz.size < bytes.size) "$fileName.gz" to gz else fileName to bytes
    }

    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
