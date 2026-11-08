package com.hamad.tvhome.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hamad.tvhome.data.TmdbClient
import com.hamad.tvhome.data.TmdbClient.TmdbException
import com.hamad.tvhome.data.TmdbRepository
import com.hamad.tvhome.data.TvPrefs
import com.hamad.tvhome.launcher.MediaItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search over real TMDB results. The 300 ms debounce is the only asynchronous
 * part; focus and text editing stay in the pure [SearchReducer].
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val prefs = TvPrefs(application)
    private val repository = TmdbRepository(
        TmdbClient(com.hamad.tvhome.BuildConfig.TMDB_READ_TOKEN),
        prefs,
    )

    private val _state = MutableStateFlow(
        SearchState(
            query = savedState.get<String>("query").orEmpty(),
            configured = repository.enabled,
        ),
    )
    val state = _state.asStateFlow()

    private var lastSearched: String? = null

    init {
        viewModelScope.launch {
            _state
                .map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(300)
                .collect { query -> runSearch(query) }
        }
    }

    fun move(dir: SearchDir) = _state.update { SearchReducer.move(it, dir) }

    fun append(text: String) = _state.update { SearchReducer.append(it, text) }

    fun activate(): MediaItem? {
        val (next, item) = SearchReducer.activate(_state.value)
        _state.value = next
        return item
    }

    /** Returns true when the screen should finish (Back on the keyboard). */
    fun back(): Boolean {
        val (next, finish) = SearchReducer.back(_state.value)
        _state.value = next
        return finish
    }

    private suspend fun runSearch(query: String) {
        if (!repository.enabled) {
            _state.value = _state.value.copy(searching = false, error = null)
            return
        }
        if (query.length < 2) {
            lastSearched = query
            _state.value = SearchReducer.withResults(
                _state.value.copy(results = emptyList()),
                emptyList(),
            )
            return
        }
        if (query == lastSearched && _state.value.results.isNotEmpty()) return
        lastSearched = query

        _state.update { it.copy(searching = true, error = null) }
        runCatching { repository.search(query) }
            .onSuccess { items ->
                _state.value = SearchReducer.withResults(
                    _state.value.copy(searching = false),
                    items,
                )
            }
            .onFailure { error ->
                _state.value = SearchReducer.withResults(
                    _state.value.copy(searching = false),
                    emptyList(),
                    error = describe(error),
                )
            }
    }

    private fun describe(error: Throwable): String = when (error) {
        is TmdbException -> "Search failed (HTTP ${error.code})."
        else -> "Can't reach TMDB. Check the network and try again."
    }
}
