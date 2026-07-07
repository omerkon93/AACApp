package com.kon.myaacapp

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.draw.clip
import com.kon.myaacapp.AACTile
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.CombinedTile
import com.kon.myaacapp.toLegacyAACTile
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

    // State to track our drag gesture
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Calculate rough cell dimensions for mapping touch coordinates to grid slots
        val cellWidthPx = constraints.maxWidth.toFloat() / columns
        val cellHeightPx = constraints.maxHeight.toFloat() / rows

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val col = (offset.x / cellWidthPx).toInt().coerceIn(0, columns - 1)
                            val row = (offset.y / cellHeightPx).toInt().coerceIn(0, rows - 1)
                            draggedIndex = row * columns + col
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val col = (change.position.x / cellWidthPx).toInt().coerceIn(0, columns - 1)
                            val row = (change.position.y / cellHeightPx).toInt().coerceIn(0, rows - 1)
                            hoveredIndex = row * columns + col
                        },
                        onDragEnd = {
                            if (draggedIndex != null && hoveredIndex != null && draggedIndex != hoveredIndex) {
                                viewModel.swapTilePositions(draggedIndex!!, hoveredIndex!!)
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
            val tileMap = tiles.associateBy { it.layoutState.cellIndex }

            items(maxCells) { index ->
                val tile = tileMap[index]
                val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f

                // Visual feedback states
                val isDragged = index == draggedIndex
                val isHovered = index == hoveredIndex
                val scale by animateFloatAsState(if (isDragged) 0.9f else 1f, label = "scale")
                val alpha by animateFloatAsState(if (isDragged) 0.6f else 1f, label = "alpha")

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .border(
                            width = if (isHovered) 3.dp else 0.dp,
                            color = if (isHovered) Color.Blue else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    if (tile != null) {
                        AdminTileItem(
                            tile = tile,
                            aspectRatio = aspectRatio,
                            onClick = { /* Normal click handled elsewhere */ },
                            onEdit = { onEditTile(tile.toLegacyAACTile()) }
                        )
                    } else {
                        EmptySlot(
                            aspectRatio = aspectRatio,
                            onClick = { onCreateTile(index) }
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
    val backgroundColor = if (tile.definition.backgroundColorHex != null) {
        try { Color(tile.definition.backgroundColorHex.toColorInt()) } catch (_: Exception) { Color.LightGray }
    } else Color.LightGray

    AdminTileUI(
        label = tile.definition.label,
        emoji = tile.definition.emoji,
        imageUri = tile.definition.imageUri,
        backgroundColor = backgroundColor,
        aspectRatio = aspectRatio,
        isCategory = tile.definition.isCategory,
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
    isCategory: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 12.sp,
    borderColor: Color = if (isCategory) Color.DarkGray else Color.Transparent,
    isHidden: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick)
            .border(
                width = if (isCategory) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
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
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        val file = File(imageUri)
                        Image(
                            painter = rememberAsyncImagePainter(if (file.exists()) file else imageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (emoji != null) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
                
                Text(
                    text = label,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Edit Indicator
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
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
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("HIDDEN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Tile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
