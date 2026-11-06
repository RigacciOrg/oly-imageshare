package org.rigacci.olyshare

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream

/**
 * Where downloaded files go. Two backends, chosen from Settings:
 *   - MediaStore: shared storage under DCIM/OlyShare (Android 10+ scoped
 *     storage, no runtime permission). Falls back to a legacy public-dir
 *     write on API < 29.
 *   - SAF: a user-picked folder tree (ACTION_OPEN_DOCUMENT_TREE), internal or SD.
 *
 * An open file is written in two steps: openStream() then, on success, commit();
 * on failure/abort, abort() removes the partial file so a resumed run re-downloads it.
 */
interface DownloadTarget {
    /** True if a file with this display name already exists in the destination. */
    fun exists(name: String): Boolean
    /** Begin a new file; returns the stream to write to. */
    fun openStream(name: String, mimeType: String): OutputStream
    /** Finish the current file successfully (make it visible). */
    fun commit()
    /** Discard the current partial file. */
    fun abort()

    companion object {
        fun mimeFor(img: GalleryImage): String = when (img.type) {
            MediaType.JPG -> "image/jpeg"
            MediaType.RAW -> "image/x-olympus-orf"
            MediaType.VIDEO -> when (img.extension) {
                "MP4" -> "video/mp4"; "AVI" -> "video/x-msvideo"; else -> "video/quicktime"
            }
            else -> "application/octet-stream"
        }

        fun create(context: Context): DownloadTarget =
            if (Prefs.downloadMode(context) == "saf") {
                val uri = Prefs.safTreeUri(context)
                    ?: throw IllegalStateException("No SAF folder chosen in Settings")
                SafTarget(context, Uri.parse(uri))
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStoreTarget(context)
                else
                    LegacyDcimTarget()
            }
    }
}

private const val SUBDIR = Prefs.MEDIASTORE_SUBDIR   // "OlyShare"

/** API 29+ : insert into the right MediaStore collection with RELATIVE_PATH DCIM/OlyShare. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreTarget(private val context: Context) : DownloadTarget {
    private val resolver = context.contentResolver
    private val relPath = "${Environment.DIRECTORY_DCIM}/$SUBDIR"
    private var pendingUri: Uri? = null
    private var pendingStream: OutputStream? = null

    private fun collectionFor(mime: String): Uri = when {
        mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else                      -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    override fun exists(name: String): Boolean {
        for (coll in listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )) {
            val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                      "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val args = arrayOf("%$SUBDIR%", name)
            resolver.query(coll, arrayOf(MediaStore.MediaColumns._ID), sel, args, null)?.use {
                if (it.count > 0) return true
            }
        }
        return false
    }

    override fun openStream(name: String, mimeType: String): OutputStream {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collectionFor(mimeType), values)
            ?: throw IllegalStateException("MediaStore insert failed for $name")
        pendingUri = uri
        pendingStream = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot open output stream for $name")
        return pendingStream!!
    }

    override fun commit() {
        pendingStream?.flush(); pendingStream?.close(); pendingStream = null
        pendingUri?.let {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(it, values, null, null)
        }
        pendingUri = null
    }

    override fun abort() {
        try { pendingStream?.close() } catch (_: Exception) {}
        pendingStream = null
        pendingUri?.let { try { resolver.delete(it, null, null) } catch (_: Exception) {} }
        pendingUri = null
    }
}

/** SAF : write into a user-picked document tree. */
class SafTarget(private val context: Context, treeUri: Uri) : DownloadTarget {
    private val tree: DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Cannot open the chosen folder")
    private var pendingDoc: DocumentFile? = null
    private var pendingStream: OutputStream? = null

    override fun exists(name: String): Boolean = tree.findFile(name) != null

    override fun openStream(name: String, mimeType: String): OutputStream {
        val doc = tree.createFile(mimeType, name)
            ?: throw IllegalStateException("Cannot create $name in the chosen folder")
        pendingDoc = doc
        pendingStream = context.contentResolver.openOutputStream(doc.uri)
            ?: throw IllegalStateException("Cannot open output stream for $name")
        return pendingStream!!
    }

    override fun commit() {
        pendingStream?.flush(); pendingStream?.close(); pendingStream = null; pendingDoc = null
    }

    override fun abort() {
        try { pendingStream?.close() } catch (_: Exception) {}
        pendingStream = null
        try { pendingDoc?.delete() } catch (_: Exception) {}
        pendingDoc = null
    }
}

/** API < 29 fallback : write straight into public DCIM/OlyShare (needs WRITE permission). */
class LegacyDcimTarget : DownloadTarget {
    private val dir: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), SUBDIR)
            .apply { mkdirs() }
    private var pending: File? = null
    private var pendingStream: OutputStream? = null

    override fun exists(name: String): Boolean = File(dir, name).exists()

    override fun openStream(name: String, mimeType: String): OutputStream {
        val f = File(dir, name)
        pending = f
        pendingStream = f.outputStream()
        return pendingStream!!
    }

    override fun commit() {
        pendingStream?.flush(); pendingStream?.close(); pendingStream = null; pending = null
    }

    override fun abort() {
        try { pendingStream?.close() } catch (_: Exception) {}
        pendingStream = null
        try { pending?.delete() } catch (_: Exception) {}
        pending = null
    }
}
