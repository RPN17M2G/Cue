package io.github.rpn17m2g.cue.data.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnails for the gallery, cached in memory.
 *
 * `loadThumbnail` decodes a fresh bitmap on every call, which is far too much
 * work for a scrolling list, so decoded frames are kept in an LRU sized in
 * bytes. Failures (an audio file without embedded art, a deleted document) are
 * remembered for the session, because retrying them on every scroll only costs
 * I/O.
 */
class ThumbnailStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheSizeBytes: Int = DEFAULT_CACHE_BYTES,
) {

    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val missing: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    suspend fun load(uri: Uri, sizePx: Int = THUMBNAIL_PX): Bitmap? = withContext(ioDispatcher) {
        val key = "$uri@$sizePx"
        cache.get(key)?.let { return@withContext it }
        if (key in missing) return@withContext null
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        }.getOrNull()
        if (bitmap == null) missing.add(key) else cache.put(key, bitmap)
        bitmap
    }

    private companion object {
        const val THUMBNAIL_PX = 144
        const val DEFAULT_CACHE_BYTES = 16 * 1024 * 1024
    }
}
