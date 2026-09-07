package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.sources.GameCatalogDownloadState
import com.swordfish.lemuroid.app.sources.SourceGame
import java.io.File
import java.util.Locale

@Composable
fun SourceGameDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceGameDetailViewModel,
) {
    val state by viewModel.state.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    if (!state.loaded) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val installed = state.installed ?: return
    val game = state.game
    if (game == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.game_sources_game_not_found))
        }
        return
    }

    val key = viewModel.downloadKey()
    val downloadState = key?.let { downloadStates[it] }
    val coverFile = viewModel.coverFile()

    SourceGameDetailContent(
        modifier = modifier,
        game = game,
        coverFile = coverFile,
        downloadState = downloadState,
        onDownload = { viewModel.startDownload() },
        onPause = { viewModel.pauseDownload() },
        onResume = { viewModel.resumeDownload() },
        onCancel = { viewModel.cancelDownload() },
        onOpen = { viewModel.openGame() },
    )
}

@Composable
private fun SourceGameDetailContent(
    modifier: Modifier = Modifier,
    game: SourceGame,
    coverFile: File?,
    downloadState: GameCatalogDownloadState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard {
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
                    )
                }
            }
        }

        Text(
            text = game.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        val systemLabel = game.system.uppercase(Locale.US)
        Text(
            text = "$systemLabel • ${formatSize(game.size)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        DownloadControlsSection(
            game = game,
            state = downloadState,
            onDownload = onDownload,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
        )

        if (downloadState is GameCatalogDownloadState.Completed) {
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.game_sources_open_game))
            }
        }

        game.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        game.version?.let { version ->
            Text(
                text = stringResource(R.string.game_sources_version) + ": $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DownloadControlsSection(
    game: SourceGame,
    state: GameCatalogDownloadState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            null -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_sources_download))
                }
            }
            is GameCatalogDownloadState.Active -> {
                LinearProgressIndicator(
                    progress = { progressFraction(state) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${formatSize(state.downloadedBytes)} / ${state.totalBytes?.let { formatSize(it) } ?: "?"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val speedLabel =
                    String.format(Locale.US, "%.1f MB/s", state.speedBytesPerSecond / (1024.0 * 1024.0))
                Text(
                    text = speedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = onPause) { Text(stringResource(R.string.game_sources_pause)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.game_sources_cancel)) }
                }
            }
            is GameCatalogDownloadState.Paused -> {
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_sources_resume))
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_sources_cancel))
                }
            }
            is GameCatalogDownloadState.WaitingRetry -> {
                Text(
                    text = stringResource(R.string.game_sources_waiting_retry, state.delayMillis / 1000),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is GameCatalogDownloadState.Completed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.game_sources_installed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            GameCatalogDownloadState.Cancelled -> Unit
            is GameCatalogDownloadState.Failed -> {
                Text(
                    text = stringResource(R.string.game_sources_download_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.game_sources_download))
                }
            }
        }
    }
}
