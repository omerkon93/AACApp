package com.kon.myaacapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit,
) {
    val tiles by viewModel.allTiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filteredTiles = remember(searchQuery, tiles) {
        if (searchQuery.isBlank()) {
            tiles
        } else {
            tiles.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.ttsText.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val showTileDialog = remember { mutableStateOf(false) }
    val editingTile = remember { mutableStateOf<AACTile?>(null) }
    val tileToDelete = remember { mutableStateOf<AACTile?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var recordingTileId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            recordingTileId?.let { tileId ->
                val newUri = viewModel.audioService.startRecording(tileId)
                if (newUri == null) recordingTileId = null
            }
        } else {
            recordingTileId = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let { viewModel.exportDatabase(it, context.contentResolver) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importDatabase(it, context.contentResolver) }
    }

    LaunchedEffect(importExportStatus) {
        importExportStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportExportStatus()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            title = { 
                                Text(
                                    "הגדרות כפתורים", 
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineMedium
                                ) 
                            },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack, 
                                        contentDescription = "חזרה",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            actions = {
                                Button(
                                    onClick = onNavigateBack,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("שמירה", fontWeight = FontWeight.Bold)
                                }
                            },
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ניהול נתונים:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(onClick = { /* Share action */ }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Share, contentDescription = "שתף")
                                }
                            }
                            IconButton(onClick = { exportLauncher.launch("myaac_backup.zip") }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Download, contentDescription = "ייצוא")
                                }
                            }
                            IconButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Upload, contentDescription = "ייבוא")
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            bottomBar = {
                BottomNavigation(onAddTile = {
                    editingTile.value = null
                    showTileDialog.value = true
                })
            }
        ) { padding ->
            val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()

            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues = padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    GlobalSettingsCard(
                        speakOnTilePress = speakOnTilePress,
                        onSpeakOnTilePressChange = { viewModel.updateSpeakOnTilePress(it) }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ניהול אריחים",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                editingTile.value = null
                                showTileDialog.value = true
                            }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("הוספת אריח חדש", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = { Text("חיפוש לפי שם אריח או טקסט הקראה...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "נקה")
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                items(filteredTiles, key = { it.id }) { tile ->
                    TileCard(
                        tile = tile,
                        isRecording = recordingTileId == tile.id,
                        onDelete = { tileToDelete.value = tile },
                        onEdit = {
                            editingTile.value = tile
                            showTileDialog.value = true
                        },
                        onQuickRecord = {
                            viewModel.viewModelScope.launch {
                                if (recordingTileId == tile.id) {
                                    viewModel.audioService.stopRecording()
                                    val outputDir = java.io.File(context.filesDir, "audio_tiles")
                                    val outputFile = java.io.File(outputDir, "audio_${tile.id}.wav")
                                    viewModel.updateTileAudioUri(tile.id, outputFile.absolutePath)
                                    recordingTileId = null
                                } else {
                                    if (recordingTileId != null) {
                                        viewModel.audioService.stopRecording()
                                        recordingTileId = null
                                    }

                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED

                                    recordingTileId = tile.id
                                    if (hasPermission) {
                                        val newUri = viewModel.audioService.startRecording(tile.id)
                                        if (newUri == null) recordingTileId = null
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    if (tileToDelete.value != null) {
        AlertDialog(
            onDismissRequest = { tileToDelete.value = null },
            title = { Text("מחיקת אריח?") },
            text = { Text("פעולה זו תסיר לצמיתות את '${tileToDelete.value?.label}' וכל הקלטת קול קשורה. לא ניתן לבטל פעולה זו.") },
            confirmButton = {
                Button(
                    onClick = {
                        tileToDelete.value?.let { viewModel.deleteTile(it) }
                        tileToDelete.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("מחק")
                }
            },
            dismissButton = {
                TextButton(onClick = { tileToDelete.value = null }) {
                    Text("ביטול")
                }
            }
        )
    }

    if (showTileDialog.value) {
        TileEditDialog(
            viewModel = viewModel,
            existingTile = editingTile.value,
            onDismiss = {
                showTileDialog.value = false
                editingTile.value = null
            }
        )
    }
}

@Composable
fun GlobalSettingsCard(
    speakOnTilePress: Boolean,
    onSpeakOnTilePressChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "הגדרות גלובליות",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "הגדרות המשפיעות על כל הממשק עבור המשתמש",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "הקראת מילים בלחיצה",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "טקסט לדיבור (TTS) יופעל בכל בחירת אריח",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = speakOnTilePress,
                        onCheckedChange = onSpeakOnTilePressChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TileCard(
    tile: AACTile,
    isRecording: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQuickRecord: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tile Image/Emoji
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tile.imageUri != null) {
                    AsyncImage(
                        model = tile.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = tile.emoji ?: "✨", fontSize = 32.sp)
                }
            }

            // Tile Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "תווית",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "טקסט להקראה",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tile.ttsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "מחיקה", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "עריכה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onQuickRecord) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "הקלטה מהירה",
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigation(onAddTile: () -> Unit) {
    // We use a Box with no clipping to allow the FAB to float above the bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(icon = Icons.Default.Dashboard, label = "בית")
                BottomNavItem(icon = Icons.Default.GridView, label = "לוחות")
                Spacer(modifier = Modifier.width(64.dp)) // Space for FAB
                BottomNavItem(icon = Icons.Default.Analytics, label = "נתונים")
                BottomNavItem(icon = Icons.Default.Settings, label = "הגדרות", isSelected = true)
            }
        }

        // Floating Add Button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-32).dp) // Half of the size to center it on the top edge
                .size(64.dp)
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { onAddTile() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "הוספה", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun RowScope.BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean = false) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
