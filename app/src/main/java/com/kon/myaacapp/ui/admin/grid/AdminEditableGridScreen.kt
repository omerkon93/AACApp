package com.kon.myaacapp.ui.admin.grid

import android.content.res.Configuration
import com.kon.myaacapp.ui.theme.FitzgeraldTileContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.model.toLegacyAACTile
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import java.io.File

@Composable
fun AdminEditableGridScreen(
    viewModel: AACViewModel,
    onEditTile: (AACTile?) -> Unit,
    onCreateTile: (Int) -> Unit
) {
    val tiles by viewModel.currentTiles.collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3
    val minRows = 4
    val maxIndex = tiles.maxOfOrNull { it.layoutState.cellIndex } ?: -1
    val rows = maxOf(minRows, (maxIndex / columns) + 1)
    val maxCells = columns * rows

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }

    val gridState = rememberLazyGridState()

    val tileMap: Map<Int, CombinedTile> = remember(tiles) {
        tiles.associateBy { tile ->
            tile.cellIndex
        }
    }

    val tileAspectRatio = remember(orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            1.2f
        } else {
            1.0f
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gridState) {
                    fun getIndexFromTouch(
                        touchX: Float,
                        touchY: Float,
                    ): Int? {
                        val matchedItem =
                            gridState.layoutInfo.visibleItemsInfo.find {
                                    itemInfo ->
                                val left =
                                    itemInfo.offset.x.toFloat()

                                val right =
                                    left + itemInfo.size.width

                                val top =
                                    itemInfo.offset.y.toFloat()

                                val bottom =
                                    top + itemInfo.size.height

                                touchX in left..right &&
                                        touchY in top..bottom
                            }

                        return matchedItem?.index
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            draggedIndex = getIndexFromTouch(
                                offset.x,
                                offset.y,
                            )

                            hoveredIndex = draggedIndex
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            hoveredIndex = getIndexFromTouch(
                                change.position.x,
                                change.position.y,
                            )
                        },
                        onDragEnd = {
                            val fromIndex = draggedIndex
                            val toIndex = hoveredIndex

                            if (
                                fromIndex != null &&
                                toIndex != null &&
                                fromIndex != toIndex
                            ) {
                                viewModel.swapTilePositions(
                                    fromIndex = fromIndex,
                                    toIndex = toIndex,
                                )
                            }

                            draggedIndex = null
                            hoveredIndex = null
                        },
                        onDragCancel = {
                            draggedIndex = null
                            hoveredIndex = null
                        },
                    )
                },
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = maxCells,
                key = { index -> index },
            ) { index ->
                val tile = tileMap[index]
                val isDragged = index == draggedIndex
                val isHovered = index == hoveredIndex

                val scale by animateFloatAsState(
                    targetValue = if (isDragged) 0.9f else 1f,
                    label = "tileScale",
                )

                val alpha by animateFloatAsState(
                    targetValue = if (isDragged) 0.6f else 1f,
                    label = "tileAlpha",
                )

                val onEmptyClick = remember(
                    index,
                    onCreateTile,
                ) {
                    {
                        onCreateTile(index)
                    }
                }

                val onTileEdit: () -> Unit = remember(
                    tile,
                    onEditTile,
                ) {
                    if (tile != null) {
                        {
                            onEditTile(
                                tile.toLegacyAACTile()
                            )
                        }
                    } else {
                        {
                            // Empty cell: no edit action.
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .border(
                            width = if (isHovered) {
                                3.dp
                            } else {
                                0.dp
                            },
                            color = if (isHovered) {
                                Color.Blue
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp),
                        ),
                ) {
                    if (tile != null) {
                        AdminTileItem(
                            tile = tile,
                            aspectRatio = tileAspectRatio,
                            onClick = {},
                            onEdit = onTileEdit,
                        )
                    } else {
                        EmptySlot(
                            aspectRatio = tileAspectRatio,
                            onClick = onEmptyClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTileItem(
    tile: CombinedTile,
    aspectRatio: Float,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val backgroundColor =
        remember(tile.definition.backgroundColorHex, tile.definition.partOfSpeech) {
            if (tile.definition.backgroundColorHex != null) {
                try {
                    Color(tile.definition.backgroundColorHex.toColorInt())
                } catch (_: Exception) {
                    resolveFitzgeraldColor(tile.definition.partOfSpeech)
                }
            } else {
                resolveFitzgeraldColor(tile.definition.partOfSpeech)
            }
        }

    AdminTileUI(
        label = tile.definition.label,
        emoji = tile.definition.emoji,
        imageUri = tile.definition.imageUri,
        backgroundColor = backgroundColor,
        aspectRatio = aspectRatio,
        tileType = tile.definition.resolvedType,
        onClick = onClick,
        onEdit = onEdit,
        isHidden = tile.layoutState.isHidden
    )
}

@Composable
fun AdminTileUI(
    label: String,
    emoji: String?,
    imageUri: String?,
    backgroundColor: Color,
    aspectRatio: Float,
    tileType: TileType,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 12.sp,
    isHidden: Boolean = false
) {
    val isFolder = tileType == TileType.FOLDER

    val imageModel = remember(imageUri) {
        if (imageUri != null) {
            val file = File(imageUri)
            if (file.exists()) file else imageUri
        } else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick)
            .border(
                width = if (isFolder) 3.dp else 0.dp,
                color = if (isFolder) Color.DarkGray else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHidden) backgroundColor.copy(alpha = 0.5f) else backgroundColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageModel != null) {
                        Image(
                            painter = rememberAsyncImagePainter(imageModel),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (emoji != null) {
                        Text(text = emoji, fontSize = 56.sp)
                    }
                }

                Text(
                    text = label,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    color = FitzgeraldTileContent
                )
            }

            val indicatorIcon = when (tileType) {
                TileType.FOLDER -> null
                TileType.CONNECTOR -> Icons.AutoMirrored.Filled.ArrowForward
                TileType.QUICK_FIRE -> Icons.Default.FlashOn
                TileType.BASIC -> null
            }

            if (indicatorIcon != null) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                    tint = FitzgeraldTileContent
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "מוסתר",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySlot(
    aspectRatio: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Tile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}