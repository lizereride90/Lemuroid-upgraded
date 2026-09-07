package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.sources.InstalledSource
import com.swordfish.lemuroid.app.sources.SourceException
import com.swordfish.lemuroid.app.sources.SourceManifest
import com.swordfish.lemuroid.app.sources.SourcesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SourceDetailsViewModel(
    private val sourceId: String,
    private val sourcesManager: SourcesManager,
) : ViewModel() {
    data class UiState(
        val installed: InstalledSource? = null,
        val manifest: SourceManifest? = null,
        val loaded: Boolean = false,
        val refreshError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val installed =
                sourcesManager.findInstalled(sourceId)
                    ?: run {
                        _state.value = UiState(loaded = true)
                        return@launch
                    }
            val manifest = runCatching { sourcesManager.readManifest(installed) }.getOrNull()
            _state.value = UiState(installed = installed, manifest = manifest, loaded = true)
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            try {
                sourcesManager.refresh(sourceId)
                val installed = sourcesManager.findInstalled(sourceId)
                val manifest =
                    installed?.let { runCatching { sourcesManager.readManifest(it) }.getOrNull() }
                _state.value = _state.value.copy(installed = installed, manifest = manifest, refreshError = null)
            } catch (e: SourceException) {
                _state.value = _state.value.copy(refreshError = e.message ?: "Refresh failed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(refreshError = e.message ?: "Refresh failed")
            }
        }
    }

    class Factory(
        private val sourceId: String,
        private val sourcesManager: SourcesManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SourceDetailsViewModel(sourceId, sourcesManager) as T
        }
    }
}
