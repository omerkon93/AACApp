package com.kon.myaacapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.kon.myaacapp.AACTile
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.AdminAuditFilter
import com.kon.myaacapp.CombinedTile
import com.kon.myaacapp.R
import com.kon.myaacapp.toLegacyAACTile

@Composable
fun AdminListView(
    viewModel: AACViewModel,
    onEditTile: (AACTile?) -> Unit,
    onDeleteTile: (AACTile) -> Unit
) {
    val tiles by viewModel.filteredTilesForAdmin.collectAsState()
    val searchQuery by viewModel.adminSearchQuery.collectAsState()
    val auditFilter by viewModel.adminAuditFilter.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setAdminSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text(stringResource(R.string.search_tiles_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setAdminSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            }
        )

        // Audit Filters
        ScrollableTabRow(
            selectedTabIndex = AdminAuditFilter.values().indexOf(auditFilter),
            edgePadding = 8.dp,
            containerColor = Color.Transparent
        ) {
            AdminAuditFilter.values().forEach { filter ->
                Tab(
                    selected = auditFilter == filter,
                    onClick = { viewModel.setAdminAuditFilter(filter) },
                    text = {
                        Text(
                            text = when (filter) {
                                AdminAuditFilter.ALL -> stringResource(R.string.filter_all)
                                AdminAuditFilter.MISSING_AUDIO -> stringResource(R.string.filter_missing_audio)
                                AdminAuditFilter.MISSING_TTS -> stringResource(R.string.filter_missing_tts)
                                AdminAuditFilter.MISSING_IMAGE -> stringResource(R.string.filter_missing_image)
                                AdminAuditFilter.UNUSED -> stringResource(R.string.filter_unused)
                                AdminAuditFilter.HIDDEN -> stringResource(R.string.filter_hidden)
                            }
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tiles, key = { it.definition.id }) { tile ->
                AdminTileListCard(
                    tile = tile,
                    onEdit = { onEditTile(tile.toLegacyAACTile()) },
                    onDelete = { onDeleteTile(tile.toLegacyAACTile()) }
                )
            }
        }
    }
}

@Composable
fun AdminTileListCard(
    tile: CombinedTile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Tile Preview
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (tile.definition.backgroundColorHex != null) Color(tile.definition.backgroundColorHex.toColorInt()) else Color.LightGray,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tile.definition.emoji != null) {
                    Text(tile.definition.emoji, fontSize = 24.sp)
                } else if (tile.definition.imageUri != null) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(tile.definition.label, fontWeight = FontWeight.Bold)
                Text(tile.definition.ttsText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
