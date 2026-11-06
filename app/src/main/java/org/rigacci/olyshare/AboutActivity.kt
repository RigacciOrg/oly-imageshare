package org.rigacci.olyshare

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView

/** Static about screen. Version is read from the package, so it always reflects
 *  the value Gradle built with. */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
        findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, pInfo.versionName, code)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
