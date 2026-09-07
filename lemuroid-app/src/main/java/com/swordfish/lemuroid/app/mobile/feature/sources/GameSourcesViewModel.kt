package com.swordfish.lemuroid.app.mobile.feature.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.sources.InstalledSource
import com.swordfish.lemuroid.app.sources.SourceException
import com.swordfish.lemuroid.app.sources.SourcesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameSourcesViewModel(private val sourcesManager: SourcesManager) : ViewModel() {
    data class UiState(
        val sources: List<InstalledSource> = emptyList(),
        val installing: Boolean = false,
        val refreshErrors: Map<String, String> = emptyMap(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(sources = sourcesManager.sources.value))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sourcesManager.sources.collect { sources ->
                _state.value = _state.value.copy(sources = sources)
            }
        }
    }

    fun install(uri: Uri) {
        _state.value = _state.value.copy(installing = true, error = null)
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { sourcesManager.install(uri) }
                }
            result.fold(
                onSuccess = { _state.value = _state.value.copy(installing = false, error = null) },
                onFailure = { e ->
                    val message =
                        when (e) {
                            is SourceException -> e.message ?: "Could not install the source"
                            else -> e.message ?: "Could not install the source"
                        }
                    _state.value = _state.value.copy(installing = false, error = message)
                },
            )
        }
    }

    fun onInstalled() {
        _state.value = _state.value.copy(installing = false, error = null)
    }

    fun onInstallError(message: String) {
        _state.value = _state.value.copy(installing = false, error = message)
    }

    fun onRefresh(id: String) {
        viewModelScope.launch {
            try {
                sourcesManager.refresh(id)
                _state.value = _state.value.copy(refreshErrors = _state.value.refreshErrors - id)
            } catch (e: SourceException) {
                _state.value = _state.value.copy(refreshErrors = _state.value.refreshErrors + (id to refreshMessage(e)))
            } catch (e: Exception) {
                _state.value = _state.value.copy(refreshErrors = _state.value.refreshErrors + (id to refreshMessage(e)))
            }
        }
    }

    private fun refreshMessage(e: Exception): String = e.message ?: "Refresh failed"

    fun onDelete(id: String) {
        sourcesManager.delete(id)
    }

    class Factory(private val sourcesManager: SourcesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameSourcesViewModel(sourcesManager) as T
        }
    }
}
