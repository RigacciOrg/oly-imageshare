package org.rigacci.olyshare

import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Olympus / OM System Wi-Fi HTTP API.
 * Pure Kotlin over HttpURLConnection, no third-party libraries.
 * All methods BLOCK, so call them from a background thread.
 */
class OlympusCamera(
    val host: String = Prefs.DEF_CAMERA_IP,
    private val dcimRoot: String = "/" + Prefs.DEF_CAMERA_DCIM,
) {
    private val connectTimeoutMs = 5000
    private val readTimeoutMs = 8000

    // ---- low-level helpers -------------------------------------------------

    private fun open(url: String, readTimeout: Int = readTimeoutMs): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            this.readTimeout = readTimeout
        }

    private fun getText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.use { it.readBytes().toString(Charsets.ISO_8859_1) }
        } finally { c.disconnect() }
    }

    private fun getBytes(url: String): ByteArray {
        val c = open(url)
        try {
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    // ---- capabilities & mode ----------------------------------------------

    fun commandList(): String = getText("http://$host/get_commandlist.cgi")

    /** True when get_commandlist advertises the given cgi command name. */
    private fun advertises(commandListXml: String, cgiName: String): Boolean =
        Regex("""name\s*=\s*"$cgiName"""").containsMatchIn(commandListXml)

    /** The camera supports deletion when get_commandlist advertises exec_erase. */
    fun supportsErase(commandListXml: String): Boolean =
        advertises(commandListXml, "exec_erase")

    /** The camera supports the share/reserved list when it advertises get_rsvimglist. */
    fun supportsShareList(commandListXml: String): Boolean =
        advertises(commandListXml, "get_rsvimglist")

    fun switchToPlay() { getText("http://$host/switch_cammode.cgi?mode=play") }
    fun switchToRec()  { getText("http://$host/switch_cammode.cgi?mode=rec") }

    // ---- listings ----------------------------------------------------------

    private fun parseListing(body: String): List<GalleryImage> {
        val out = ArrayList<GalleryImage>()
        for (line in body.lineSequence()) {
            val f = line.split(",")
            if (f.size < 6) continue                 // skips the "VER_100" header
            val size = f[2].toLongOrNull() ?: continue
            out.add(
                GalleryImage(
                    dir = f[0], name = f[1], size = size,
                    attribute = f[3].toIntOrNull() ?: 0,
                    dateField = f[4].toIntOrNull() ?: 0,
                    timeField = f[5].toIntOrNull() ?: 0,
                )
            )
        }
        return out
    }

    fun listDir(dir: String): List<GalleryImage> =
        parseListing(getText("http://$host/get_imglist.cgi?DIR=$dir"))

    /** Paths (dir/name) flagged for sharing in this directory. */
    fun shareList(dir: String): Set<String> =
        parseListing(getText("http://$host/get_rsvimglist.cgi?DIR=$dir"))
            .map { it.path }.toSet()

    /**
     * Walk DCIM and all sub-directories, returning every (non-directory) file
     * flattened, together with the union of share-flagged paths. The share list
     * is queried only when [fetchShared] is true (i.e. the camera supports it).
     */
    fun scanAll(fetchShared: Boolean): Pair<List<GalleryImage>, Set<String>> {
        val files = ArrayList<GalleryImage>()
        val shared = HashSet<String>()
        val stack = ArrayDeque<String>()
        stack.add(dcimRoot)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val entries = try { listDir(dir) } catch (e: Exception) { emptyList() }
            var hasFiles = false
            for (e in entries) {
                if (e.isDirectory) stack.add(e.path) else { files.add(e); hasFiles = true }
            }
            if (fetchShared && hasFiles) {
                try { shared.addAll(shareList(dir)) } catch (e: Exception) { /* ignore */ }
            }
        }
        return files to shared
    }

    // ---- media -------------------------------------------------------------

    /** 160x120 thumbnail bytes, or null on failure. */
    fun thumbnail(path: String): ByteArray? =
        try { getBytes("http://$host/get_thumbnail.cgi?DIR=$path") } catch (e: Exception) { null }

    /**
     * Stream a full-resolution file to [out]. [onProgress] gets (bytesSoFar, totalBytes);
     * totalBytes is -1 when the server sends no Content-Length. [isAborted] is polled to
     * cancel. Returns true if fully written, false if aborted.
     */
    fun downloadFull(
        path: String,
        out: OutputStream,
        onProgress: (Long, Long) -> Unit,
        isAborted: () -> Boolean,
    ): Boolean {
        val c = open("http://$host$path", readTimeout = 30000)
        try {
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                var sent = 0L
                while (true) {
                    if (isAborted()) return false
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    onProgress(sent, total)
                }
            }
            return true
        } finally { c.disconnect() }
    }

    fun erase(path: String) { getText("http://$host/exec_erase.cgi?DIR=$path") }
}
