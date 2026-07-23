package com.kon.myaacapp.ui.communication

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.ui.theme.FitzgeraldTileContent
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import java.io.File

@Composable
fun MainCommunicationScreen(
    viewModel: AACViewModel,
    onNavigateToCategory: (String) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onHomeClick: () -> Unit
) {
    val tiles by viewModel.currentTiles.collectAsState()
    val sentence by viewModel.selectedSentence.collectAsState()
    val currentParentId by viewModel.currentParentId.collectAsState()
    val userGender by viewModel.userGender.collectAsState()
    val langCode by viewModel.languageCode.collectAsState()

    val layoutSettingsLoaded by
    viewModel.layoutSettingsLoaded.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val gridTileScale by viewModel.gridTileScale.collectAsState()
    val gridTileContainerScale by viewModel.gridTileContainerScale.collectAsState() // 👉 Added container scale
    val barTileImageScale by viewModel.barTileImageScale.collectAsState()
    val barTileTitleScale by viewModel.barTileTitleScale.collectAsState()
    val actionButtonScale by viewModel.actionButtonScale.collectAsState()
    val showSentenceBar by viewModel.showSentenceBar.collectAsState()

    val showBackButton by viewModel.showBackButton.collectAsState()
    val showBackspaceButton by viewModel.showBackspaceButton.collectAsState()
    val showSpeakButton by viewModel.showSpeakButton.collectAsState()
    val homeInActionBar by viewModel.homeInActionBar.collectAsState()

    BackHandler(enabled = currentParentId != null) {
        viewModel.navigateBack()
    }

    val onSpeak = remember(viewModel) { { viewModel.speakSentence() } }
    val onClear = remember(viewModel) { { viewModel.clearSentence() } }
    val onBackspace = remember(viewModel) { { viewModel.backspaceSentence() } }
    val onTileClick = remember(viewModel, onNavigateToCategory) {
        { tile: CombinedTile -> viewModel.selectTile(tile, onNavigateToCategory) }
    }
    val handleBackClick = remember(currentParentId, viewModel, onBackClick) {
        {
            if (currentParentId != null) {
                viewModel.navigateBack()
            } else {
                onBackClick()
            }
        }
    }

    if (!layoutSettingsLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
        )

        return
    }

    MainCommunicationScreenContent(
        tiles = tiles,
        sentence = sentence,
        currentParentId = currentParentId,
        userGender = userGender,
        langCode = langCode,
        onSpeak = onSpeak,
        onClear = onClear,
        onBackspace = onBackspace,
        onTileClick = onTileClick,
        onBackClick = handleBackClick,
        onNavigateToAdmin = onNavigateToAdmin,
        onHomeClick = onHomeClick,
        gridColumns = gridColumns,
        gridRows = gridRows,
        gridTileScale = gridTileScale,
        gridTileContainerScale = gridTileContainerScale, // 👉 Pass container scale down
        barTileImageScale = barTileImageScale,
        barTileTitleScale = barTileTitleScale,
        actionButtonScale = actionButtonScale,
        showSentenceBar = showSentenceBar,
        showBackButton = showBackButton,
        showBackspaceButton = showBackspaceButton,
        showSpeakButton = showSpeakButton,
        homeInActionBar = homeInActionBar
    )
}

