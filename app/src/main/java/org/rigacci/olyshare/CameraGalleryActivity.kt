package org.rigacci.olyshare

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class CameraGalleryActivity : AppCompatActivity() {

    private lateinit var camera: OlympusCamera

    private var content: GalleryContent? = null
    private val selected = HashSet<String>()

    private var currentPage = 0
    private var pageSize = 1
    private var spanCount = 1
    private var rows = 1
    private var cellContentPx = 0

    private lateinit var grid: RecyclerView
    private lateinit var glm: GridLayoutManager
    private lateinit var adapter: ThumbAdapter

    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSelectAll: ImageButton
    private lateinit var btnDeselect: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var btnFirst: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnLast: ImageButton
    private lateinit var lblPage: TextView
    private lateinit var lblSel: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView

    private val ui = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    @Volatile private var aborted = false

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        camera = OlympusCamera(Prefs.cameraIp(this), "/" + Prefs.cameraDcim(this).trim('/'))

        bindViews()
        applyButtonSizes()
        applyLabelSizes()
        setupGrid()
        wireButtons()

        // Delete stays disabled/greyed until a successful load confirms support.
        setDeleteAvailable(false)

        savedInstanceState?.getStringArrayList("selected")?.let { selected.addAll(it) }
        currentPage = savedInstanceState?.getInt("page", 0) ?: 0

        startLoad()
    }

    private fun bindViews() {
        grid = findViewById(R.id.grid)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselect = findViewById(R.id.btnDeselect)
        btnDelete = findViewById(R.id.btnDelete)
        btnDownload = findViewById(R.id.btnDownload)
        btnFirst = findViewById(R.id.btnFirst)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnLast = findViewById(R.id.btnLast)
        lblPage = findViewById(R.id.lblPage)
        lblSel = findViewById(R.id.lblSel)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)
    }

    /** Enable/disable the Delete button and grey its icon accordingly. */
    private fun setDeleteAvailable(available: Boolean) {
        btnDelete.isEnabled = available
        btnDelete.alpha = if (available) 1f else 0.3f
    }

    private fun applyButtonSizes() {
        val h = resources.getDimensionPixelSize(
            when (Prefs.buttonSize(this)) {
                "small" -> R.dimen.icon_size_small
                "large" -> R.dimen.icon_size_large
                else -> R.dimen.icon_size_medium
            }
        )
        listOf(btnRefresh, btnSelectAll, btnDeselect, btnDelete, btnDownload,
               btnFirst, btnPrev, btnNext, btnLast).forEach {
            val lp = it.layoutParams; lp.height = h; it.layoutParams = lp
        }
    }

    private fun applyLabelSizes() {
        val spPx = resources.getDimension(
            when (Prefs.labelSize(this)) {
                "small" -> R.dimen.label_size_small
                "large" -> R.dimen.label_size_large
                else -> R.dimen.label_size_medium
            }
        )
        val sp = spPx / resources.displayMetrics.scaledDensity
        lblPage.textSize = sp
        lblSel.textSize = sp
    }

    private fun columnsForThumb(): Int = when (Prefs.thumbSize(this)) {
        "large" -> 3; "medium" -> 4; "small" -> 5; "tiny" -> 6; else -> 4
    }

    private fun setupGrid() {
        glm = GridLayoutManager(this, spanCount)
        grid.layoutManager = glm
        adapter = ThumbAdapter(this, emptySet(), selected) { toggle(it) }
        grid.adapter = adapter

        val gap = resources.getDimensionPixelSize(R.dimen.thumb_padding)
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View,
                                        parent: RecyclerView, state: RecyclerView.State) {
                outRect.set(gap / 2, gap / 2, gap / 2, gap / 2)
            }
        })

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy) && abs(vx) > 800) {
                    if (vx < 0) goToPage(currentPage + 1) else goToPage(currentPage - 1)
                    return true
                }
                return false
            }
        })
        grid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean =
                detector.onTouchEvent(e)
        })
    }

    private fun wireButtons() {
        btnRefresh.setOnClickListener { startLoad() }

        btnSelectAll.setOnClickListener { showSelectMenu(it) }
        btnDeselect.setOnClickListener { showDeselectMenu(it) }

        btnDelete.setOnClickListener { confirmDelete() }
        btnDownload.setOnClickListener { confirmDownload() }

        btnFirst.setOnClickListener { goToPage(0) }
        btnPrev.setOnClickListener { goToPage(currentPage - 1) }
        btnNext.setOnClickListener { goToPage(currentPage + 1) }
        btnLast.setOnClickListener { goToPage(totalPages() - 1) }
    }

    // ------------------------------------------------------------------ load

    private fun startLoad() {
        aborted = false
        loadingOverlay.visibility = View.VISIBLE
        bg.execute {
            try {
                val c = GalleryLoader.load(this, camera, onProgress = { p ->
                    ui.post {
                        loadingText.text = when (p) {
                            is LoadProgress.QueryingCamera -> getString(R.string.loading_query)
                            is LoadProgress.Listing -> getString(R.string.loading_list)
                            is LoadProgress.Thumbnails ->
                                getString(R.string.loading_thumbs, p.done, p.total)
                        }
                    }
                })
                ui.post { onLoaded(c) }
            } catch (e: Exception) {
                ui.post {
                    loadingOverlay.visibility = View.GONE
                    setDeleteAvailable(false)   // couldn't confirm support -> keep it greyed
                    toast("Error: ${e.message}")
                }
            }
        }
    }

    private fun onLoaded(c: GalleryContent) {
        content = c
        val existing = c.images.map { it.path }.toHashSet()
        selected.retainAll(existing)

        setDeleteAvailable(c.deleteSupported)

        adapter = ThumbAdapter(this, c.sharedPaths, selected) { toggle(it) }
        grid.adapter = adapter

        loadingOverlay.visibility = View.GONE
        grid.post {
            computeGridMetrics()
            if (currentPage >= totalPages()) currentPage = max(0, totalPages() - 1)
            showPage(currentPage)
        }
    }

    // --------------------------------------------------------------- layout

    private fun computeGridMetrics() {
        content ?: return
        val metrics = resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        val cols = columnsForThumb()
        val nominalCell = shortSide / cols

        val usableW = grid.width - grid.paddingLeft - grid.paddingRight
        val usableH = grid.height - grid.paddingTop - grid.paddingBottom
        if (usableW <= 0 || usableH <= 0) return

        spanCount = max(1, usableW / nominalCell)
        val slotW = usableW / spanCount
        val gap = resources.getDimensionPixelSize(R.dimen.thumb_padding)
        cellContentPx = slotW - gap
        val cellH = cellContentPx * 3 / 4
        rows = max(1, usableH / max(1, cellH))
        pageSize = max(1, spanCount * rows)

        glm.spanCount = spanCount
        adapter.cellContentPx = cellContentPx
    }

    private fun totalPages(): Int {
        val n = content?.images?.size ?: 0
        return max(1, ceil(n.toDouble() / pageSize).toInt())
    }

    private fun showPage(page: Int) {
        val c = content ?: return
        currentPage = page.coerceIn(0, totalPages() - 1)
        val from = currentPage * pageSize
        val to = min(from + pageSize, c.images.size)
        adapter.submit(if (from < to) c.images.subList(from, to) else emptyList())
        grid.scrollToPosition(0)
        updateCounters()
    }

    private fun goToPage(page: Int) {
        if (content == null) return
        showPage(page.coerceIn(0, totalPages() - 1))
    }

    private fun updateCounters() {
        val total = content?.images?.size ?: 0
        lblPage.text = "p.${currentPage + 1}/${totalPages()}"
        lblSel.text = "sel.${selected.size}/$total"
    }

    // ------------------------------------------------------------- rotation

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val anchorIndex = currentPage * pageSize
        grid.post {
            computeGridMetrics()
            currentPage = (anchorIndex / pageSize).coerceIn(0, totalPages() - 1)
            showPage(currentPage)
        }
    }

    // ------------------------------------------------------------ selection

    private fun pagePaths(): List<String> {
        val c = content ?: return emptyList()
        val from = currentPage * pageSize
        val to = min(from + pageSize, c.images.size)
        return if (from < to) c.images.subList(from, to).map { it.path } else emptyList()
    }

    private fun toggle(img: GalleryImage) {
        if (!selected.add(img.path)) selected.remove(img.path)
        refreshAfterSelection()
    }

    private fun showSelectMenu(anchor: View) {
        val c = content ?: return
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, getString(R.string.sel_mode_page))
        menu.menu.add(0, 2, 1, getString(R.string.sel_mode_all))
        menu.menu.add(0, 3, 2, getString(R.string.sel_mode_protected))
        menu.menu.add(0, 4, 3, getString(R.string.sel_mode_shared))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> selected.addAll(pagePaths())
                2 -> selected.addAll(c.images.map { it.path })
                3 -> selected.addAll(c.images.filter { it.isProtected }.map { it.path })
                4 -> selected.addAll(c.images.filter { c.sharedPaths.contains(it.path) }.map { it.path })
            }
            refreshAfterSelection()
            true
        }
        menu.show()
    }

    private fun showDeselectMenu(anchor: View) {
        val c = content ?: return
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, getString(R.string.desel_mode_page))
        menu.menu.add(0, 2, 1, getString(R.string.desel_mode_all))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> selected.removeAll(pagePaths().toSet())
                2 -> selected.clear()
            }
            refreshAfterSelection()
            true
        }
        menu.show()
    }

    private fun refreshAfterSelection() {
        adapter.notifyDataSetChanged()
        updateCounters()
    }

    // ------------------------------------------------------------- download

    private fun confirmDownload() {
        if (selected.isEmpty()) { toast(getString(R.string.no_selection)); return }
        val n = selected.size
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.dl_confirm, n))
            .setPositiveButton(R.string.btn_ok) { _, _ -> runDownload() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun runDownload() {
        val c = content ?: return
        val target = try { DownloadTarget.create(this) }
            catch (e: Exception) { toast("Error: ${e.message}"); return }

        val toDownload = c.images.filter { selected.contains(it.path) }
        val n = toDownload.size
        aborted = false

        val view = layoutInflater.inflate(R.layout.dialog_download, null)
        val counter = view.findViewById<TextView>(R.id.dlCounter)
        val fileName = view.findViewById<TextView>(R.id.dlFileName)
        val totalBar = view.findViewById<ProgressBar>(R.id.dlTotalBar)
        val fileBar = view.findViewById<ProgressBar>(R.id.dlFileBar)
        totalBar.max = n
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setNegativeButton(R.string.btn_abort) { _, _ -> aborted = true }
            .create()
        dialog.show()

        bg.execute {
            var skipped = 0
            var index = 0
            for (img in toDownload) {
                if (aborted) break
                index++
                ui.post {
                    counter.text = getString(R.string.dl_progress, index, n)
                    fileName.text = img.name
                    fileBar.progress = 0
                }
                if (target.exists(img.name)) {
                    skipped++
                    ui.post { totalBar.progress = index }
                    continue
                }
                val ok = try {
                    val out = target.openStream(img.name, DownloadTarget.mimeFor(img))
                    val done = camera.downloadFull(img.path, out,
                        onProgress = { sent, tot ->
                            if (tot > 0) ui.post { fileBar.progress = ((sent * 100) / tot).toInt() }
                        },
                        isAborted = { aborted })
                    if (done) { target.commit(); true } else { target.abort(); false }
                } catch (e: Exception) {
                    target.abort(); false
                }
                if (ok) {
                    selected.remove(img.path)
                    ui.post { updateCounters() }
                }
                ui.post { totalBar.progress = index }
            }
            ui.post {
                dialog.dismiss()
                if (skipped > 0) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.dl_skipped_warning, skipped))
                        .setPositiveButton(R.string.btn_ok, null)
                        .show()
                }
                showPage(currentPage)
            }
        }
    }

    // --------------------------------------------------------------- delete

    private fun confirmDelete() {
        if (!(content?.deleteSupported ?: false)) { toast(getString(R.string.delete_unsupported)); return }
        if (selected.isEmpty()) { toast(getString(R.string.no_selection)); return }
        val n = selected.size
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.del_confirm, n))
            .setPositiveButton(R.string.btn_ok) { _, _ -> runDelete() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun runDelete() {
        val c = content ?: return
        val toDelete = c.images.filter { selected.contains(it.path) }
        val n = toDelete.size
        aborted = false

        val view = layoutInflater.inflate(R.layout.dialog_delete, null)
        val counter = view.findViewById<TextView>(R.id.delCounter)
        val bar = view.findViewById<ProgressBar>(R.id.delBar)
        bar.max = n
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setNegativeButton(R.string.btn_abort) { _, _ -> aborted = true }
            .create()
        dialog.show()

        bg.execute {
            var index = 0
            for (img in toDelete) {
                if (aborted) break
                index++
                ui.post {
                    counter.text = getString(R.string.del_progress, index, n)
                    bar.progress = index
                }
                try { camera.erase(img.path); selected.remove(img.path) } catch (_: Exception) {}
            }
            ui.post {
                dialog.dismiss()
                startLoad()
            }
        }
    }

    // ----------------------------------------------------------- lifecycle

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected", ArrayList(selected))
        outState.putInt("page", currentPage)
    }

    override fun onDestroy() {
        super.onDestroy()
        val cam = camera
        Executors.newSingleThreadExecutor().execute {
            try { cam.switchToRec() } catch (_: Exception) {}
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
