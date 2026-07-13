package com.kon.myaacapp.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSection(
    typeOptions: Map<TileType, String>,

    tileType: TileType,
    onTileTypeChange: (TileType) -> Unit,

    categories: List<CombinedTile>,
    existingTile: AACTile?,

    parentId: String?,
    onParentIdChange: (String?) -> Unit,

    linkedCategoryId: String?,
    onLinkedCategoryIdChange: (String?) -> Unit,

    cellIndex: String,
    onCellIndexChange: (String) -> Unit,

    tilesInParent: List<CombinedTile>,
    maxCapacity: Int,
    onOccupiedCellSelected: (String) -> Unit,

    isHidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,

    tileId: String,
    onTileIdChange: (String) -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var parentExpanded by remember { mutableStateOf(false) }
    var linkedCategoryExpanded by remember { mutableStateOf(false) }
    var positionExpanded by remember { mutableStateOf(false) }

    EditSection(
        title = stringResource(R.string.section_advanced),
        icon = Icons.Default.Settings,
        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. TILE TYPE
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.tile_type_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box {
                    OutlinedTextField(
                        value = typeOptions[tileType]
                            ?: stringResource(R.string.tile_type_basic),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    typeExpanded = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                        },
                    )

                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = {
                            typeExpanded = false
                        },
                    ) {
                        typeOptions.forEach { (type, text) ->
                            DropdownMenuItem(
                                text = {
                                    Text(text)
                                },
                                onClick = {
                                    onTileTypeChange(type)
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // 2. PARENT CATEGORY
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.parent_category_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box {
                    val parentLabel =
                        categories.find { category ->
                            category.id == parentId
                        }?.label ?: stringResource(R.string.root_home)

                    OutlinedTextField(
                        value = parentLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    parentExpanded = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                        },
                    )

                    DropdownMenu(
                        expanded = parentExpanded,
                        onDismissRequest = {
                            parentExpanded = false
                        },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.root_home))
                            },
                            onClick = {
                                onParentIdChange(null)
                                parentExpanded = false
                            },
                        )

                        categories
                            .filter { category ->
                                category.isCategory &&
                                        category.id != existingTile?.id
                            }
                            .forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text(category.label)
                                    },
                                    onClick = {
                                        onParentIdChange(category.id)
                                        parentExpanded = false
                                    },
                                )
                            }
                    }
                }
            }

            // 3. JUMP TO CATEGORY
            if (
                tileType == TileType.FOLDER ||
                tileType == TileType.CONNECTOR
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.jump_to_category),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Box {
                        val linkedCategoryLabel =
                            categories.find { category ->
                                category.id == linkedCategoryId
                            }?.label
                                ?: stringResource(R.string.none_speech_only)

                        OutlinedTextField(
                            value = linkedCategoryLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        linkedCategoryExpanded = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )

                        DropdownMenu(
                            expanded = linkedCategoryExpanded,
                            onDismissRequest = {
                                linkedCategoryExpanded = false
                            },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.none_speech_only,
                                        ),
                                    )
                                },
                                onClick = {
                                    onLinkedCategoryIdChange(null)
                                    linkedCategoryExpanded = false
                                },
                            )

                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text(category.label)
                                    },
                                    onClick = {
                                        onLinkedCategoryIdChange(category.id)
                                        linkedCategoryExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 4. POSITION
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.cell_index_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExposedDropdownMenuBox(
                    expanded = positionExpanded,
                    onExpandedChange = { expanded ->
                        positionExpanded = expanded
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val selectedTile =
                        tilesInParent.find { tile ->
                            tile.cellIndex.toString() == cellIndex
                        }

                    val positionText: String =
                        when {
                            cellIndex.isEmpty() -> {
                                stringResource(R.string.pick_location)
                            }

                            selectedTile != null -> {
                                "$cellIndex - ${selectedTile.label}"
                            }

                            else -> {
                                cellIndex
                            }
                        }

                    OutlinedTextField(
                        value = positionText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType
                                    .PrimaryNotEditable,
                            )
                            .fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = positionExpanded,
                            )
                        },
                        colors =
                            ExposedDropdownMenuDefaults
                                .outlinedTextFieldColors(),
                    )

                    ExposedDropdownMenu(
                        expanded = positionExpanded,
                        onDismissRequest = {
                            positionExpanded = false
                        },
                    ) {
                        (0 until maxCapacity).forEach { index ->
                            val indexString = index.toString()

                            val tileAtIndex =
                                tilesInParent.find { tile ->
                                    tile.cellIndex == index
                                }

                            val isOccupied =
                                tileAtIndex != null &&
                                        tileAtIndex.id != existingTile?.id

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (tileAtIndex != null) {
                                            "$index - ${tileAtIndex.label}"
                                        } else {
                                            indexString
                                        },
                                        color = if (isOccupied) {
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                                .copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    if (isOccupied) {
                                        onOccupiedCellSelected(indexString)
                                    } else {
                                        onCellIndexChange(indexString)
                                    }

                                    positionExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // 5. HIDDEN
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isHidden,
                    onCheckedChange = onHiddenChange,
                )

                Text(
                    text = stringResource(R.string.hide_tile_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // 6. CUSTOM ID
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.advanced_id_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = tileId,
                    onValueChange = { newId ->
                        onTileIdChange(newId.trim())
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.advanced_id_placeholder,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    enabled = existingTile == null,
                    supportingText = {
                        if (existingTile != null) {
                            Text(
                                text = stringResource(
                                    R.string.advanced_id_error_existing,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string.advanced_id_hint,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}