package org.rigacci.olyshare

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Single source of truth for settings keys and defaults.
 * Values live in the app's private default SharedPreferences
 * (/data/data/<pkg>/shared_prefs/<pkg>_preferences.xml), so they persist
 * across runs. Every getter falls back to the default, so the rest of the
 * app reads valid values even before the user ever opens Settings.
 */
object Prefs {
    const val DOWNLOAD_MODE = "download_mode"   // "mediastore" | "saf"
    const val SAF_TREE_URI  = "saf_tree_uri"    // persisted tree Uri (SAF mode)
    const val THUMB_SIZE    = "thumb_size"
    const val BUTTON_SIZE   = "button_size"
    const val LABEL_SIZE    = "label_size"
    const val CACHE_MAX_DAYS = "cache_max_days"
    const val CAMERA_IP     = "camera_ip"
    const val CAMERA_DCIM   = "camera_dcim"

    const val DEF_DOWNLOAD_MODE = "mediastore"
    const val DEF_SIZE          = "medium"
    const val DEF_CACHE_MAX_DAYS = 180
    const val DEF_CAMERA_IP     = "192.168.0.10"
    const val DEF_CAMERA_DCIM   = "DCIM"

    /** Sub-folder created inside the shared DCIM collection in MediaStore mode. */
    const val MEDIASTORE_SUBDIR = "OlyShare"

    private fun sp(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)

    fun downloadMode(c: Context): String = sp(c).getString(DOWNLOAD_MODE, DEF_DOWNLOAD_MODE)!!
    fun safTreeUri(c: Context): String?  = sp(c).getString(SAF_TREE_URI, null)
    fun thumbSize(c: Context): String    = sp(c).getString(THUMB_SIZE, DEF_SIZE)!!
    fun buttonSize(c: Context): String   = sp(c).getString(BUTTON_SIZE, DEF_SIZE)!!
    fun labelSize(c: Context): String    = sp(c).getString(LABEL_SIZE, DEF_SIZE)!!
    fun cacheMaxDays(c: Context): Int =
        sp(c).getString(CACHE_MAX_DAYS, DEF_CACHE_MAX_DAYS.toString())!!.toIntOrNull() ?: DEF_CACHE_MAX_DAYS
    fun cameraIp(c: Context): String     = sp(c).getString(CAMERA_IP, DEF_CAMERA_IP)!!.trim()
    fun cameraDcim(c: Context): String   = sp(c).getString(CAMERA_DCIM, DEF_CAMERA_DCIM)!!.trim()
}
