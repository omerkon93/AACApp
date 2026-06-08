package com.kon.myaacapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
    onEditTile: (AACTile) -> Unit,
    onDeleteTile: (AACTile) -> Unit
) {
    val tiles by viewModel.allTiles.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val filteredTiles = remember(searchQuery, tiles) {
        if (searchQuery.isBlank()) {
            tiles
        } else {
            tiles.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.ttsText.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            placeholder = { Text(stringResource(R.string.search_tiles_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_action))
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

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
                    }
                }
            }

            items(filteredTiles, key = { it.id }) { tile ->
                AdminTileListCard(
                    tile = tile,
                    onEdit = { onEditTile(tile) },
                    onDelete = { onDeleteTile(tile) },
                    onTestAudio = {
                        viewModel.playPreviewAudio(tile.ttsText, tile.audioUri)
                    }
                )
            }
        }
    }
}

@Composable
fun AdminTileListCard(
    tile: AACTile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestAudio: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTestAudio) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.test_audio),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
