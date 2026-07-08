package com.kon.myaacapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class MediaFilter { MISSING_AUDIO, MISSING_TTS, MISSING_IMAGE }
enum class UsageFilter { LOW_USAGE, HIDDEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminListView(
    viewModel: AACViewModel,
    onEditTile: (AACTile?) -> Unit,
    onDeleteTile: (AACTile) -> Unit
) {
    val allTiles by viewModel.allTiles.collectAsState()

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var isFilterActive by remember { mutableStateOf(false) }

    // Filter Sets
    var selectedMediaFilters by remember { mutableStateOf(setOf<MediaFilter>()) }
    var selectedTypes by remember { mutableStateOf(setOf<TileType>()) }
    var selectedUsageFilters by remember { mutableStateOf(setOf<UsageFilter>()) }

    val activeFilterCount = selectedMediaFilters.size + selectedTypes.size + selectedUsageFilters.size

    // Bottom Sheet State
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dynamic Filtering Logic
    val filteredTiles = remember(allTiles, searchQuery, isFilterActive, selectedMediaFilters, selectedTypes, selectedUsageFilters) {
        var currentList = allTiles

        // 1. Apply Search
        if (searchQuery.isNotBlank()) {
            currentList = currentList.filter {
                it.definition.label.contains(searchQuery, ignoreCase = true) ||
                        it.definition.ttsText.contains(searchQuery, ignoreCase = true)
            }
        }

        // 2. Apply Filters (Only if the master toggle is ON)
        if (isFilterActive && activeFilterCount > 0) {

            if (selectedMediaFilters.contains(MediaFilter.MISSING_AUDIO)) {
                currentList = currentList.filter { it.definition.audioUri == null }
            }
            if (selectedMediaFilters.contains(MediaFilter.MISSING_TTS)) {
                currentList = currentList.filter { it.definition.ttsText.isBlank() }
            }
            if (selectedMediaFilters.contains(MediaFilter.MISSING_IMAGE)) {
                currentList = currentList.filter { it.definition.imageUri == null && it.definition.emoji == null }
            }

            if (selectedTypes.isNotEmpty()) {
                currentList = currentList.filter { selectedTypes.contains(it.definition.resolvedType) }
            }

            if (selectedUsageFilters.contains(UsageFilter.HIDDEN)) {
                currentList = currentList.filter { it.layoutState.isHidden }
            }
            if (selectedUsageFilters.contains(UsageFilter.LOW_USAGE)) {
                currentList = currentList.filter { it.layoutState.clickCount < 5 }
            }
        }

        currentList
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_tiles_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isFilterActive = true
                                showFilterSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeFilterCount > 0 && isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (activeFilterCount > 0 && isFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (activeFilterCount > 0)
                                    stringResource(R.string.filter_by_count, activeFilterCount)
                                else
                                    stringResource(R.string.filter_by)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(100.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(stringResource(R.string.filter_active), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = isFilterActive,
                                    onCheckedChange = { isFilterActive = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTiles, key = { it.definition.id }) { tile ->
                    AdminListRowItem(
                        tile = tile,
                        onEdit = { onEditTile(tile.toLegacyAACTile()) },
                        onDelete = { onDeleteTile(tile.toLegacyAACTile()) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onEditTile(null) },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add), tint = Color.White)
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter_by),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // SECTION 1: MEDIA STATUS
                    Column {
                        Text(stringResource(R.string.filter_section_media), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        FilterCheckboxRow(
                            label = stringResource(R.string.filter_missing_audio),
                            isChecked = selectedMediaFilters.contains(MediaFilter.MISSING_AUDIO)
                        ) { isChecked ->
                            selectedMediaFilters = if (isChecked) selectedMediaFilters + MediaFilter.MISSING_AUDIO else selectedMediaFilters - MediaFilter.MISSING_AUDIO
                        }
                        FilterCheckboxRow(
                            label = stringResource(R.string.filter_missing_tts),
                            isChecked = selectedMediaFilters.contains(MediaFilter.MISSING_TTS)
                        ) { isChecked ->
                            selectedMediaFilters = if (isChecked) selectedMediaFilters + MediaFilter.MISSING_TTS else selectedMediaFilters - MediaFilter.MISSING_TTS
                        }
                        FilterCheckboxRow(
                            label = stringResource(R.string.filter_missing_image),
                            isChecked = selectedMediaFilters.contains(MediaFilter.MISSING_IMAGE)
                        ) { isChecked ->
                            selectedMediaFilters = if (isChecked) selectedMediaFilters + MediaFilter.MISSING_IMAGE else selectedMediaFilters - MediaFilter.MISSING_IMAGE
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // SECTION 2: TILE TYPE
                    Column {
                        Text(stringResource(R.string.filter_section_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        TileType.entries.forEach { type ->
                            val labelRes = when(type) {
                                TileType.BASIC -> R.string.tile_type_basic
                                TileType.FOLDER -> R.string.tile_type_folder
                                TileType.CONNECTOR -> R.string.tile_type_connector
                                TileType.QUICK_FIRE -> R.string.tile_type_quick_fire
                            }
                            FilterCheckboxRow(
                                label = stringResource(labelRes),
                                isChecked = selectedTypes.contains(type)
                            ) { isChecked ->
                                selectedTypes = if (isChecked) selectedTypes + type else selectedTypes - type
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // SECTION 3: TILE USAGE
                    Column {
                        Text(stringResource(R.string.filter_section_usage), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        FilterCheckboxRow(
                            label = stringResource(R.string.filter_usage_low),
                            isChecked = selectedUsageFilters.contains(UsageFilter.LOW_USAGE)
                        ) { isChecked ->
                            selectedUsageFilters = if (isChecked) selectedUsageFilters + UsageFilter.LOW_USAGE else selectedUsageFilters - UsageFilter.LOW_USAGE
                        }
                        FilterCheckboxRow(
                            label = stringResource(R.string.filter_usage_hidden),
                            isChecked = selectedUsageFilters.contains(UsageFilter.HIDDEN)
                        ) { isChecked ->
                            selectedUsageFilters = if (isChecked) selectedUsageFilters + UsageFilter.HIDDEN else selectedUsageFilters - UsageFilter.HIDDEN
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedMediaFilters = emptySet()
                            selectedTypes = emptySet()
                            selectedUsageFilters = emptySet()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.clear_action))
                    }
                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(stringResource(R.string.filter_apply))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterCheckboxRow(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AdminListRowItem(
    tile: CombinedTile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    text = tile.definition.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (tile.definition.ttsText.isNotBlank() && tile.definition.ttsText != tile.definition.label) {
                    Text(
                        text = tile.definition.ttsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (tile.definition.imageUri != null) {
                    AsyncImage(
                        model = tile.definition.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = tile.definition.emoji ?: "✨", fontSize = 28.sp)
                }
            }
        }
    }
}