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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.content.Intent
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit
) {
    val tiles by viewModel.allTiles.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    var showTileDialog by remember { mutableStateOf(false) }
    var editingTile by remember { mutableStateOf<AACTile?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { viewModel.exportDatabase(it, context.contentResolver) }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importDatabase(it, context.contentResolver) }
        }
    )

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
                    IconButton(onClick = { exportLauncher.launch("myaac_backup.json") }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingTile = null
                showTileDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Tile")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(tiles) { tile ->
                TileRow(
                    tile = tile,
                    onDelete = { viewModel.deleteTile(tile) },
                    onClick = {
                        editingTile = tile
                        showTileDialog = true
                    }
                )
                HorizontalDivider()
            }
        }

        if (showTileDialog) {
            TileDialog(
                viewModel = viewModel,
                existingTile = editingTile,
                onDismiss = { showTileDialog = false }
            )
        }
    }
}

@Composable
fun TileRow(tile: AACTile, onDelete: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (tile.backgroundColorHex != null) {
                        try { Color(android.graphics.Color.parseColor(tile.backgroundColorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primaryContainer }
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (tile.imageUri != null) {
                AsyncImage(
                    model = tile.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (tile.emoji != null) {
                Text(text = tile.emoji, fontSize = 20.sp)
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
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileDialog(
    viewModel: AACViewModel,
    existingTile: AACTile? = null,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(existingTile?.label ?: "") }
    var ttsText by remember { mutableStateOf(existingTile?.ttsText ?: "") }
    var emoji by remember { mutableStateOf(existingTile?.emoji ?: "") }
    var imageUri by remember { mutableStateOf<String?>(existingTile?.imageUri) }
    var isCategory by remember { mutableStateOf(existingTile?.isCategory ?: false) }
    var parentId by remember { mutableStateOf<String?>(existingTile?.parentId) }
    var backgroundColorHex by remember { mutableStateOf(existingTile?.backgroundColorHex ?: "") }
    
    var partOfSpeech by remember { mutableStateOf(existingTile?.partOfSpeech ?: "NONE") }
    var isQuickFire by remember { mutableStateOf(existingTile?.isQuickFire ?: false) }
    var linkedCategoryId by remember { mutableStateOf<String?>(existingTile?.linkedCategoryId) }
    var labelFeminine by remember { mutableStateOf(existingTile?.labelFeminine ?: "") }
    var ttsTextFeminine by remember { mutableStateOf(existingTile?.ttsTextFeminine ?: "") }
    var grammaticalGender by remember { mutableStateOf(existingTile?.grammaticalGender ?: "M") }
    var audioUri by remember { mutableStateOf<String?>(existingTile?.audioUri) }
    var cellIndex by remember { mutableStateOf(existingTile?.cellIndex?.toString() ?: "") }

    // Recording State
    var isRecording by remember { mutableStateOf(false) }

    val categories by viewModel.allCategories.collectAsState()
    var categoryExpanded by remember { mutableStateOf(false) }
    var posExpanded by remember { mutableStateOf(false) }
    var linkedCategoryExpanded by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val posOptions = listOf("NONE", "NOUN", "VERB", "ADJECTIVE", "PRONOUN", "SOCIAL")

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                imageUri = uri.toString()
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTile == null) "Add New Professional Tile" else "Edit Tile") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (Default/Masculine)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = ttsText,
                        onValueChange = { ttsText = it },
                        label = { Text("TTS Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { emoji = it },
                            label = { Text("Emoji") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("Photo")
                        }
                    }
                }

                // Audio Recording Button
                item {
                    Button(
                        onClick = {
                            if (isRecording) {
                                viewModel.audioService.stopRecording()
                                isRecording = false
                            } else {
                                val tempId = java.util.UUID.randomUUID().toString()
                                audioUri = viewModel.audioService.startRecording(tempId)
                                if (audioUri != null) isRecording = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRecording) "Stop Recording" else "Record Custom Voice")
                    }
                    if (audioUri != null && !isRecording) {
                        Text("Voice Recorded ✓", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
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
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
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
                        label = { Text("Feminine Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = ttsTextFeminine,
                        onValueChange = { ttsTextFeminine = it },
                        label = { Text("Feminine TTS") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Grid Position
                item {
                    OutlinedTextField(
                        value = cellIndex,
                        onValueChange = { cellIndex = it },
                        label = { Text("Cell Index (0-11 for Motor Planning)") },
                        modifier = Modifier.fillMaxWidth()
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
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
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
                                cellIndex = cellIndex.toIntOrNull()
                            )
                        } else {
                            viewModel.updateTile(
                                existingTile.copy(
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
                                    cellIndex = cellIndex.toIntOrNull()
                                )
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = label.isNotBlank() && ttsText.isNotBlank()
            ) {
                Text(if (existingTile == null) "Save Professional Tile" else "Update Tile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
