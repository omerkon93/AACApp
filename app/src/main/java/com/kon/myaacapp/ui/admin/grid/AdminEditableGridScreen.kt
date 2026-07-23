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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.theme.FitzgeraldTileContent
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import java.io.File

@Composable
fun AdminEditableGridScreen(
    state: AdminGridState,
    onAction: (AdminGridAction) -> Unit,
) {
    val orientation =
        LocalConfiguration.current.orientation

    val columns = if (
        orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    ) {
        state.safeGridColumns * 2
    } else {
        state.safeGridColumns
    }

    val maxCells =
        state.maximumCells(columns)

    val gridState =
        rememberLazyGridState()

    val tileMap = remember(state.tiles) {
        state.tileMap
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val spacing = 8.dp

        val visibleRows = state.safeGridRows

        val totalVerticalSpacing =
            spacing * (visibleRows - 1)

        val cellHeight = maxOf(
            0.dp,
            (
                    maxHeight -
                            totalVerticalSpacing -
                            16.dp
                    ) / visibleRows
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    gridState,
                    onAction,
                ) {
                    fun getIndexFromTouch(
                        touchX: Float,
                        touchY: Float,
                    ): Int? {
                        val matchedItem =
                            gridState.layoutInfo
                                .visibleItemsInfo
                                .find { itemInfo ->
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
                            val draggedCellIndex =
                                getIndexFromTouch(
                                    touchX = offset.x,
                                    touchY = offset.y,
                                )

                            onAction(
                                AdminGridAction.DragStarted(
                                    cellIndex = draggedCellIndex,
                                )
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            val hoveredCellIndex =
                                getIndexFromTouch(
                                    touchX = change.position.x,
                                    touchY = change.position.y,
                                )

                            onAction(
                                AdminGridAction.DragHovered(
                                    cellIndex = hoveredCellIndex,
                                )
                            )
                        },
                        onDragEnd = {
                            onAction(
                                AdminGridAction.DragEnded
                            )
                        },
                        onDragCancel = {
                            onAction(
                                AdminGridAction.DragCancelled
                            )
                        },
                    )
                },
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement =
                Arrangement.spacedBy(spacing),
            verticalArrangement =
                Arrangement.spacedBy(spacing)
        ) {
            items(
                count = maxCells,
                key = { index ->
                    index
                }
            ) { index ->
                val tile = tileMap[index]

                val isDragged =
                    index == state.draggedIndex

                val isHovered =
                    index == state.hoveredIndex

                val dragScale by animateFloatAsState(
                    targetValue = if (isDragged) {
                        0.9f
                    } else {
                        1f
                    },
                    label = "tileDragScale"
                )

                val dragAlpha by animateFloatAsState(
                    targetValue = if (isDragged) {
                        0.6f
                    } else {
                        1f
                    },
                    label = "tileDragAlpha"
                )

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
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (tile != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(
                                fraction =
                                    state.gridTileContainerScale.coerceIn(
                                        minimumValue = 0.1f,
                                        maximumValue = 1f,
                                    )
                            )
                        ) {
                            AdminTileItem(
                                tile = tile,
                                scale = state.gridTileScale,
                                onEdit = {
                                    onAction(
                                        AdminGridAction.TileEditClicked(
                                            tile = tile,
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        EmptySlot(
                            onClick = {
                                onAction(
                                    AdminGridAction.EmptyCellClicked(
                                        cellIndex = index,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
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
            ?: resolveFitzgeraldColor(
                definition.partOfSpeech
            )
    }

    AdminTileUI(
        label = definition.label,
        emoji = definition.emoji,
        imageUri = definition.imageUri,
        backgroundColor = backgroundColor,
        tileType = definition.resolvedType,
        scale = scale,
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
            .border(
                width = if (isFolder) {
                    3.dp
                } else {
                    0.dp
                },
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
                horizontalAlignment =
                    Alignment.CenterHorizontally,
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
                                painter =
                                    rememberAsyncImagePainter(
                                        model = imageModel
                                    ),
                                contentDescription = null,
                                modifier =
                                    Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        emoji != null -> {
                            Text(
                                text = emoji,
                                fontSize = (
                                        56f * scale
                                        ).sp
                            )
                        }
                    }
                }

                Text(
                    text = label,
                    fontSize = (
                            labelSize.value * scale
                            ).sp,
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
                        .zIndex(2f)
                        .padding(6.dp)
                        .size((16f * scale).dp),
                    tint = FitzgeraldTileContent
                )
            }

            /*
             * The hidden-state overlay is drawn before the edit
             * button and uses a lower z-index. This prevents it
             * from visually or interactively covering the button.
             */
            if (isHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
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

            /*
             * The edit button is the topmost interactive element.
             * Only this button opens the tile edit menu.
             */
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(3f)
                    .padding(4.dp)
                    .size(36.dp)
                    .background(
                        color = Color.Black.copy(
                            alpha = 0.55f
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit tile",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
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
            .clickable(
                onClick = onClick
            )
            .border(
                width = 1.dp,
                color =
                    MaterialTheme.colorScheme.outline.copy(
                        alpha = 0.3f
                    ),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add tile",
            tint =
                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.5f
                )
        )
    }
}