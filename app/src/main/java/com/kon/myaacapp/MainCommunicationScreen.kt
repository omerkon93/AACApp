package com.kon.myaacapp

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.core.graphics.toColorInt
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
    tiles: List<AACTile>,
    sentence: List<AACTile>,
    currentParentId: String?,
    userGender: Gender,
    langCode: String,
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

    val layoutDir = if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Sentence Bar
            SentenceBar(
                sentence = sentence,
                modifier = Modifier
                    .pointerInput(Unit) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // Action Bar
            ActionBar(
                onBackClick = onBackClick,
                onClear = onClear,
                onBackspace = onBackspace,
                onSpeak = onSpeak,
                isRoot = currentParentId == null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tiles Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
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
                        val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f
                        Spacer(modifier = Modifier.aspectRatio(aspectRatio))
                    }
                }
            }
        }
    }
}

@Composable
fun SentenceBar(
    sentence: List<AACTile>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        LazyRow(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(sentence) { tile ->
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ActionBar(
    onBackClick: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSpeak: () -> Unit,
    isRoot: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [ Back/Up ] [ Clear (X) ] [ Backspace ] [ Speak ]
        
        ActionButton(
            onClick = onBackClick,
            enabled = !isRoot,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            containerColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            onClick = onClear,
            icon = Icons.Default.Clear,
            contentDescription = stringResource(R.string.clear),
            containerColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            onClick = onBackspace,
            icon = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.backspace),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )

        ActionButton(
            onClick = onSpeak,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(R.string.speak),
            containerColor = PrimaryBlue,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = if (containerColor.luminance() > 0.5f) Color.Black else Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp)
        )
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
        title = { Text(stringResource(R.string.admin_access)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_admin_password))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.default_password_hint),
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
                Text(stringResource(R.string.unlock))
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
    contentColor: Color = if (backgroundColor.luminance() > 0.4f) Color.Black else Color.White
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

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape", apiLevel = 35)
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
            langCode = "he",
            onSpeak = {},
            onClear = {},
            onBackspace = {},
            onTileClick = {},
            onBackClick = {},
            onNavigateToAdmin = {}
        )
    }
}
