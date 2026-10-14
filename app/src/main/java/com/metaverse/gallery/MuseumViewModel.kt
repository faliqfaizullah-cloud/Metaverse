package com.metaverse.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.data.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MuseumViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val prefs = app.getSharedPreferences("metaverse", 0)
    private var job: Job? = null

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    /** Photos the user imported with the picker (shown first on the home grid). */
    private val _extras = MutableStateFlow<List<MediaItem>>(emptyList())
    val extras: StateFlow<List<MediaItem>> = _extras.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.getStringSet("favs", emptySet()).orEmpty().toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _archived = MutableStateFlow(prefs.getStringSet("archived", emptySet()).orEmpty().toSet())
    val archived: StateFlow<Set<String>> = _archived.asStateFlow()

    private val _views = MutableStateFlow(
        prefs.all.entries.mapNotNull { e ->
            (e.value as? Int)?.takeIf { e.key.startsWith("v:") }?.let { e.key.removePrefix("v:") to it }
        }.toMap()
    )
    /** How many times each photo has been opened (drives "Most viewed"). */
    val views: StateFlow<Map<String, Int>> = _views.asStateFlow()

    fun bumpView(uri: Uri) {
        val k = uri.toString()
        val n = (_views.value[k] ?: 0) + 1
        _views.value = _views.value + (k to n)
        prefs.edit().putInt("v:$k", n).apply()
    }

    val month = MutableStateFlow(YearMonth.now())
    val selectedDay = MutableStateFlow<LocalDate?>(null)
    val showFullMuseum = MutableStateFlow(false)
    val query = MutableStateFlow("")

    /** Starts live auto-detection of photos (call after permission is granted). */
    fun startObserving() {
        if (job != null) return
        job = viewModelScope.launch {
            repo.observe().collectLatest { _items.value = it }
        }
    }

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun addExtra(uri: Uri) {
        if (_extras.value.any { it.uri == uri }) return
        val type = getApplication<Application>().contentResolver.getType(uri).orEmpty()
        val item = MediaItem(
            id = uri.hashCode().toLong(), uri = uri, takenMillis = System.currentTimeMillis(),
            name = "", bucket = "Imported", isVideo = type.startsWith("video")
        )
        _extras.value = listOf(item) + _extras.value
    }

    fun toggleFavorite(uri: Uri): Boolean {
        val key = uri.toString()
        val now = if (key in _favorites.value) _favorites.value - key else _favorites.value + key
        _favorites.value = now
        prefs.edit().putStringSet("favs", now).apply()
        return key in now
    }

    fun removeExtra(uri: Uri) {
        _extras.value = _extras.value.filterNot { it.uri == uri }
    }

    /** Archive hides a photo from the wall (the file itself is untouched). */
    fun setArchived(uri: Uri, archived: Boolean) {
        val key = uri.toString()
        val now = if (archived) _archived.value + key else _archived.value - key
        _archived.value = now
        prefs.edit().putStringSet("archived", now).apply()
    }
}
