package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.sources.GameCatalogDownloadState
import com.swordfish.lemuroid.app.sources.InstalledSource
import com.swordfish.lemuroid.app.sources.ResolvedSystem
import com.swordfish.lemuroid.app.sources.SourceGame
import com.swordfish.lemuroid.app.sources.SystemResolver
import java.io.File
import java.util.Locale

@Composable
fun SourceCatalogScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceCatalogViewModel,
    onGameClicked: (InstalledSource, SourceGame) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val allSystems = remember { SystemResolver.allSystems() }
    val systems: List<ResolvedSystem> =
        allSystems.filter { sys ->
            state.games.any { it.resolvedSystem?.dbname == sys.dbname }
        }
    val sourceIds = state.allSources.map { it.id }.sorted()

    Column(modifier = modifier.fillMaxSize()) {
        // System filters
        if (systems.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.systemFilter == null,
                        onClick = { viewModel.setSystemFilter(null) },
                        label = { Text(stringResource(R.string.game_sources_filter_all)) },
                    )
                }
                items(systems.size, key = { systems[it].dbname }) { index ->
                    val sys = systems[index]
                    FilterChip(
                        selected = state.systemFilter == sys.dbname,
                        onClick = { viewModel.setSystemFilter(sys.dbname) },
                        label = { Text(sys.dbname.uppercase(Locale.US)) },
                    )
                }
            }
        }

        // Source filters
        if (sourceIds.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.sourceId == null,
                        onClick = { viewModel.setSourceFilter(null) },
                        label = { Text(stringResource(R.string.game_sources_filter_all)) },
                    )
                }
                items(sourceIds.size, key = { "src-${sourceIds[it]}" }) { index ->
                    val id = sourceIds[index]
                    val name = state.allSources.firstOrNull { it.id == id }?.name ?: id
                    FilterChip(
                        selected = state.sourceId == id,
                        onClick = { viewModel.setSourceFilter(id) },
                        label = { Text(name) },
                    )
                }
            }
        }

        // Search field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.game_sources_search_hint)) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
        )

        if (state.filteredGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text =
                        if (state.games.isEmpty()) {
                            stringResource(R.string.game_sources_no_catalog)
                        } else {
                            stringResource(R.string.game_sources_no_results)
                        },
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.filteredGames.size) { index ->
                val entry = state.filteredGames[index]
                val key = viewModel.keyFor(entry.installed, entry.game)
                val status = downloadStates[key]
                val coverFile = viewModel.coverFile(entry.installed, entry.game)
                CatalogGameCard(
                    installed = entry.installed,
                    game = entry.game,
                    coverFile = coverFile,
                    state = status,
                    onDownload = { viewModel.startDownload(entry.installed, entry.game) },
                    onPause = { viewModel.pauseDownload(key) },
                    onResume = { viewModel.resumeDownload(key) },
                    onCancel = { viewModel.cancelDownload(key) },
                    onClick = { onGameClicked(entry.installed, entry.game) },
                )
            }
        }
    }
}

@Composable
internal fun CatalogGameCard(
    installed: InstalledSource,
    game: SourceGame,
    coverFile: File?,
    state: GameCatalogDownloadState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            if (coverFile != null) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = game.title.take(1).uppercase(Locale.US),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(10.dp),
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val systemLabel = game.system.uppercase(Locale.US)
                Text(
                    text = "$systemLabel • ${formatSize(game.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                when (state) {
                    null -> {
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.game_sources_download))
                        }
                    }
                    is GameCatalogDownloadState.Active -> {
                        LinearProgressIndicator(
                            progress = { progressFraction(state) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        val sizeLabel = state.totalBytes?.let { formatSize(it) } ?: "?"
                        Text(
                            text = "${formatSize(state.downloadedBytes)} / $sizeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        androidx.compose.foundation.layout.Row {
                            TextButton(onClick = onPause) { Text(stringResource(R.string.game_sources_pause)) }
                            TextButton(onClick = onCancel) { Text(stringResource(R.string.game_sources_cancel)) }
                        }
                    }
                    is GameCatalogDownloadState.Paused -> {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.game_sources_resume))
                        }
                    }
                    is GameCatalogDownloadState.WaitingRetry -> {
                        Text(
                            text = stringResource(R.string.game_sources_waiting_retry, state.delayMillis / 1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    is GameCatalogDownloadState.Completed -> {
                        Text(
                            text = stringResource(R.string.game_sources_installed),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    GameCatalogDownloadState.Cancelled -> Unit
                    is GameCatalogDownloadState.Failed -> {
                        Text(
                            text = stringResource(R.string.game_sources_download_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(onClick = onDownload) { Text(stringResource(R.string.game_sources_download)) }
                    }
                }
            }
        }
    }
}

internal fun progressFraction(state: GameCatalogDownloadState.Active): Float {
    val total = state.totalBytes
    if (total != null && total > 0) {
        return (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    return 0f
}

internal fun formatSize(bytes: Long?): String {
    if (bytes == null) return "?"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
