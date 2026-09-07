package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.sources.GameCatalogDownloadState
import com.swordfish.lemuroid.app.sources.GameDownloadCoordinator
import com.swordfish.lemuroid.app.sources.InstalledSource
import com.swordfish.lemuroid.app.sources.SourceGame
import com.swordfish.lemuroid.app.sources.SourcesManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SourceGameDetailViewModel(
    private val sourceId: String,
    private val gameId: String,
    private val sourcesManager: SourcesManager,
    private val coordinator: GameDownloadCoordinator,
    private val retrogradeDb: RetrogradeDatabase,
    private val gameInteractor: GameInteractor,
) : ViewModel() {
    data class UiState(
        val installed: InstalledSource? = null,
        val game: SourceGame? = null,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val downloadStates: StateFlow<Map<String, GameCatalogDownloadState>> = coordinator.states

    init {
        viewModelScope.launch {
            val installed =
                sourcesManager.findInstalled(sourceId)
                    ?: run {
                        _state.value = UiState(loaded = true)
                        return@launch
                    }
            val catalog = runCatching { sourcesManager.loadCatalog(installed) }.getOrNull()
            val game = catalog?.games?.firstOrNull { it.id == gameId }
            _state.value = UiState(installed = installed, game = game, loaded = true)
        }
    }

    fun coverFile(): File? {
        val installed = _state.value.installed ?: return null
        val game = _state.value.game ?: return null
        return sourcesManager.coverFile(installed, game)
    }

    fun downloadKey(): String? {
        val installed = _state.value.installed ?: return null
        val game = _state.value.game ?: return null
        return coordinator.key(installed.id, game.id)
    }

    fun startDownload() {
        val installed = _state.value.installed ?: return
        val game = _state.value.game ?: return
        coordinator.start(installed, game)
    }

    fun pauseDownload() = downloadKey()?.let { coordinator.pause(it) }

    fun resumeDownload() = downloadKey()?.let { coordinator.resume(it) }

    fun cancelDownload() {
        downloadKey()?.let { coordinator.cancel(it) }
        _state.value = _state.value
    }

    fun openGame() {
        val key = downloadKey() ?: return
        val downloadState = downloadStates.value[key]
        val installedGameId =
            when (downloadState) {
                is GameCatalogDownloadState.Completed -> downloadState.gameId
                else -> null
            }
        if (installedGameId == null) return
        viewModelScope.launch {
            val game = retrogradeDb.gameDao().selectById(installedGameId)
            if (game != null) {
                gameInteractor.onGamePlay(game)
            }
        }
    }

    class Factory(
        private val sourceId: String,
        private val gameId: String,
        private val sourcesManager: SourcesManager,
        private val coordinator: GameDownloadCoordinator,
        private val retrogradeDb: RetrogradeDatabase,
        private val gameInteractor: GameInteractor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SourceGameDetailViewModel(
                sourceId,
                gameId,
                sourcesManager,
                coordinator,
                retrogradeDb,
                gameInteractor,
            ) as T
        }
    }
}
