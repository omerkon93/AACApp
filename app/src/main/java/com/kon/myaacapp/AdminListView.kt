package com.kon.myaacapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AdminListView(
    viewModel: AACViewModel,
    onEditTile: (AACTile?) -> Unit,
    onDeleteTile: (AACTile) -> Unit
) {
    val tiles by viewModel.filteredTilesForAdmin.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val searchQuery by viewModel.adminSearchQuery.collectAsState()
    val selectedFilter by viewModel.adminAuditFilter.collectAsState()
    val recordingTileId by viewModel.recordingTileId.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetStatsConfirm by remember { mutableStateOf(false) }
    var showRemoveAudioConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(importExportStatus) {
        importExportStatus?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearImportExportStatus()
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_warning_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetToDefault(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResetStatsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetStatsConfirm = false },
            title = { Text(stringResource(R.string.reset_stats_title)) },
            text = { Text(stringResource(R.string.reset_stats_warning_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetStatsConfirm = false
                        viewModel.resetStatistics(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRemoveAudioConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAudioConfirm = false },
            title = { Text(stringResource(R.string.remove_all_audio_title)) },
            text = { Text(stringResource(R.string.remove_all_audio_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveAudioConfirm = false
                        viewModel.removeAllAudio(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAudioConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setAdminSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.search_tiles_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setAdminSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_action))
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Audit Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminAuditFilter.entries.forEach { filter ->
                val label = when (filter) {
                    AdminAuditFilter.ALL -> stringResource(R.string.filter_all)
                    AdminAuditFilter.MISSING_AUDIO -> stringResource(R.string.filter_missing_audio)
                    AdminAuditFilter.MISSING_TTS -> stringResource(R.string.filter_missing_tts)
                    AdminAuditFilter.MISSING_IMAGE -> stringResource(R.string.filter_missing_image)
                    AdminAuditFilter.UNUSED -> stringResource(R.string.filter_unused)
                    AdminAuditFilter.HIDDEN -> stringResource(R.string.filter_hidden)
                }

                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { viewModel.setAdminAuditFilter(filter) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.database_management),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.factory_reset_button))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showResetStatsConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reset_stats_button))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showRemoveAudioConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.remove_all_audio_button))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onEditTile(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_new_tile_button))
                        }
                    }
                }
            }

            items(tiles, key = { it.id }) { tile ->
                val isRecording = recordingTileId == tile.id
                AdminTileListCard(
                    tile = tile,
                    isRecording = isRecording,
                    onEdit = { onEditTile(tile) },
                    onDelete = { onDeleteTile(tile) },
                    onStartRecord = { viewModel.startQuickRecording(tile.id) },
                    onStopRecord = { viewModel.stopQuickRecording(tile.id) },
                    onPlayAudio = { viewModel.playPreviewAudio(tile.ttsText, tile.audioUri) }
                )
            }
        }
    }
}

@Composable
fun AdminTileListCard(
    tile: AACTile,
    isRecording: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPlayAudio: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tile Image/Emoji
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (isRecording) MaterialTheme.colorScheme.errorContainer 
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tile.imageUri != null) {
                    AsyncImage(
                        model = tile.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = tile.emoji ?: "✨", fontSize = 32.sp)
                }
                
                if (isRecording) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 4.dp
                    )
                }
            }

            // Tile Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.ttsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tile.audioUri != null) {
                    Text(
                        text = "🔊 Custom Audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tile.audioUri != null && !isRecording) {
                    IconButton(onClick = onPlayAudio) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play_action),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = {
                    if (isRecording) onStopRecord() else onStartRecord()
                }) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(
                            if (isRecording) R.string.stop_record_action else R.string.quick_record_action
                        ),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_action),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
