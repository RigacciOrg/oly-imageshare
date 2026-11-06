package org.rigacci.olyshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Hosts the settings screen. Uses an AppCompat theme (set in the manifest). */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // Up arrow -> menu
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
