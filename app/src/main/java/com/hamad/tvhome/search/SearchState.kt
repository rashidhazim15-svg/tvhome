package com.hamad.tvhome.search

import androidx.compose.runtime.Immutable
import com.hamad.tvhome.launcher.MediaItem

enum class SearchPane { Keyboard, Actions, Results }

enum class SearchDir { Left, Right, Up, Down }

@Immutable
data class SearchState(
    val query: String = "",
    val pane: SearchPane = SearchPane.Keyboard,
    val keyIndex: Int = 0,
    val actionIndex: Int = 0,
    val resultIndex: Int = 0,
    val results: List<MediaItem> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
    val configured: Boolean = true,
) {
    val canSearch: Boolean get() = query.trim().length >= 2
}

object SearchKeys {
    const val COLUMNS = 6
    const val ROWS = 6

    /** 36 keys: a-z then 0-9, laid out in six rows of six. */
    val chars: List<String> = buildList {
        for (c in 'a'..'z') add(c.toString())
        for (c in '0'..'9') add(c.toString())
    }

    val actions: List<String> = listOf("Space", "Delete", "Clear")

    fun indexOf(char: String): Int = chars.indexOf(char.lowercase()).coerceAtLeast(0)
}

object SearchReducer {
    const val RESULT_COLUMNS = 4

    fun move(state: SearchState, dir: SearchDir): SearchState = when (state.pane) {
        SearchPane.Keyboard -> moveKeyboard(state, dir)
        SearchPane.Actions -> moveActions(state, dir)
        SearchPane.Results -> moveResults(state, dir)
    }

    private fun moveKeyboard(state: SearchState, dir: SearchDir): SearchState {
        val col = state.keyIndex % SearchKeys.COLUMNS
        val row = state.keyIndex / SearchKeys.COLUMNS
        val next = when (dir) {
            SearchDir.Left -> if (col > 0) state.keyIndex - 1 else state.keyIndex
            SearchDir.Right -> if (col < SearchKeys.COLUMNS - 1) state.keyIndex + 1 else state.keyIndex
            SearchDir.Up -> if (row > 0) state.keyIndex - SearchKeys.COLUMNS else state.keyIndex
            SearchDir.Down -> when {
                row < SearchKeys.ROWS - 1 -> state.keyIndex + SearchKeys.COLUMNS
                else -> -1
            }
        }
        return if (next < 0) {
            state.copy(pane = SearchPane.Actions, actionIndex = 0)
        } else {
            state.copy(keyIndex = next)
        }
    }

    private fun moveActions(state: SearchState, dir: SearchDir): SearchState = when (dir) {
        SearchDir.Left -> state.copy(actionIndex = (state.actionIndex - 1).coerceAtLeast(0))
        SearchDir.Right -> state.copy(
            actionIndex = (state.actionIndex + 1).coerceAtMost(SearchKeys.actions.lastIndex),
        )
        SearchDir.Up -> state.copy(
            pane = SearchPane.Keyboard,
            keyIndex = (SearchKeys.ROWS - 1) * SearchKeys.COLUMNS +
                (state.actionIndex * 2).coerceAtMost(SearchKeys.COLUMNS - 1),
        )
        SearchDir.Down -> if (state.results.isEmpty()) {
            state
        } else {
            state.copy(pane = SearchPane.Results, resultIndex = state.resultIndex.coerceIn(0, state.results.lastIndex))
        }
    }

    private fun moveResults(state: SearchState, dir: SearchDir): SearchState {
        if (state.results.isEmpty()) return state.copy(pane = SearchPane.Actions)
        val index = state.resultIndex.coerceIn(0, state.results.lastIndex)
        val col = index % RESULT_COLUMNS
        val lastRowStart = (state.results.lastIndex / RESULT_COLUMNS) * RESULT_COLUMNS
        return when (dir) {
            SearchDir.Left -> state.copy(resultIndex = if (col > 0) index - 1 else index)
            SearchDir.Right -> {
                val next = index + 1
                if (col < RESULT_COLUMNS - 1 && next <= state.results.lastIndex) {
                    state.copy(resultIndex = next)
                } else {
                    state
                }
            }
            SearchDir.Up -> if (index < RESULT_COLUMNS) {
                state.copy(pane = SearchPane.Actions)
            } else {
                state.copy(resultIndex = index - RESULT_COLUMNS)
            }
            SearchDir.Down -> {
                val next = index + RESULT_COLUMNS
                if (next <= state.results.lastIndex) {
                    state.copy(resultIndex = next)
                } else {
                    state.copy(resultIndex = index.coerceAtLeast(lastRowStart).coerceAtMost(state.results.lastIndex))
                }
            }
        }
    }

    fun append(state: SearchState, text: String): SearchState =
        state.copy(query = (state.query + text.lowercase()).take(64))

    fun activate(state: SearchState): Pair<SearchState, MediaItem?> = when (state.pane) {
        SearchPane.Keyboard -> {
            val char = SearchKeys.chars.getOrNull(state.keyIndex) ?: return state to null
            append(state, char) to null
        }
        SearchPane.Actions -> when (state.actionIndex) {
            0 -> append(state, " ") to null
            1 -> state.copy(query = state.query.dropLast(1)) to null
            else -> state.copy(query = "") to null
        }
        SearchPane.Results -> {
            val item = state.results.getOrNull(state.resultIndex)
            state to item
        }
    }

    fun back(state: SearchState): Pair<SearchState, Boolean> = if (state.pane == SearchPane.Keyboard) {
        state to true // true = finish the screen
    } else {
        state.copy(pane = SearchPane.Keyboard) to false
    }

    fun withResults(
        state: SearchState,
        results: List<MediaItem>,
        searching: Boolean = false,
        error: String? = null,
    ): SearchState {
        val focused = state.pane == SearchPane.Results
        return state.copy(
            results = results,
            searching = searching,
            error = error,
            resultIndex = state.resultIndex.coerceIn(0, maxOf(0, results.lastIndex)),
            pane = if (focused && results.isEmpty()) SearchPane.Actions else state.pane,
        )
    }
}
