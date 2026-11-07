package org.rigacci.olyshare

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Xml
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors

/**
 * Runs get_caminfo.cgi and reports whether the camera is reachable.
 * On success it highlights the model and any serial numbers; on failure it
 * classifies the error and tells the user how to connect. A "Wi-Fi Settings"
 * button jumps to the system Wi-Fi screen so the user can join the camera hotspot.
 */
class ConnectionActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var modelLabel: TextView
    private lateinit var modelText: TextView
    private lateinit var serialText: TextView
    private lateinit var detailsText: TextView
    private lateinit var retryButton: Button
    private lateinit var wifiButton: Button

    private val ui = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    private val green = Color.parseColor("#2E7D32")
    private val red = Color.parseColor("#C62828")
    private val grey = Color.parseColor("#666666")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        statusText = findViewById(R.id.statusText)
        modelLabel = findViewById(R.id.modelLabel)
        modelText = findViewById(R.id.modelText)
        serialText = findViewById(R.id.serialText)
        detailsText = findViewById(R.id.detailsText)
        retryButton = findViewById(R.id.retryButton)
        wifiButton = findViewById(R.id.wifiButton)

        retryButton.setOnClickListener { check() }
        wifiButton.setOnClickListener { openWifiSettings() }
        check()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Open the system Wi-Fi screen. Android 10+ shows a lightweight slide-up
     * Wi-Fi panel; older versions open the full Wi-Fi settings. Falls back
     * gracefully if a device lacks either activity.
     */
    private fun openWifiSettings() {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Intent(Settings.Panel.ACTION_WIFI)
        else
            Intent(Settings.ACTION_WIFI_SETTINGS)
        try {
            startActivity(primary)
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            catch (e2: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun check() {
        val ip = Prefs.cameraIp(this)
        retryButton.isEnabled = false
        statusText.setTextColor(grey)
        statusText.text = getString(R.string.conn_checking)
        modelLabel.visibility = View.GONE
        modelText.visibility = View.GONE
        serialText.visibility = View.GONE
        detailsText.text = ""

        bg.execute {
            try {
                val xml = httpGet("http://$ip/get_caminfo.cgi")
                val info = parse(xml)
                ui.post { showSuccess(ip, info) }
            } catch (e: Exception) {
                ui.post { showError(e) }
            }
        }
    }

    // --- networking ---------------------------------------------------------

    private fun httpGet(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }
        try {
            val code = c.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.ISO_8859_1) }
        } finally {
            c.disconnect()
        }
    }

    // --- parsing ------------------------------------------------------------

    private class CamInfo(
        val model: String?,
        val serials: List<String>,
        val details: List<Pair<String, String>>,
    )

    private fun parse(xml: String): CamInfo {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val stack = ArrayDeque<String>()
        var model: String? = null
        val serials = ArrayList<String>()
        val details = LinkedHashMap<String, MutableList<String>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> stack.addLast(parser.name)
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty() && stack.isNotEmpty()) {
                        val tag = stack.last()
                        val parent = if (stack.size >= 2) stack[stack.size - 2] else ""
                        when {
                            tag == "model" -> model = text
                            parent == "SerialNumberList" -> serials.add(text)
                            parent == "caminfo" -> details.getOrPut(tag) { ArrayList() }.add(text)
                            else -> details.getOrPut(parent) { ArrayList() }.add(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeLast()
            }
            event = parser.next()
        }
        return CamInfo(model, serials, details.map { it.key to it.value.joinToString(", ") })
    }

    // --- rendering ----------------------------------------------------------

    private fun showSuccess(ip: String, info: CamInfo) {
        statusText.setTextColor(green)
        statusText.text = getString(R.string.conn_ok, ip)

        modelLabel.visibility = View.VISIBLE
        modelText.visibility = View.VISIBLE
        modelText.text = info.model ?: "(unknown model)"

        if (info.serials.isNotEmpty()) {
            serialText.visibility = View.VISIBLE
            serialText.text =
                if (info.serials.size == 1) getString(R.string.conn_serial_one, info.serials[0])
                else getString(R.string.conn_serial_many, info.serials.joinToString(", "))
        } else {
            serialText.visibility = View.GONE
        }

        detailsText.setTextColor(grey)
        detailsText.text = info.details.joinToString("\n") { "${it.first}: ${it.second}" }
        retryButton.isEnabled = true
    }

    private fun showError(e: Throwable) {
        statusText.setTextColor(red)
        statusText.text = getString(R.string.conn_fail, errorTitle(e))
        modelLabel.visibility = View.GONE
        modelText.visibility = View.GONE
        serialText.visibility = View.GONE
        detailsText.setTextColor(red)
        detailsText.text = getString(R.string.conn_instructions)
        retryButton.isEnabled = true
    }

    private fun errorTitle(e: Throwable): String = when (e) {
        is SocketTimeoutException -> getString(R.string.conn_err_timeout)
        is UnknownHostException -> getString(R.string.conn_err_host)
        is NoRouteToHostException -> getString(R.string.conn_err_host_unreach)
        is ConnectException -> {
            val m = (e.message ?: "")
            when {
                m.contains("ENETUNREACH") || m.contains("unreachable", true) ->
                    getString(R.string.conn_err_net_unreach)
                m.contains("ECONNREFUSED") || m.contains("refused", true) ->
                    getString(R.string.conn_err_refused)
                else -> getString(R.string.conn_err_host_unreach)
            }
        }
        else -> {
            val m = (e.message ?: "")
            when {
                m.contains("Cleartext", true) -> getString(R.string.conn_err_cleartext)
                m.startsWith("HTTP ") -> getString(R.string.conn_err_http, m.removePrefix("HTTP "))
                else -> getString(R.string.conn_err_generic)
            }
        }
    }
}