@Composable
fun MainCommunicationScreenContent(
    tiles: List<CombinedTile>,
    sentence: List<CombinedTile>,
    currentParentId: String?,
    userGender: Gender,
    langCode: String,
    onSpeak: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onTileClick: (CombinedTile) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onHomeClick: () -> Unit,
    gridColumns: Int,
    gridRows: Int,
    gridTileScale: Float,
    gridTileContainerScale: Float,
    barTileImageScale: Float,
    barTileTitleScale: Float,
    actionButtonScale: Float,
    showSentenceBar: Boolean,
    showBackButton: Boolean,
    showBackspaceButton: Boolean,
    showSpeakButton: Boolean,
    homeInActionBar: Boolean
) {
    var showPinDialog by remember { mutableStateOf(false) }

    if (showPinDialog) {
        AdminPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = {
                showPinDialog = false
                onNavigateToAdmin()
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            if (showSentenceBar) {
                SentenceBar(
                    sentence = sentence,
                    userGender = userGender,
                    onSettingsClick = { showPinDialog = true },
                    imageScale = barTileImageScale,
                    titleScale = barTileTitleScale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            ActionBar(
                onSpeak = onSpeak,
                onClear = onClear,
                onBackspace = onBackspace,
                onBack = onBackClick,
                onSettingsClick = { showPinDialog = true },
                onHomeClick = onHomeClick,
                showSettingsFallback = !showSentenceBar,
                canGoBack = currentParentId != null,
                scale = actionButtonScale,
                showClearButton = showSentenceBar,
                showBackButton = showBackButton,
                showBackspaceButton = showSentenceBar && showBackspaceButton,
                showSpeakButton = showSentenceBar && showSpeakButton,
                homeInActionBar = homeInActionBar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            val orientation = LocalConfiguration.current.orientation

            val columns = if (
                orientation == Configuration.ORIENTATION_LANDSCAPE
            ) {
                gridColumns * 2
            } else {
                gridColumns
            }

            val totalCellsToRender = gridRows * columns

            val tileMap: Map<Int, CombinedTile> = remember(tiles) {
                tiles.associateBy { tile ->
                    tile.layoutState.cellIndex
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                val spacing = 8.dp

                val totalVerticalSpacing =
                    spacing * (gridRows - 1).coerceAtLeast(0)

                val cellHeight = maxOf(
                    0.dp,
                    (maxHeight - totalVerticalSpacing) / gridRows
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    userScrollEnabled = false
                ) {
                    items(
                        count = totalCellsToRender,
                        key = { index ->
                            tileMap[index]?.definition?.id
                                ?: "empty_cell_$index"
                        }
                    ) { index ->
                        val tile: CombinedTile? = tileMap[index]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (tile != null) {
                                AACTileItem(
                                    tile = tile,
                                    userGender = userGender,
                                    scale = gridTileScale,
                                    modifier = Modifier.fillMaxSize(
                                        fraction = gridTileContainerScale
                                    ),
                                    onClick = {
                                        onTileClick(tile)
                                    }
                                )
                            } else {
                                Spacer(
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!homeInActionBar) {
            BottomHomeBar(
                onHomeClick = onHomeClick,
                scale = actionButtonScale
            )
        }
    }
}

@Composable
fun BottomHomeBar(
    onHomeClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF2D2F33),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onHomeClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .width((80 * scale).dp)
                .height((48 * scale).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Home",
                tint = Color.Black,
                modifier = Modifier.size((32 * scale).dp)
            )
        }
    }
}

@Composable
fun SentenceBar(
    sentence: List<CombinedTile>,
    userGender: Gender,
    onSettingsClick: () -> Unit,
    imageScale: Float,
    titleScale: Float,
    modifier: Modifier = Modifier
) {
    val rowModifier = Modifier
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
        .padding(8.dp)

    val settingsButtonModifier = Modifier
        .size(48.dp)
        .background(MaterialTheme.colorScheme.surface, CircleShape)

    val tileModifier = remember(imageScale) { Modifier.size((86 * imageScale).dp) }

    Row(
        modifier = modifier.then(rowModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                items = sentence,
                key = { index, tile -> "${tile.definition.id}_$index" }
            ) { _, tile ->
                val displayLabel = if (userGender == Gender.FEMALE) {
                    tile.definition.labelFeminine ?: tile.definition.label
                } else {
                    tile.definition.label
                }

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

                val imageModel = remember(tile.definition.imageUri) {
                    tile.definition.imageUri?.let { uri ->
                        val file = File(uri)
                        if (file.exists()) file else uri
                    }
                }

                val emojiText = tile.definition.emoji

                Card(
                    modifier = tileModifier,
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageModel != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(imageModel),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (emojiText != null) {
                                Text(text = emojiText, fontSize = (24 * imageScale).sp)
                            }
                        }

                        Text(
                            text = displayLabel,
                            fontSize = (9 * titleScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            color = FitzgeraldTileContent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onSettingsClick,
            modifier = settingsButtonModifier
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Admin Settings",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ActionBar(
    onSpeak: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onHomeClick: () -> Unit,
    showSettingsFallback: Boolean,
    canGoBack: Boolean,
    scale: Float,
    showClearButton: Boolean,
    showBackButton: Boolean,
    showBackspaceButton: Boolean,
    showSpeakButton: Boolean,
    homeInActionBar: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                backgroundColor = Color(0xFFE0E0E0),
                iconTint = Color.DarkGray,
                onClick = onBack,
                enabled = canGoBack,
                scale = scale,
                modifier = Modifier.weight(1f)
            )
        }

        if (showClearButton) {
            ActionButton(
                icon = Icons.Default.Delete,
                backgroundColor = Color(0xFFFFCDD2),
                iconTint = Color.Red,
                onClick = onClear,
                enabled = true,
                scale = scale,
                modifier = Modifier.weight(1f)
            )
        }

        if (showBackspaceButton) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                backgroundColor = Color(0xFFF8BBD0),
                iconTint = Color(0xFFC2185B),
                onClick = onBackspace,
                enabled = true,
                scale = scale,
                modifier = Modifier.weight(1f)
            )
        }

        if (showSpeakButton) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                backgroundColor = Color(0xFF64B5F6),
                iconTint = Color.White,
                onClick = onSpeak,
                enabled = true,
                scale = scale,
                modifier = Modifier.weight(2f)
            )
        }

        if (homeInActionBar) {
            ActionButton(
                icon = Icons.Default.Home,
                backgroundColor = Color(0xFFE0E0E0),
                iconTint = Color.DarkGray,
                onClick = onHomeClick,
                enabled = true,
                scale = scale,
                modifier = Modifier.weight(1f)
            )
        }

        if (showSettingsFallback) {
            ActionButton(
                icon = Icons.Default.Settings,
                backgroundColor = Color(0xFF454950),
                iconTint = Color.White,
                onClick = onSettingsClick,
                enabled = true,
                scale = scale,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height((64 * scale).dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            modifier = Modifier.size((32 * scale).dp)
        )
    }
}

@Composable
fun AdminPinDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_access)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_admin_password))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            pin = it
                            error = false
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error,
                    singleLine = true,
                    label = { Text(stringResource(R.string.password)) }
                )
                if (error) {
                    Text(
                        "PIN שגוי",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin == "1234") {
                    onConfirm()
                } else {
                    error = true
                }
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AACTileItem(
    tile: CombinedTile,
    userGender: Gender,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
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

    val displayLabel = if (userGender == Gender.FEMALE) {
        tile.definition.labelFeminine ?: tile.definition.label
    } else {
        tile.definition.label
    }

    TileUI(
        label = displayLabel,
        imageUri = tile.definition.imageUri,
        emoji = tile.definition.emoji,
        backgroundColor = backgroundColor,
        tileType = tile.definition.resolvedType,
        isHidden = tile.layoutState.isHidden,
        scale = scale,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun TileUI(
    label: String,
    imageUri: String?,
    emoji: String?,
    backgroundColor: Color,
    tileType: TileType,
    isHidden: Boolean = false,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    labelFontSize: TextUnit = 14.sp
) {
    if (isHidden) return

    val isFolder = tileType == TileType.FOLDER

    val imageModel = remember(imageUri) {
        imageUri?.let {
            val file = File(it)
            if (file.exists()) file else it
        }
    }

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = if (isFolder) 3.dp else 0.dp,
                color = if (isFolder) Color.DarkGray else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
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
                        Text(text = emoji, fontSize = (56f * scale).sp)
                    }
                }

                Text(
                    text = label,
                    fontSize = (labelFontSize.value * scale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    color = FitzgeraldTileContent
                )
            }

            val indicatorIcon = when (tileType) {
                TileType.FOLDER -> Icons.Default.Folder
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
                        .padding(4.dp)
                        .size((18f * scale).dp),
                    tint = FitzgeraldTileContent
                )
            }
        }
    }
}