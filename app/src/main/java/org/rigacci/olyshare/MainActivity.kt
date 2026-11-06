package org.rigacci.olyshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import java.util.concurrent.Executors

/**
 * Main page: a vertical stack of menu buttons. Each opens its own Activity;
 * "Quit" cleans up the thumbnail cache (removing entries older than the
 * "Max days" setting) and then closes the app.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, CameraGalleryActivity::class.java))
        }
        findViewById<Button>(R.id.btnConnection).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<Button>(R.id.btnQuit).setOnClickListener { quit() }
    }

    /** Prune old cached thumbnails in the background, then close every screen. */
    private fun quit() {
        val ctx = applicationContext
        val days = Prefs.cacheMaxDays(this)
        Executors.newSingleThreadExecutor().execute {
            try { ThumbCache.prune(ctx, days) } catch (_: Exception) {}
            runOnUiThread { finishAffinity() }
        }
    }
}
