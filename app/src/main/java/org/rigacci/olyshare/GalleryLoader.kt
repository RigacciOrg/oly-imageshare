package org.rigacci.olyshare

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Immutable result of a full gallery scan. */
class GalleryContent(
    val images: List<GalleryImage>,      // flattened, sorted newest-first
    val sharedPaths: Set<String>,
    val deleteSupported: Boolean,
)

/** Progress phases reported to the UI while loading. */
sealed class LoadProgress {
    object QueryingCamera : LoadProgress()
    object Listing : LoadProgress()
    data class Thumbnails(val done: Int, val total: Int) : LoadProgress()
}

/**
 * Orchestrates one gallery load: capability check, play mode, directory walk,
 * share lists, and parallel thumbnail caching. Blocks; run on a background thread.
 */
object GalleryLoader {

    fun load(
        context: Context,
        camera: OlympusCamera,
        onProgress: (LoadProgress) -> Unit,
        isAborted: () -> Boolean = { false },
    ): GalleryContent {
        onProgress(LoadProgress.QueryingCamera)
        val cmdList = try { camera.commandList() } catch (e: Exception) { "" }
        val deleteSupported = camera.supportsErase(cmdList)
        val shareSupported = camera.supportsShareList(cmdList)

        camera.switchToPlay()

        onProgress(LoadProgress.Listing)
        // Query get_rsvimglist only when the camera advertises it.
        val (files, shared) = camera.scanAll(fetchShared = shareSupported)
        val sorted = files.sortedWith(
            compareByDescending<GalleryImage> { it.timestamp }.thenBy { it.name }
        )

        // Prune stale cache before filling it again.
        ThumbCache.prune(context, Prefs.cacheMaxDays(context))

        // Fetch only missing thumbnails, in parallel.
        val missing = sorted.filter { !ThumbCache.has(context, it.cacheKey) }
        val total = sorted.size
        val done = AtomicInteger(total - missing.size)
        onProgress(LoadProgress.Thumbnails(done.get(), total))

        if (missing.isNotEmpty()) {
            val pool = Executors.newFixedThreadPool(4)
            try {
                val tasks = missing.map { img ->
                    java.util.concurrent.Callable {
                        if (!isAborted()) {
                            val bytes = camera.thumbnail(img.path)
                            if (bytes != null && bytes.isNotEmpty()) {
                                ThumbCache.save(context, img.cacheKey, bytes)
                            }
                            onProgress(LoadProgress.Thumbnails(done.incrementAndGet(), total))
                        }
                        null
                    }
                }
                pool.invokeAll(tasks)
            } finally {
                pool.shutdown()
            }
        }
        // Keep already-cached, still-referenced thumbs fresh.
        sorted.forEach { ThumbCache.touch(context, it.cacheKey) }

        return GalleryContent(sorted, shared, deleteSupported)
    }
}
