package com.example.mobilepelacak.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilepelacak.domain.model.HistoryDevice
import com.example.mobilepelacak.domain.repository.HistoryRepository
import com.example.mobilepelacak.domain.repository.TimeProvider
import com.example.mobilepelacak.ui.common.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val devices: List<HistoryDevice> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val now: Long = 0L,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val savedStateHandle: SavedStateHandle,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeHistory(),
        query,
        // Refresh berkala agar label "x mnt lalu" tetap akurat.
        tickerFlow(15_000L, timeProvider),
    ) { all, q, now ->
        val trimmed = q.trim()
        val filtered = if (trimmed.isEmpty()) all else all.filter {
            it.name?.contains(trimmed, ignoreCase = true) == true ||
                it.address.contains(trimmed, ignoreCase = true) ||
                it.address.replace(":", "").contains(trimmed.replace(":", ""), ignoreCase = true)
        }
        HistoryUiState(
            isLoading = false,
            devices = filtered,
            totalCount = all.size,
            query = q,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun delete(address: String) {
        viewModelScope.launch { historyRepository.delete(address) }
    }

    fun clearAll() {
        viewModelScope.launch { historyRepository.clear() }
    }

    private companion object {
        const val KEY_QUERY = "history_query"
    }
}
