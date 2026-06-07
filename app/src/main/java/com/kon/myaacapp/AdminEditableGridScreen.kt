package com.kon.myaacapp

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

@Composable
fun AdminEditableGridScreen(
    viewModel: AACViewModel,
    onEditTile: (AACTile?) -> Unit,
    onCreateTile: (Int) -> Unit
) {
    val tiles by viewModel.currentTiles.collectAsState()
    val currentParentId by viewModel.currentParentId.collectAsState()
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    
    val columnCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
    val maxCapacity = 12

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentParentId == null) "עריכת מסך ראשי" else "עריכת קטגוריה",
                style = MaterialTheme.typography.titleLarge
            )

            if (currentParentId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setCategory(null) }) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("בית")
                    }
                    
                    TextButton(onClick = {
                        // Find the grandparent ID
                        val currentTile = viewModel.allTiles.value.find { it.id == currentParentId }
                        viewModel.setCategory(currentTile?.parentId)
                    }) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("למעלה")
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(maxCapacity) { index ->
                val tile = tiles.find { it.cellIndex == index }
                val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f
                
                if (tile != null) {
                    AdminTileItem(
                        tile = tile,
                        aspectRatio = aspectRatio,
                        onClick = {
                            if (tile.isCategory) {
                                viewModel.setCategory(tile.id)
                            } else {
                                onEditTile(tile)
                            }
                        },
                        onLongClick = { onEditTile(tile) }
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

@Composable
fun AdminTileItem(
    tile: AACTile,
    aspectRatio: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Basic Tile UI without the complex logic of MainCommunicationScreen
    // Mirroring the visual style
    val backgroundColor = try {
        tile.backgroundColorHex?.let { Color(it.toColorInt()) }
            ?: resolveFitzgeraldColor(tile.partOfSpeech)
    } catch (_: Exception) {
        resolveFitzgeraldColor(tile.partOfSpeech)
    }

    AdminTileUI(
        label = tile.label,
        imageUri = tile.imageUri,
        emoji = tile.emoji,
        backgroundColor = backgroundColor,
        aspectRatio = aspectRatio,
        isCategory = tile.isCategory,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun AdminTileUI(
    label: String,
    imageUri: String?,
    emoji: String?,
    backgroundColor: Color,
    aspectRatio: Float,
    isCategory: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    contentColor: Color = contentColorFor(backgroundColor)
) {
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    OutlinedCard(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = backgroundColor
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isCategory) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp),
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
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
                        AsyncImage(
                            model = imageUri,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (emoji != null) {
                        Text(
                            text = emoji,
                            fontSize = 64.sp
                        )
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.05f)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = label,
                            fontSize = labelFontSize,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                .align(Alignment.Center),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
    val stroke = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
    val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color,
                style = stroke,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            )
        }
        
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "הוסף",
            modifier = Modifier.align(Alignment.Center).size(32.dp),
            tint = color
        )
    }
}
