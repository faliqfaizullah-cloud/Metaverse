package com.metaverse.gallery

import android.app.Application
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
    private var job: Job? = null

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

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
}
