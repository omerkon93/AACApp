package com.kon.myaacapp

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.CombinedTile
import com.kon.myaacapp.Gender
import com.kon.myaacapp.R
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
    var showPinDialog by remember { mutableStateOf(value = false) }
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    Column(modifier = Modifier.fillMaxSize()) {
        // Sentence Bar & Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (orientation == Configuration.ORIENTATION_LANDSCAPE) 100.dp else 120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SentenceBar(
                sentence = sentence,
                modifier = Modifier.weight(1f),
                userGender = userGender
            )
            
            Spacer(modifier = Modifier.width(8.dp))

            ActionBar(
                onSpeak = onSpeak,
                onClear = onClear,
                onBackspace = onBackspace,
                onSettings = { showPinDialog = true },
                onBack = onBackClick,
                canGoBack = currentParentId != null
            )
        }

        // Main Grid
        Box(modifier = Modifier.weight(1f)) {
            val columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3
            val minRows = 4
            val maxIndex = tiles.maxOfOrNull { it.layoutState.cellIndex } ?: -1
            val totalRows = maxOf(minRows, (maxIndex / columns) + 1)
            val totalCells = totalRows * columns
            val tileMap = remember(tiles) { tiles.associateBy { it.layoutState.cellIndex } }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totalCells) { index ->
                    val tile = tileMap[index]
                    if (tile != null) {
                        AACTileItem(
                            tile = tile,
                            userGender = userGender,
                            orientation = orientation,
                            onClick = { onTileClick(tile) }
                        )
                    } else {
                        // Empty slot to maintain grid structure
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AdminPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = {
                showPinDialog = false
                onNavigateToAdmin()
            }
        )
    }
}

@Composable
fun SentenceBar(
    sentence: List<CombinedTile>,
    modifier: Modifier = Modifier,
    userGender: Gender
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(sentence) { tile ->
                Box(modifier = Modifier.size(if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 70.dp else 90.dp)) {
                    AACTileItem(
                        tile = tile,
                        userGender = userGender,
                        orientation = LocalConfiguration.current.orientation,
                        onClick = { /* Could allow clicking to remove */ }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionBar(
    onSpeak: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val buttonSize = if (isLandscape) 48.dp else 56.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Color(0xFF9E9E9E), Modifier.size(buttonSize), enabled = true)
        ActionButton(onBackspace, Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.backspace), Color(0xFFFF9800), Modifier.size(buttonSize))
        ActionButton(onClear, Icons.Default.Clear, stringResource(R.string.clear), Color(0xFFF44336), Modifier.size(buttonSize))
        ActionButton(onSpeak, Icons.Default.PlayArrow, stringResource(R.string.speak), Color(0xFF4CAF50), Modifier.size(buttonSize))
        ActionButton(onSettings, Icons.Default.Settings, stringResource(R.string.admin_access), Color(0xFF607D8B), Modifier.size(buttonSize))
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(0.dp),
        enabled = enabled
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
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
                        "PIN שגוי", // Or add to strings.xml
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
        isCategory = tile.definition.isCategory,
        isHidden = tile.layoutState.isHidden,
        onClick = onClick
    )
}

@Composable
fun TileUI(
    label: String,
    imageUri: String?,
    emoji: String?,
    backgroundColor: Color,
    aspectRatio: Float,
    isCategory: Boolean,
    isHidden: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelFontSize: TextUnit = 14.sp,
    showSpeakerIcon: Boolean = false,
) {
    if (isHidden) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick)
            .border(
                width = if (isCategory) 2.dp else 0.dp,
                color = if (isCategory) Color.DarkGray else Color.Transparent,
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
                        Text(
                            text = emoji,
                            fontSize = 32.sp,
                            textAlign = TextAlign.Center
                        )
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

            if (showSpeakerIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun resolveFitzgeraldColor(partOfSpeech: String?): Color {
    return when (partOfSpeech?.uppercase()) {
        "PRONOUN", "PEOPLE" -> Color(0xFFFFF176) // Yellow
        "VERB" -> Color(0xFFAED581) // Green
        "NOUN" -> Color(0xFFBBDEFB) // Orange/Tan -> Blue in some versions
        "ADJECTIVE" -> Color(0xFFB39DDB) // Blue -> Purple
        "ADVERB" -> Color(0xFFF48FB1) // Brown -> Pink
        "PREPOSITION", "CONJUNCTION" -> Color(0xFFFFCC80) // Pink -> Orange
        "SOCIAL" -> Color(0xFFE1BEE7) // Purple
        else -> Color(0xFFF5F5F5) // Light Gray
    }
}
