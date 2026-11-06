package org.rigacci.olyshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Thumbnails live in the app's private internal storage (filesDir/thumbs),
 * one JPEG per image, named by the image's cacheKey (SHA-1 of the listing
 * fields). A small in-memory LruCache avoids re-decoding while scrolling.
 */
object ThumbCache {

    private const val DIR = "thumbs"
    // ~8 MB of decoded 160x120 bitmaps is plenty for a page.
    private val mem = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(c: Context): File = File(c.filesDir, DIR).apply { mkdirs() }
    fun file(c: Context, key: String): File = File(dir(c), "$key.jpg")
    fun has(c: Context, key: String): Boolean = file(c, key).exists()

    fun save(c: Context, key: String, bytes: ByteArray) {
        file(c, key).writeBytes(bytes)
    }

    /** Decode (memory-cached) the thumbnail, or null if missing/corrupt. */
    fun bitmap(c: Context, key: String): Bitmap? {
        mem.get(key)?.let { return it }
        val f = file(c, key)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        mem.put(key, bmp)
        return bmp
    }

    /** Delete cached files not touched within [maxDays]. */
    fun prune(c: Context, maxDays: Int) {
        if (maxDays <= 0) return
        val cutoff = System.currentTimeMillis() - maxDays.toLong() * 24 * 3600 * 1000
        dir(c).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /** Touch so a still-referenced file isn't pruned later. */
    fun touch(c: Context, key: String) {
        file(c, key).setLastModified(System.currentTimeMillis())
    }

    fun clearMemory() = mem.evictAll()
}
