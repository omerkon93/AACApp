package com.kon.myaacapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun TileActionDialog(
    tile: AACTile,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onOpen: (() -> Unit)? = null
) {
    // OPTIMIZATION: Cache static modifiers to prevent minor heap allocations
    val fillWidthModifier = remember { Modifier.fillMaxWidth() }
    val roundedShape = remember { RoundedCornerShape(12.dp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tile_action_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = fillWidthModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(roundedShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (tile.imageUri != null) {
                        AsyncImage(
                            model = tile.imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(tile.emoji ?: "✨", fontSize = 40.sp)
                    }
                }

                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (onOpen != null) {
                    Button(
                        onClick = onOpen, // Function reference passed directly, highly optimal
                        modifier = fillWidthModifier,
                        shape = roundedShape
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open_category))
                    }
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = fillWidthModifier,
                    shape = roundedShape
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_edit_tile))
                }

                Button(
                    onClick = onRemove,
                    modifier = fillWidthModifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = roundedShape
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_remove_from_screen))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun TilePickerDialog(
    viewModel: AACViewModel,
    onDismiss: () -> Unit,
    onTileSelected: (CombinedTile?) -> Unit
) {
    val allTiles by viewModel.allTiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredTiles = remember(searchQuery, allTiles) {
        if (searchQuery.isBlank()) {
            allTiles
        } else {
            allTiles.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                        it.ttsText.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // OPTIMIZATION: Memoize the "Add New" click action to prevent lambda reallocation
    val onAddNewClick = remember(onTileSelected) { { onTileSelected(null) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_or_create_tile), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_existing_tile)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // OPTIMIZATION: Added a hardcoded key so this item never re-renders during filtering
                    item(key = "ADD_NEW_TILE_BUTTON") {
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable(onClick = onAddNewClick),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text(stringResource(R.string.new_tile), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    items(
                        items = filteredTiles,
                        // OPTIMIZATION: Added the unique key mapping. Compose now tracks node movement in O(1) time.
                        key = { it.id }
                    ) { tile ->
                        // OPTIMIZATION: Extracted to a scoped private composable to enable Strong Skipping.
                        PickerTileItem(
                            tile = tile,
                            onTileSelected = onTileSelected
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// OPTIMIZATION: Private scoped composable. Because `CombinedTile` is @Immutable,
// this function will completely skip recomposition unless the specific tile data changes.
@Composable
private fun PickerTileItem(
    tile: CombinedTile,
    onTileSelected: (CombinedTile?) -> Unit
) {
    // Memoize the specific click lambda for this tile ID
    val onClick = remember(tile.id, onTileSelected) { { onTileSelected(tile) } }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (tile.imageUri != null) {
                AsyncImage(
                    model = tile.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = tile.emoji ?: "✨",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 24.sp
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    tile.label,
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}