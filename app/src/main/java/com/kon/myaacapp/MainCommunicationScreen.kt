package com.kon.myaacapp

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import java.io.File

@Composable
fun MainCommunicationScreen(
    viewModel: AACViewModel,
    onNavigateToCategory: (String) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAdmin: () -> Unit,
) {
    val tiles by viewModel.currentTiles.collectAsState()
    val sentence by viewModel.selectedSentence.collectAsState()
    val currentParentId by viewModel.currentParentId.collectAsState()
    val userGender by viewModel.userGender.collectAsState()
    val langCode by viewModel.languageCode.collectAsState()

    BackHandler(enabled = currentParentId != null) {
        viewModel.navigateBack()
    }

    MainCommunicationScreenContent(
        tiles = tiles,
        sentence = sentence,
        currentParentId = currentParentId,
        userGender = userGender,
        langCode = langCode,
        onSpeak = { viewModel.speakSentence() },
        onClear = { viewModel.clearSentence() },
        onBackspace = { viewModel.backspaceSentence() },
        onTileClick = { tile ->
            viewModel.selectTile(tile, onNavigateToCategory)
        },
        onBackClick = {
            if (currentParentId != null) {
                viewModel.navigateBack()
            } else {
                onBackClick()
            }
        },
        onNavigateToAdmin = onNavigateToAdmin
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
    onNavigateToAdmin: () -> Unit
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
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 1. Sentence Bar (The Pencil Icon triggers showPinDialog!)
        SentenceBar(
            sentence = sentence,
            userGender = userGender,
            onSettingsClick = { showPinDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(bottom = 8.dp)
        )

        // 2. Action Buttons on their own line (Middle)
        ActionBar(
            onSpeak = onSpeak,
            onClear = onClear,
            onBackspace = onBackspace,
            onBack = onBackClick,
            canGoBack = currentParentId != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // 3. Tile Grid (Fixed to respect cellIndex and empty spaces)
        val orientation = LocalConfiguration.current.orientation
        val columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3
        val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f

        // Calculate total cells needed to ensure we don't cut off the grid
        // (Minimum 12 cells for a 3x4 grid)
        val maxTileIndex = tiles.maxOfOrNull { it.layoutState.cellIndex } ?: 0
        val minCells = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 24 else 12
        val totalCellsToRender = maxOf(minCells, maxTileIndex + 1)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Iterate by exact grid positions, not just the list size
            items(count = totalCellsToRender) { index ->
                val tile = tiles.find { it.layoutState.cellIndex == index }

                if (tile != null) {
                    AACTileItem(
                        tile = tile,
                        userGender = userGender,
                        orientation = orientation,
                        onClick = { onTileClick(tile) }
                    )
                } else {
                    // Render an invisible placeholder to preserve the empty slot
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                    )
                }
            }
        }
    }
}

@Composable
fun SentenceBar(
    sentence: List<CombinedTile>,
    userGender: Gender,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowModifier = remember {
        Modifier
            .background(Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
            .padding(8.dp)
    }
    val settingsButtonModifier = remember {
        Modifier
            .size(48.dp)
            .background(Color.White, CircleShape)
    }
    val tileModifier = remember { Modifier.size(86.dp) }

    Row(
        modifier = modifier.then(rowModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = sentence,
                key = { it.definition.id }
            ) { tile ->
                val displayLabel = if (userGender == Gender.FEMALE) {
                    tile.definition.labelFeminine ?: tile.definition.label
                } else {
                    tile.definition.label
                }

                val backgroundColor = remember(tile.definition.backgroundColorHex, tile.definition.partOfSpeech) {
                    if (tile.definition.backgroundColorHex != null) {
                        try { Color(tile.definition.backgroundColorHex.toColorInt()) }
                        catch (_: Exception) { resolveFitzgeraldColor(tile.definition.partOfSpeech) }
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
                            } else if (tile.definition.emoji != null) {
                                // Scaled down to 24.sp to fit the 64dp container without clipping
                                Text(text = tile.definition.emoji, fontSize = 36.sp)
                            }
                        }

                        Text(
                            text = displayLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
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
                tint = Color.Gray
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
    canGoBack: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            backgroundColor = Color(0xFFE0E0E0),
            iconTint = Color.DarkGray,
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            icon = Icons.Default.Delete,
            backgroundColor = Color(0xFFFFCDD2),
            iconTint = Color.Red,
            onClick = onClear,
            enabled = true,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            icon = Icons.AutoMirrored.Filled.Backspace,
            backgroundColor = Color(0xFFF8BBD0),
            iconTint = Color(0xFFC2185B),
            onClick = onBackspace,
            enabled = true,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            backgroundColor = Color(0xFF64B5F6),
            iconTint = Color.White,
            onClick = onSpeak,
            enabled = true,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
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
            modifier = Modifier.size(32.dp)
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
fun AACTileItem(tile: CombinedTile, userGender: Gender, orientation: Int, onClick: () -> Unit) {
    val backgroundColor = remember(tile.definition.backgroundColorHex, tile.definition.partOfSpeech) {
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

    val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f

    TileUI(
        label = displayLabel,
        imageUri = tile.definition.imageUri,
        emoji = tile.definition.emoji,
        backgroundColor = backgroundColor,
        aspectRatio = aspectRatio,
        tileType = tile.definition.resolvedType, // Pass the new resolved type
        isHidden = tile.layoutState.isHidden,
        onClick = onClick
    )
}

// 2. Update the TileUI signature and content:
@Composable
fun TileUI(
    label: String,
    imageUri: String?,
    emoji: String?,
    backgroundColor: Color,
    aspectRatio: Float,
    tileType: TileType,
    isHidden: Boolean = false,
    onClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    labelFontSize: TextUnit = 14.sp
) {
    if (isHidden) return

    val isFolder = tileType == TileType.FOLDER

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick)
            .border(
                width = if (isFolder) 3.dp else 0.dp, // Thicker border for folders
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
                        .padding(8.dp), // A little padding so images don't touch the edges
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        val file = File(imageUri)
                        Image(
                            painter = rememberAsyncImagePainter(if (file.exists()) file else imageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // Keeps aspect ratio without cropping
                        )
                    } else if (emoji != null) {
                        // Bumped up from 24.sp to 56.sp!
                        Text(text = emoji, fontSize = 56.sp)
                    }
                }

                Text(
                    text = label,
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Visual Indicators based on TileType
            val indicatorIcon = when (tileType) {
                TileType.FOLDER -> Icons.Default.Folder
                TileType.CONNECTOR -> Icons.AutoMirrored.Filled.ArrowForward
                TileType.QUICK_FIRE -> Icons.Default.FlashOn // Lightning bolt
                TileType.BASIC -> null
            }

            if (indicatorIcon != null) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}