package com.swordfish.lemuroid.app.mobile.feature.sources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

@Composable
fun SourceDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceDetailsViewModel,
    onBrowseCatalog: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    if (!state.loaded) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val installed = state.installed
    if (installed == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.game_sources_not_found))
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Icon(
            Icons.Filled.VideogameAsset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = installed.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        state.manifest?.let { manifest ->
            manifest.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            InfoRow(stringResource(R.string.game_sources_author), manifest.author)
            manifest.version?.let { InfoRow(stringResource(R.string.game_sources_version), it) }
        }
        InfoRow(
            stringResource(R.string.game_sources_installed_date),
            installed.installedAt?.toString() ?: "—",
        )
        InfoRow(
            stringResource(R.string.game_sources_last_updated),
            installed.lastUpdated?.toString() ?: "—",
        )
        InfoRow(stringResource(R.string.game_sources_games_count_short), installed.gameCount.toString())

        state.refreshError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { onBrowseCatalog(installed.id) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.game_sources_browse_catalog))
        }

        Button(
            onClick = { viewModel.onRefresh() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.game_sources_refresh))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        HorizontalDivider()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}