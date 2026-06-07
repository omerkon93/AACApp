package com.kon.myaacapp

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import com.kon.myaacapp.ui.theme.PrimaryBlue
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

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
    val userGender by viewModel.tileService.userGender.collectAsState()

    val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()

    MainCommunicationScreenContent(
        tiles = tiles,
        sentence = sentence,
        currentParentId = currentParentId,
        userGender = userGender,
        onToggleGender = { 
            viewModel.tileService.setUserGender(
                if (userGender == Gender.MALE) Gender.FEMALE else Gender.MALE,
                viewModel.viewModelScope
            )
        },
        onSpeak = { viewModel.speakSentence() },
        onClear = { viewModel.clearSentence() },
        onBackspace = { viewModel.backspaceSentence() },
        onTileClick = { tile -> 
            viewModel.selectTile(tile, onNavigateToCategory)
            if (speakOnTilePress) {
                viewModel.playPreviewAudio(tile.ttsText, tile.audioUri)
            }
        },
        onBackClick = onBackClick,
        onNavigateToAdmin = onNavigateToAdmin
    )
}

@Composable
fun MainCommunicationScreenContent(
    tiles: List<AACTile>,
    sentence: List<AACTile>,
    currentParentId: String?,
    userGender: Gender,
    onToggleGender: () -> Unit,
    onSpeak: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onTileClick: (AACTile) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(value = false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }
    val orientation = configuration.orientation

    val columnCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        if (screenWidthDp >= 800) 6 else 5
    } else {
        if (screenWidthDp >= 600) 4 else 3
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Top Bar with Gender Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentParentId == null) "מסך ראשי" else "קטגוריה",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                IconButton(onClick = onToggleGender) {
                    Icon(
                        imageVector = if (userGender == Gender.MALE) Icons.Default.Male else Icons.Default.Female,
                        contentDescription = "Toggle Gender",
                        tint = if (userGender == Gender.MALE) Color(0xFF2196F3) else Color(0xFFE91E63)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sentence Builder Bar
            SentenceBuilder(
                sentence = sentence,
                currentParentId = currentParentId,
                onSpeak = onSpeak,
                onClear = onClear,
                onBackspace = onBackspace,
                onBackClick = onBackClick,
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.fastAny { it.pressed }) {
                                val startTime = System.currentTimeMillis()
                                var isLongPress = false
                                
                                while (true) {
                                    val nextEvent = withTimeoutOrNull(100) {
                                        awaitPointerEvent()
                                    }
                                    
                                    if (nextEvent == null) {
                                        // Timeout reached, check duration
                                        if ((System.currentTimeMillis() - startTime) >= 2000) {
                                            isLongPress = true
                                            break
                                        }
                                    } else {
                                        // Check if pointer is still down
                                        if (nextEvent.changes.fastAny { it.pressed.not() }) {
                                            break
                                        }
                                    }
                                }
                                
                                if (isLongPress) {
                                    showPinDialog = true
                                }
                            }
                        }
                    }
                }
            )

            if (showPinDialog) {
                AdminPinDialog(
                    onDismiss = { showPinDialog = false },
                    onAuthenticated = {
                        showPinDialog = false
                        onNavigateToAdmin()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Motor Planning: Fixed Grid Layout
                // Use a larger fixed number for tablets (e.g., 24 or 30) or dynamic
                val totalCells = if (columnCount >= 5) 30 else 12
                items(totalCells) { index ->
                    val tile = tiles.find { it.cellIndex == index }
                    if (tile != null && !tile.isHidden) {
                        AACTileItem(
                            tile = tile,
                            userGender = userGender,
                            orientation = orientation,
                            onClick = { onTileClick(tile) }
                        )
                    } else {
                        // Spacer to maintain grid positions
                        val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f
                        Spacer(modifier = Modifier.aspectRatio(aspectRatio))
                    }
                }
            }
        }
    }
}

@Composable
fun SentenceBuilder(
    sentence: List<AACTile>,
    currentParentId: String?,
    onSpeak: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Controls (On the physical left in RTL)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // Back Button (only shown when inside a category)
                if (currentParentId != null) {
                    Button(
                        onClick = onBackClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Speak Button
                Button(
                    onClick = onSpeak,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Backspace Button
                Button(
                    onClick = onBackspace,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Clear Button
                Button(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Horizontal list of selected words (Fills the rest with spaces)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sentence.forEach { tile ->
                    Column(
                        modifier = Modifier
                            .width(70.dp)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (tile.imageUri != null) {
                            AsyncImage(
                                model = tile.imageUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (tile.emoji != null) {
                            Text(
                                text = tile.emoji,
                                fontSize = 24.sp
                            )
                        }
                        
                        Text(
                            text = tile.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // Optional Clear Button if needed, or just rely on backspace. 
            // The prompt mentioned backspace and speak. I'll stick to those for now as primary.
        }
    }
}

@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onAuthenticated: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val correctPassword = "1234"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Access") },
        text = {
            Column {
                Text("Enter Admin Password")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Default password: 1234",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password == correctPassword) {
                        onAuthenticated()
                    }
                },
                enabled = password.isNotBlank()
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AACTileItem(tile: AACTile, userGender: Gender, orientation: Int, onClick: () -> Unit) {
    val backgroundColor = remember(tile.backgroundColorHex, tile.partOfSpeech) {
        if (tile.backgroundColorHex != null) {
            try {
                Color(tile.backgroundColorHex.toColorInt())
            } catch (_: Exception) {
                resolveFitzgeraldColor(tile.partOfSpeech)
            }
        } else {
            resolveFitzgeraldColor(tile.partOfSpeech)
        }
    }

    val displayLabel = if (userGender == Gender.FEMALE) {
        tile.labelFeminine ?: tile.label
    } else {
        tile.label
    }

    val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f

    TileUI(
        label = displayLabel,
        imageUri = tile.imageUri,
        emoji = tile.emoji,
        backgroundColor = backgroundColor,
        aspectRatio = aspectRatio,
        isCategory = tile.isCategory,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    showSpeakerIcon: Boolean = false,
    contentColor: Color = contentColorFor(backgroundColor)
) {
    OutlinedCard(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clickable { onClick() },
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
                        
                        if (showSpeakerIcon) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MainCommunicationScreenPreview() {
    val sampleTiles = listOf(
        AACTile("1", "אני", "אני", emoji = "🙋‍♂️", isCategory = false, cellIndex = 0),
        AACTile("2", "רוצה", "רוצה", emoji = "❤️", isCategory = false, cellIndex = 1),
        AACTile("3", "אוכל", "אוכל", emoji = "🍕", isCategory = true, cellIndex = 2),
        AACTile("4", "שתייה", "שתייה", emoji = "🥤", isCategory = true, cellIndex = 3)
    )
    val sampleSentence = listOf(
        AACTile("1", "אני", "אני", emoji = "🙋‍♂️", isCategory = false, cellIndex = 0),
        AACTile("2", "רוצה", "רוצה", emoji = "❤️", isCategory = false, cellIndex = 1)
    )

    MyAACAppTheme {
        MainCommunicationScreenContent(
            tiles = sampleTiles,
            sentence = sampleSentence,
            currentParentId = "some_id",
            userGender = Gender.MALE,
            onToggleGender = {},
            onSpeak = {},
            onClear = {},
            onBackspace = {},
            onTileClick = {},
            onBackClick = {},
            onNavigateToAdmin = {}
        )
    }
}
