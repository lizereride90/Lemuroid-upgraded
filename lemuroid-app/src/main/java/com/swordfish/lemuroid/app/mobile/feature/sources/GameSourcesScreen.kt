package com.swordfish.lemuroid.app.mobile.feature.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.sources.InstalledSource

@Composable
fun GameSourcesScreen(
    modifier: Modifier = Modifier,
    viewModel: GameSourcesViewModel,
    onBrowse: (String) -> Unit,
    onShowDetails: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    var pickPending by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                viewModel.install(uri)
            }
        }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { pickPending = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.game_sources_add))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.installing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.game_sources_installing),
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else if (state.sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.game_sources_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.sources.size, key = { state.sources[it].id }) { index ->
                        val source = state.sources[index]
                        SourceCard(
                            source = source,
                            refreshError = state.refreshErrors[source.id],
                            onBrowse = { onBrowse(source.id) },
                            onRefresh = { viewModel.onRefresh(source.id) },
                            onDelete = { viewModel.onDelete(source.id) },
                            onDetails = { onShowDetails(source.id) },
                        )
                    }
                }
            }

            state.error?.let { message ->
                AlertDialog(
                    onDismissRequest = { viewModel.onInstalled() },
                    title = { Text(stringResource(R.string.game_sources_install_error_title)) },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onInstalled() }) { Text(stringResource(R.string.ok)) }
                    },
                )
            }
        }
    }

    if (pickPending) {
        picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        pickPending = false
    }
}

@Composable
private fun SourceCard(
    source: InstalledSource,
    refreshError: String?,
    onBrowse: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.game_sources_games_count, source.gameCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.game_sources_details)) },
                        onClick = {
                            menuExpanded = false
                            onDetails()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.game_sources_refresh)) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.game_sources_delete)) },
                        onClick = {
                            menuExpanded = false
                            confirmDelete = true
                        },
                    )
                }
            }

            if (refreshError != null) {
                Text(
                    text = refreshError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TextButton(
                onClick = onBrowse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.game_sources_browse_catalog))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.game_sources_delete_confirm_title)) },
            text = { Text(stringResource(R.string.game_sources_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.game_sources_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.game_sources_delete_confirm_no))
                }
            },
        )
    }
}