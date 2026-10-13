package com.metaverse.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val takenMillis: Long,
    val name: String,
    val bucket: String,
    val isVideo: Boolean,
    val width: Int = 0,
    val height: Int = 0
) {
    val date: LocalDate
        get() = Instant.ofEpochMilli(takenMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

/**
 * Reads every photo/video on the device through MediaStore. Photos that Google Photos has
 * backed up or synced to the device show up here automatically, and a ContentObserver pushes
 * new items live (auto-detect). Google's cloud-only Library API no longer allows reading a
 * user's whole library from third-party apps, so MediaStore is the supported route.
 */
class MediaRepository(private val context: Context) {

    private val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun observe(): Flow<List<MediaItem>> = callbackFlow {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(imagesUri, true, obs)
        context.contentResolver.registerContentObserver(videosUri, true, obs)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(obs) }
    }
        .conflate()
        .map { loadAll() }
        .flowOn(Dispatchers.IO)

    private fun loadAll(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        out += query(imagesUri, isVideo = false)
        out += query(videosUri, isVideo = true)
        return out.sortedByDescending { it.takenMillis }
    }

    private fun query(base: Uri, isVideo: Boolean): List<MediaItem> {
        val list = ArrayList<MediaItem>()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        val cr: ContentResolver = context.contentResolver
        try {
            cr.query(base, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                while (c.moveToNext() && list.size < 4000) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                    list += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        takenMillis = taken,
                        name = c.getString(iName) ?: "",
                        bucket = c.getString(iBucket) ?: "",
                        isVideo = isVideo,
                        width = c.getInt(iW),
                        height = c.getInt(iH)
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission not granted yet
        }
        return list
    }
}
