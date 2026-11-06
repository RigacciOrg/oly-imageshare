package org.rigacci.olyshare

import java.security.MessageDigest

enum class MediaType { JPG, RAW, VIDEO, OTHER }

/**
 * One entry from the camera's get_imglist.cgi listing.
 * Line format: <directory>,<filename>,<size>,<attribute>,<date>,<time>
 *
 * attribute is a bit field:
 *   bit 0 (0x01) = protected   (confirmed from sample data: protected JPGs have attr 1)
 *   bit 4 (0x10) = subdirectory (the DCIM entry 100OLYMP has attr 16)
 *
 * date/time are FAT-style bit fields:
 *   date: bits 15..9 year (+1980), bits 8..5 month (1..12), bits 4..0 day (1..31)
 *   time: bits 15..11 hour, bits 10..5 minute, bits 4..0 second/2
 */
data class GalleryImage(
    val dir: String,
    val name: String,
    val size: Long,
    val attribute: Int,
    val dateField: Int,
    val timeField: Int,
) {
    val path: String get() = "$dir/$name"
    val isProtected: Boolean get() = (attribute and 0x01) != 0
    val isDirectory: Boolean get() = (attribute and 0x10) != 0

    val extension: String get() = name.substringAfterLast('.', "").uppercase()

    val type: MediaType get() = when (extension) {
        "JPG", "JPEG" -> MediaType.JPG
        "ORF"         -> MediaType.RAW
        "MOV", "MP4", "AVI" -> MediaType.VIDEO
        else -> MediaType.OTHER
    }

    val isVideo: Boolean get() = type == MediaType.VIDEO
    val isRaw: Boolean get() = type == MediaType.RAW

    /** Seconds-resolution sortable timestamp decoded from the FAT bit fields. */
    val timestamp: Long get() {
        val year   = 1980 + (dateField ushr 9)
        val month  = (dateField ushr 5) and 0x0F
        val day    = dateField and 0x1F
        val hour   = timeField ushr 11
        val minute = (timeField ushr 5) and 0x3F
        val second = (timeField and 0x1F) * 2
        // Not a real calendar conversion (good enough for ordering): pack fields.
        return (((((year.toLong() * 13 + month) * 32 + day) * 24 + hour) * 60 + minute) * 60 + second)
    }

    /** Stable cache key = SHA-1 of all listing fields. */
    val cacheKey: String get() {
        val raw = "$dir,$name,$size,$attribute,$dateField,$timeField"
        val md = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }
}
