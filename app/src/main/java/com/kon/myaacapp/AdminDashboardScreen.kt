package com.kon.myaacapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit,
) {
    val tiles by viewModel.allTiles.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val showTileDialog = remember { mutableStateOf(value = false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("myaac_backup.zip") }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTile.value = null
                    showTileDialog.value = true
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tile")
            }
        },
    ) { padding ->
        val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()

        LazyColumn(
            modifier = Modifier
                .padding(paddingValues = padding)
                .fillMaxSize(),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Global Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speak Words on Tap")
                        Switch(
                            checked = speakOnTilePress,
                            onCheckedChange = { viewModel.updateSpeakOnTilePress(it) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                }
            }

            items(tiles) { tile ->
                val isThisTileRecording = recordingTileId == tile.id
                TileRow(
                    tile = tile,
                    isRecording = isThisTileRecording,
                    onDelete = { tileToDelete.value = tile },
                    onPlayAudio = { tile.audioUri?.let { viewModel.audioService.playRecording(it) } },
                    onClearAudio = { viewModel.updateTileAudioUri(tile.id, null) },
                    onQuickRecord = {
                        viewModel.viewModelScope.launch {
                            if (isThisTileRecording) {
                                viewModel.audioService.stopRecording()
                                // Get the URI (it's predictable based on tileId in AudioRecordingService)
                                val outputDir = java.io.File(context.filesDir, "audio_tiles")
                                val outputFile = java.io.File(outputDir, "audio_${tile.id}.wav")
                                viewModel.updateTileAudioUri(tile.id, outputFile.absolutePath)
                                recordingTileId = null
                            } else {
                                // Stop any other active recording first (safety)
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
                    },
                    onClick = {
                        editingTile.value = tile
                        showTileDialog.value = true
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (tileToDelete.value != null) {
        AlertDialog(
            onDismissRequest = { tileToDelete.value = null },
            title = { Text("Delete Tile?") },
            text = { Text("This action will permanently remove '${tileToDelete.value?.label}' and any associated custom voice recordings. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        tileToDelete.value?.let { viewModel.deleteTile(it) }
                        tileToDelete.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tileToDelete.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTileDialog.value) {
        TileDialog(
            viewModel = viewModel,
            existingTile = editingTile.value,
        ) {
            showTileDialog.value = false
            editingTile.value = null
        }
    }
}

@Composable
fun TileRow(
    tile: AACTile,
    isRecording: Boolean,
    onDelete: () -> Unit,
    onQuickRecord: () -> Unit,
    onPlayAudio: () -> Unit,
    onClearAudio: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (tile.backgroundColorHex != null) {
                        try {
                            Color(tile.backgroundColorHex.toColorInt())
                        } catch (_: Exception) {
                            resolveFitzgeraldColor(tile.partOfSpeech)
                        }
                    } else {
                        val fitzColor = resolveFitzgeraldColor(tile.partOfSpeech)
                        if (fitzColor == Color.White) MaterialTheme.colorScheme.primaryContainer else fitzColor
                    },
                    shape = RoundedCornerShape(size = 4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.imageUri != null) {
                AsyncImage(
                    model = tile.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape = RoundedCornerShape(size = 4.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else tile.emoji?.let {
                Text(text = it, fontSize = 20.sp)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tile.label, style = MaterialTheme.typography.titleMedium)
            Text(text = "TTS: ${tile.ttsText}", style = MaterialTheme.typography.bodySmall)
            if (tile.isCategory) {
                Text(text = "Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // Audio Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRecording) {
                IconButton(onClick = onQuickRecord) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else if (tile.audioUri != null) {
                IconButton(onClick = onPlayAudio) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Recording",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onClearAudio) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Recording",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                IconButton(onClick = onQuickRecord) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Recording",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Separation
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(8.dp))
        
        // Tile Deletion
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Tile",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileDialog(
    viewModel: AACViewModel,
    existingTile: AACTile? = null,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(existingTile?.label ?: "") }
    var ttsText by remember { mutableStateOf(existingTile?.ttsText ?: "") }
    var emoji by remember { mutableStateOf(existingTile?.emoji ?: "") }
    var imageUri by remember { mutableStateOf(existingTile?.imageUri) }
    var isCategory by remember { mutableStateOf(existingTile?.isCategory ?: false) }
    var parentId by remember { mutableStateOf(existingTile?.parentId) }
    var backgroundColorHex by remember { mutableStateOf(existingTile?.backgroundColorHex ?: "") }
    
    var partOfSpeech by remember { mutableStateOf(existingTile?.partOfSpeech ?: "NONE") }
    var isQuickFire by remember { mutableStateOf(existingTile?.isQuickFire ?: false) }
    var linkedCategoryId by remember { mutableStateOf(existingTile?.linkedCategoryId) }
    var labelFeminine by remember { mutableStateOf(existingTile?.labelFeminine ?: "") }
    var ttsTextFeminine by remember { mutableStateOf(existingTile?.ttsTextFeminine ?: "") }
    var grammaticalGender by remember { mutableStateOf(existingTile?.grammaticalGender ?: "M") }
    var audioUri by remember { mutableStateOf(existingTile?.audioUri) }
    var cellIndex by remember { mutableStateOf(existingTile?.cellIndex?.toString() ?: "") }

    // Recording State
    var isRecording by remember { mutableStateOf(value = false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }

    // Task: Recording Timer Logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = (System.currentTimeMillis() - startTime) / 1000
                delay(100)
            }
        } else {
            recordingDuration = 0L
        }
    }

    val categories by viewModel.allCategories.collectAsState()
    var posExpanded by remember { mutableStateOf(value = false) }
    var linkedCategoryExpanded by remember { mutableStateOf(value = false) }
    
    val context = LocalContext.current
    val posOptions = listOf("NONE", "NOUN", "VERB", "ADJECTIVE", "PRONOUN", "SOCIAL")

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            val tempId = java.util.UUID.randomUUID().toString()
            audioUri = viewModel.audioService.startRecording(tempId)
            if (audioUri != null) isRecording = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            imageUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTile == null) "Add New Professional Tile" else "Edit Tile") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(text = "Label (Default/Masculine)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = ttsText,
                        onValueChange = { ttsText = it },
                        label = { Text(text = "TTS Text") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { emoji = it },
                            label = { Text(text = "Emoji") },
                            modifier = Modifier.weight(weight = 1f),
                        )
                        
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    input = PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) {
                            Text(text = "Photo")
                        }
                    }
                }

                // Audio Recording Button
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                viewModel.viewModelScope.launch {
                                    if (isRecording) {
                                        viewModel.audioService.stopRecording()
                                        isRecording = false
                                    } else {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasPermission) {
                                            val tempId = java.util.UUID.randomUUID().toString()
                                            audioUri = viewModel.audioService.startRecording(tempId)
                                            if (audioUri != null) isRecording = true
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(width = 8.dp))
                            Text(
                                text = if (isRecording) {
                                    "Stop (${recordingDuration}s)"
                                } else {
                                    "Record Custom Voice"
                                },
                            )
                        }

                        if ((audioUri != null) && !isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { audioUri?.let { viewModel.audioService.playRecording(it) } },
                                    modifier = Modifier.weight(weight = 1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Text(text = "Play Review")
                                }

                                OutlinedButton(
                                    onClick = {
                                        audioUri?.let { viewModel.audioService.deleteRecording(it) }
                                        audioUri = null
                                    },
                                    modifier = Modifier.weight(weight = 1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                    Text(text = "Delete Voice")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCategory, onCheckedChange = { isCategory = it })
                        Text("Is Category (Folder)")
                        Spacer(Modifier.width(16.dp))
                        Checkbox(checked = isQuickFire, onCheckedChange = { isQuickFire = it })
                        Text("QuickFire")
                    }
                }

                // POS Dropdown
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = partOfSpeech,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Part of Speech (Fitzgerald Color)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { posExpanded = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                        )
                        DropdownMenu(expanded = posExpanded, onDismissRequest = { posExpanded = false }) {
                            posOptions.forEach { pos ->
                                DropdownMenuItem(text = { Text(pos) }, onClick = { partOfSpeech = pos; posExpanded = false })
                            }
                        }
                    }
                }

                // Advanced Section Header
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Hebrew & Navigation (Optional)", style = MaterialTheme.typography.labelLarge)
                }

                item {
                    OutlinedTextField(
                        value = labelFeminine,
                        onValueChange = { labelFeminine = it },
                        label = { Text(text = "Feminine Label") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = ttsTextFeminine,
                        onValueChange = { ttsTextFeminine = it },
                        label = { Text(text = "Feminine TTS") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Grid Position
                item {
                    OutlinedTextField(
                        value = cellIndex,
                        onValueChange = { cellIndex = it },
                        label = { Text(text = "Cell Index (0-11 for Motor Planning)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Linked Category Dropdown
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = categories.find { it.id == linkedCategoryId }?.label ?: "None (No Jump)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Jump to Category after Speech") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { linkedCategoryExpanded = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                        )
                        DropdownMenu(expanded = linkedCategoryExpanded, onDismissRequest = { linkedCategoryExpanded = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { linkedCategoryId = null; linkedCategoryExpanded = false })
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.label) }, onClick = { linkedCategoryId = cat.id; linkedCategoryExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank() && ttsText.isNotBlank()) {
                        if (existingTile == null) {
                            viewModel.addTile(
                                label = label,
                                ttsText = ttsText,
                                emoji = emoji.ifBlank { null },
                                imageUri = imageUri,
                                isCategory = isCategory,
                                parentId = parentId,
                                backgroundColorHex = backgroundColorHex.ifBlank { null },
                                partOfSpeech = if (partOfSpeech == "NONE") null else partOfSpeech,
                                isQuickFire = isQuickFire,
                                linkedCategoryId = linkedCategoryId,
                                labelFeminine = labelFeminine.ifBlank { null },
                                ttsTextFeminine = ttsTextFeminine.ifBlank { null },
                                grammaticalGender = grammaticalGender,
                                audioUri = audioUri,
                                cellIndex = cellIndex.toIntOrNull(),
                            )
                        } else {
                            viewModel.updateTile(
                                tile = existingTile.copy(
                                    label = label,
                                    ttsText = ttsText,
                                    emoji = emoji.ifBlank { null },
                                    imageUri = imageUri,
                                    isCategory = isCategory,
                                    parentId = parentId,
                                    backgroundColorHex = backgroundColorHex.ifBlank { null },
                                    partOfSpeech = if (partOfSpeech == "NONE") null else partOfSpeech,
                                    isQuickFire = isQuickFire,
                                    linkedCategoryId = linkedCategoryId,
                                    labelFeminine = labelFeminine.ifBlank { null },
                                    ttsTextFeminine = ttsTextFeminine.ifBlank { null },
                                    grammaticalGender = grammaticalGender,
                                    audioUri = audioUri,
                                    cellIndex = cellIndex.toIntOrNull(),
                                ),
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = label.isNotBlank() && ttsText.isNotBlank(),
            ) {
                Text(text = if (existingTile == null) "Save Professional Tile" else "Update Tile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
