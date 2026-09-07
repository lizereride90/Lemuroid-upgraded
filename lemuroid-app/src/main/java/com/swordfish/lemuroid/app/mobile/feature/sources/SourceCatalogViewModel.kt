package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.sources.GameCatalogDownloadState
import com.swordfish.lemuroid.app.sources.GameDownloadCoordinator
import com.swordfish.lemuroid.app.sources.InstalledSource
import com.swordfish.lemuroid.app.sources.ResolvedSystem
import com.swordfish.lemuroid.app.sources.SourceGame
import com.swordfish.lemuroid.app.sources.SourcesManager
import com.swordfish.lemuroid.app.sources.SystemResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class CatalogEntry(
    val installed: InstalledSource,
    val game: SourceGame,
    val resolvedSystem: ResolvedSystem?,
)

class SourceCatalogViewModel(
    private val sourceId: String?,
    private val sourcesManager: SourcesManager,
    private val coordinator: GameDownloadCoordinator,
) : ViewModel() {
    data class UiState(
        val allSources: List<InstalledSource> = emptyList(),
        val allGames: List<CatalogEntry> = emptyList(),
        val sourceId: String? = null,
        val searchQuery: String = "",
        val systemFilter: String? = null,
    ) {
        val games: List<CatalogEntry>
            get() = if (sourceId == null) allGames else allGames.filter { it.installed.id == sourceId }

        val filteredGames: List<CatalogEntry>
            get() {
                val query = searchQuery.trim().lowercase(Locale.US)
                return games.filter { entry ->
                    val systemMatch = systemFilter == null || entry.resolvedSystem?.dbname == systemFilter
                    val queryMatch = query.isEmpty() || entry.game.title.lowercase(Locale.US).contains(query)
                    systemMatch && queryMatch
                }
            }

        fun allGamesAt(activeSourceId: String?): List<CatalogEntry> = allGames
    }

    private val _state = MutableStateFlow(UiState(sourceId = sourceId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val downloadStates: StateFlow<Map<String, GameCatalogDownloadState>> = coordinator.states

    init {
        viewModelScope.launch {
            sourcesManager.sources.collect { installedList ->
                val loaded =
                    installedList.mapNotNull { installed ->
                        runCatching {
                            val catalog = sourcesManager.loadCatalog(installed)
                            catalog.games.map {
                                CatalogEntry(
                                    installed = installed,
                                    game = it,
                                    resolvedSystem = SystemResolver.resolve(it.system),
                                )
                            }
                        }.getOrNull()
                    }.flatten()
                _state.value = _state.value.copy(allSources = installedList, allGames = loaded)
            }
        }
    }

    val filteredGames: List<CatalogEntry>
        get() = _state.value.filteredGames

    fun setSourceFilter(id: String?) {
        _state.value = _state.value.copy(sourceId = id)
        _state.value = _state.value.copy(systemFilter = null)
    }

    fun setSystemFilter(dbname: String?) {
        _state.value = _state.value.copy(systemFilter = dbname)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun keyFor(
        installed: InstalledSource,
        game: SourceGame,
    ): String = coordinator.key(installed.id, game.id)

    fun startDownload(
        installed: InstalledSource,
        game: SourceGame,
    ) {
        coordinator.start(installed, game)
    }

    fun pauseDownload(key: String) = coordinator.pause(key)

    fun resumeDownload(key: String) = coordinator.resume(key)

    fun cancelDownload(key: String) = coordinator.cancel(key)

    fun coverFile(
        installed: InstalledSource,
        game: SourceGame,
    ): File? = sourcesManager.coverFile(installed, game)

    class Factory(
        private val sourceId: String?,
        private val sourcesManager: SourcesManager,
        private val coordinator: GameDownloadCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SourceCatalogViewModel(sourceId, sourcesManager, coordinator) as T
        }
    }
}