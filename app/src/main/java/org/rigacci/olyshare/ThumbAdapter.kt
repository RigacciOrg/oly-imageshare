package org.rigacci.olyshare

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * Shows ONE page of thumbnails. The activity swaps the page's sublist in with
 * [submit] and sets [cellContentPx] (the 4:3 cell width) after each layout pass.
 */
class ThumbAdapter(
    private val context: Context,
    private val sharedPaths: Set<String>,
    private val selected: Set<String>,
    private val onToggle: (GalleryImage) -> Unit,
) : RecyclerView.Adapter<ThumbAdapter.VH>() {

    private var items: List<GalleryImage> = emptyList()
    var cellContentPx: Int = 0

    fun submit(page: List<GalleryImage>) { items = page; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context).inflate(R.layout.item_thumb, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val img = items[position]

        // 4:3 cell height from the current cell width.
        if (cellContentPx > 0) {
            val lp = h.cell.layoutParams
            lp.height = cellContentPx * 3 / 4
            h.cell.layoutParams = lp
        }

        // thumbnail (cached) or generic fallback
        val bmp = ThumbCache.bitmap(context, img.cacheKey)
        if (bmp != null) h.thumb.setImageBitmap(bmp)
        else h.thumb.setImageResource(R.drawable.ic_broken_image)

        // green status flags (bottom-left)
        h.flagProtected.visibility = if (img.isProtected) View.VISIBLE else View.GONE
        h.flagShare.visibility = if (sharedPaths.contains(img.path)) View.VISIBLE else View.GONE
        h.flagVideo.visibility = if (img.isVideo) View.VISIBLE else View.GONE
        h.flagRaw.visibility = if (img.isRaw) View.VISIBLE else View.GONE
        // yellow selection tick (top-right)
        h.flagSelected.visibility = if (selected.contains(img.path)) View.VISIBLE else View.GONE

        // flag size scales with the cell
        if (cellContentPx > 0) {
            val fs = (cellContentPx * 0.24f).toInt().coerceIn(dp(14), dp(28))
            setFlagSize(h.flagProtected, fs); setFlagSize(h.flagShare, fs)
            setFlagSize(h.flagVideo, fs); setFlagSize(h.flagRaw, fs)
            setFlagSize(h.flagSelected, fs)
        }

        h.cell.setOnClickListener { onToggle(img) }
    }

    private fun setFlagSize(v: ImageView, px: Int) {
        val lp = v.layoutParams
        lp.width = px; lp.height = px
        v.layoutParams = lp
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cell: FrameLayout = v.findViewById(R.id.cell)
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val flagProtected: ImageView = v.findViewById(R.id.flagProtected)
        val flagShare: ImageView = v.findViewById(R.id.flagShare)
        val flagVideo: ImageView = v.findViewById(R.id.flagVideo)
        val flagRaw: ImageView = v.findViewById(R.id.flagRaw)
        val flagSelected: ImageView = v.findViewById(R.id.flagSelected)
    }
}
