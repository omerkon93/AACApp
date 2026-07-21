package com.kon.myaacapp.ui.admin.grid

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.theme.FitzgeraldTileContent
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import java.io.File

@Composable
fun AdminEditableGridScreen(
    viewModel: AACViewModel,
    onEditTile: (CombinedTile) -> Unit,
    onCreateTile: (Int) -> Unit
) {
    val tiles by viewModel.currentTiles.collectAsState()

    val gridColumns by viewModel.gridColumns.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val gridTileScale by viewModel.gridTileScale.collectAsState()
    val gridTileContainerScale by viewModel.gridTileContainerScale.collectAsState()

    val orientation = LocalConfiguration.current.orientation

    val columns = if (
        orientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        gridColumns * 2
    } else {
        gridColumns
    }

    val maxIndex = tiles.maxOfOrNull { tile ->
        tile.layoutState.cellIndex
    } ?: -1

    val rows = maxOf(
        gridRows,
        (maxIndex / columns) + 1
    )

    val maxCells = columns * rows

    var draggedIndex by remember {
        mutableStateOf<Int?>(null)
    }

    var hoveredIndex by remember {
        mutableStateOf<Int?>(null)
    }

    val gridState = rememberLazyGridState()

    val tileMap: Map<Int, CombinedTile> = remember(tiles) {
        tiles.associateBy { tile ->
            tile.layoutState.cellIndex
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val spacing = 8.dp
        val totalVerticalSpacing = spacing * (gridRows - 1)

        val cellHeight = maxOf(
            0.dp,
            (maxHeight - totalVerticalSpacing) / gridRows
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gridState) {
                    fun getIndexFromTouch(
                        touchX: Float,
                        touchY: Float
                    ): Int? {
                        val matchedItem =
                            gridState.layoutInfo.visibleItemsInfo.find { itemInfo ->
                                val left = itemInfo.offset.x.toFloat()
                                val right = left + itemInfo.size.width

                                val top = itemInfo.offset.y.toFloat()
                                val bottom = top + itemInfo.size.height

                                touchX in left..right &&
                                        touchY in top..bottom
                            }

                        return matchedItem?.index
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            draggedIndex = getIndexFromTouch(
                                touchX = offset.x,
                                touchY = offset.y
                            )

                            hoveredIndex = draggedIndex
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            hoveredIndex = getIndexFromTouch(
                                touchX = change.position.x,
                                touchY = change.position.y
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
                                    toIndex = toIndex
                                )
                            }

                            draggedIndex = null
                            hoveredIndex = null
                        },
                        onDragCancel = {
                            draggedIndex = null
                            hoveredIndex = null
                        }
                    )
                },
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = maxCells,
                key = { index -> index }
            ) { index ->
                val tile = tileMap[index]

                val isDragged = index == draggedIndex
                val isHovered = index == hoveredIndex

                val dragScale by animateFloatAsState(
                    targetValue = if (isDragged) 0.9f else 1f,
                    label = "tileDragScale"
                )

                val dragAlpha by animateFloatAsState(
                    targetValue = if (isDragged) 0.6f else 1f,
                    label = "tileDragAlpha"
                )

                val onEmptyClick: () -> Unit = remember(
                    index,
                    onCreateTile
                ) {
                    {
                        onCreateTile(index)
                    }
                }

                val onTileEdit: () -> Unit = remember(
                    tile,
                    onEditTile
                ) {
                    {
                        tile?.let(onEditTile)
                    }
                }

                Box(
                    modifier = Modifier
                        .height(cellHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = dragScale
                            scaleY = dragScale
                            alpha = dragAlpha
                        }
                        .border(
                            width = if (isHovered) 3.dp else 0.dp,
                            color = if (isHovered) {
                                Color.Blue
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (tile != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(
                                fraction = gridTileContainerScale
                            )
                        ) {
                            AdminTileItem(
                                tile = tile,
                                scale = gridTileScale,
                                onClick = onTileEdit,
                                onEdit = onTileEdit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        EmptySlot(
                            onClick = onEmptyClick,
                            modifier = Modifier.fillMaxSize()
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
    scale: Float,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val definition = tile.definition

    val backgroundColor = remember(
        definition.backgroundColorHex,
        definition.partOfSpeech
    ) {
        definition.backgroundColorHex
            ?.let { colorHex ->
                runCatching {
                    Color(colorHex.toColorInt())
                }.getOrNull()
            }
            ?: resolveFitzgeraldColor(definition.partOfSpeech)
    }

    AdminTileUI(
        label = definition.label,
        emoji = definition.emoji,
        imageUri = definition.imageUri,
        backgroundColor = backgroundColor,
        tileType = definition.resolvedType,
        scale = scale,
        onClick = onClick,
        onEdit = onEdit,
        isHidden = tile.layoutState.isHidden,
        modifier = modifier
    )
}

@Composable
fun AdminTileUI(
    label: String,
    emoji: String?,
    imageUri: String?,
    backgroundColor: Color,
    tileType: TileType,
    scale: Float,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 12.sp,
    isHidden: Boolean = false
) {
    val isFolder = tileType == TileType.FOLDER

    val imageModel = remember(imageUri) {
        imageUri?.let { uri ->
            val file = File(uri)

            if (file.exists()) {
                file
            } else {
                uri
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .border(
                width = if (isFolder) 3.dp else 0.dp,
                color = if (isFolder) {
                    Color.DarkGray
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) {
                backgroundColor.copy(alpha = 0.5f)
            } else {
                backgroundColor
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                    when {
                        imageModel != null -> {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = imageModel
                                ),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        emoji != null -> {
                            Text(
                                text = emoji,
                                fontSize = (56f * scale).sp
                            )
                        }
                    }
                }

                Text(
                    text = label,
                    fontSize = (labelSize.value * scale).sp,
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

                TileType.CONNECTOR -> {
                    Icons.AutoMirrored.Filled.ArrowForward
                }

                TileType.QUICK_FIRE -> {
                    Icons.Default.FlashOn
                }

                TileType.BASIC -> null
            }

            if (indicatorIcon != null) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size((16f * scale).dp),
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
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
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
                        .background(
                            Color.Gray.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "מוסתר",
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                )
            )
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.3f
                ),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Tile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.5f
            )
        )
    }
}