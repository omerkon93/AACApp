package com.kon.myaacapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImageOptions
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditDialog(
    viewModel: AACViewModel,
    existingTile: AACTile? = null,
    initialCellIndex: Int? = null,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(existingTile?.label ?: "") }
    var ttsText by remember { mutableStateOf(existingTile?.ttsText ?: "") }
    var tileId by remember { mutableStateOf(existingTile?.id ?: "") }
    var emoji by remember { mutableStateOf(existingTile?.emoji ?: "") }
    var imageUri by remember { mutableStateOf(existingTile?.imageUri) }
    var isCategory by remember { mutableStateOf(existingTile?.isCategory ?: false) }
    var parentId by remember { mutableStateOf(if (existingTile == null) viewModel.currentParentId.value else existingTile.parentId) }
    var backgroundColorHex by remember { mutableStateOf(existingTile?.backgroundColorHex ?: "") }

    var partOfSpeech by remember { mutableStateOf(existingTile?.partOfSpeech ?: "NONE") }
    var isQuickFire by remember { mutableStateOf(existingTile?.isQuickFire ?: false) }
    var linkedCategoryId by remember { mutableStateOf(existingTile?.linkedCategoryId) }
    var labelFeminine by remember { mutableStateOf(existingTile?.labelFeminine ?: "") }
    var ttsTextFeminine by remember { mutableStateOf(existingTile?.ttsTextFeminine ?: "") }
    var grammaticalGender by remember { mutableStateOf(existingTile?.grammaticalGender ?: "M") }
    var audioUri by remember { mutableStateOf(existingTile?.audioUri) }
    var cellIndex by remember { 
        mutableStateOf(
            existingTile?.cellIndex?.toString() ?: initialCellIndex?.toString() ?: ""
        )
    }

    // Dropdown and Overwrite Logic
    val maxCapacity = 15 // 0-14
    val tilesInParent by viewModel.getTilesByParentId(parentId).collectAsState(initial = emptyList())
    var showOverwriteDialog by remember { mutableStateOf(value = false) }
    var pendingCellIndex by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(value = false) }

    // Emoji Picker State
    var showEmojiPicker by remember { mutableStateOf(value = false) }
    var tempEmoji by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Recording State
    var isRecording by remember { mutableStateOf(value = false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = (System.currentTimeMillis() - startTime) / 1000
                delay(100.milliseconds)
            }
        } else {
            recordingDuration = 0L
        }
    }

    val categories by viewModel.allCategories.collectAsState()
    val posOptions = listOf("NONE", "NOUN", "VERB", "ADJECTIVE", "PRONOUN", "SOCIAL")
    val context = LocalContext.current
    val imageStorageService = remember { ImageStorageService(context) }
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = CustomCropImageContract(),
    ) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                val savedPath = imageStorageService.saveImage(croppedUri)
                if (savedPath != null) {
                    imageUri = savedPath
                    emoji = ""
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                val cropOptions = CustomCropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        aspectRatioX = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 12 else 10,
                        aspectRatioY = 10,
                        fixAspectRatio = true,
                    ),
                )
                cropLauncher.launch(cropOptions)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val cropOptions = CustomCropImageContractOptions(
                uri = uri,
                cropImageOptions = CropImageOptions(
                    aspectRatioX = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 12 else 10,
                    aspectRatioY = 10,
                    fixAspectRatio = true,
                ),
            )
            cropLauncher.launch(cropOptions)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            val uri = imageStorageService.getTempUri()
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            val tempId = UUID.randomUUID().toString()
            audioUri = viewModel.audioService.startRecording(tempId)
            if (audioUri != null) isRecording = true
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (existingTile == null) stringResource(R.string.add_tile_title) else stringResource(R.string.edit_tile_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }

                    if (showOverwriteDialog) {
                        AlertDialog(
                            onDismissRequest = { showOverwriteDialog = false },
                            title = {
                                Text(
                                    text = stringResource(R.string.cell_occupied_title),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            text = {
                                val occupiedTile = tilesInParent.find { it.cellIndex?.toString() == pendingCellIndex }
                                Text(
                                    text = stringResource(R.string.cell_occupied_msg, occupiedTile?.label ?: ""),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        cellIndex = pendingCellIndex
                                        showOverwriteDialog = false
                                    },
                                ) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showOverwriteDialog = false },
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    if (showEmojiPicker) {
                        AlertDialog(
                            onDismissRequest = { showEmojiPicker = false },
                            title = {
                                Text(
                                    text = stringResource(R.string.enter_emoji),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    OutlinedTextField(
                                        value = tempEmoji,
                                        onValueChange = {
                                            if (it.length <= 4) { // Allow for compound emojis
                                                tempEmoji = it
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        placeholder = { Text(stringResource(R.string.type_emoji_here)) },
                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp),
                                        singleLine = true,
                                    )
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (tempEmoji.isNotBlank()) {
                                            emoji = tempEmoji
                                            imageUri = null
                                            showEmojiPicker = false
                                        }
                                    },
                                    enabled = tempEmoji.isNotBlank(),
                                ) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showEmojiPicker = false },
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.edit_tile_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Section 1: Basic Info
                        item {
                            EditSection(
                                title = stringResource(R.string.section_basic),
                                icon = Icons.Default.Info,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                iconColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.display_label_text), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = label,
                                            onValueChange = { label = it },
                                            placeholder = { Text(stringResource(R.string.label_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.tts_text_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = ttsText,
                                            onValueChange = { ttsText = it },
                                            placeholder = { Text(stringResource(R.string.tts_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2: Visuals
                        item {
                            EditSection(
                                title = stringResource(R.string.section_visuals),
                                icon = Icons.Default.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                iconColor = MaterialTheme.colorScheme.tertiary,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Text(stringResource(R.string.tile_appearance), style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        // Camera Picker
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(100.dp)
                                                .clickable {
                                                    val hasPermission = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.CAMERA,
                                                    ) == PackageManager.PERMISSION_GRANTED

                                                    if (hasPermission) {
                                                        val uri = imageStorageService.getTempUri()
                                                        tempCameraUri = uri
                                                        cameraLauncher.launch(uri)
                                                    } else {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                                            color = Color.Transparent,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                                Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        // Gallery Picker
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(100.dp)
                                                .clickable {
                                                    galleryLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                    )
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                                            color = Color.Transparent,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                                Text(stringResource(R.string.pick_from_gallery), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        // Emoji Picker Logic
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(100.dp)
                                                .clickable {
                                                    tempEmoji = emoji
                                                    showEmojiPicker = true
                                                },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Text(emoji.ifBlank { "🍎" }, fontSize = 32.sp)
                                                Text(stringResource(R.string.change_emoji), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    // Part of Speech / Color
                                    Text(stringResource(R.string.part_of_speech_label), style = MaterialTheme.typography.labelLarge)
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        posOptions.forEach { pos ->
                                            val color = resolveFitzgeraldColor(pos)
                                            val isSelected = partOfSpeech == pos
                                            val labelRes = when (pos) {
                                                "NONE" -> R.string.pos_none
                                                "NOUN" -> R.string.pos_noun
                                                "VERB" -> R.string.pos_verb
                                                "ADJECTIVE" -> R.string.pos_adjective
                                                "PRONOUN" -> R.string.pos_pronoun
                                                "SOCIAL" -> R.string.pos_social
                                                else -> R.string.pos_none
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .clickable { partOfSpeech = pos },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) color.copy(alpha = 0.3f) else Color.Transparent,
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .background(color, CircleShape)
                                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                    )
                                                    Text(
                                                        text = stringResource(labelRes),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Audio Section
                        item {
                            EditSection(
                                title = stringResource(R.string.section_audio),
                                icon = Icons.Default.Mic,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                iconColor = MaterialTheme.colorScheme.secondary,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                                        val tempId = UUID.randomUUID().toString()
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
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isRecording) stringResource(R.string.stop_recording, recordingDuration) else stringResource(R.string.custom_voice_recording))
                                    }

                                    if ((audioUri != null) && !isRecording) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(
                                                onClick = { audioUri?.let { viewModel.audioService.playRecording(it) } },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Text(stringResource(R.string.test_audio))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    audioUri?.let { viewModel.audioService.deleteRecording(it) }
                                                    audioUri = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                                Text(stringResource(R.string.delete_action))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 4: Advanced
                        item {
                            EditSection(
                                title = stringResource(R.string.section_advanced),
                                icon = Icons.Default.Settings,
                                iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("מזהה ייחודי (ID) - למתקדמים", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = tileId,
                                            onValueChange = { tileId = it.trim() },
                                            placeholder = { Text("לדוגמה: my_custom_id") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = existingTile == null,
                                            supportingText = {
                                                if (existingTile != null) {
                                                    Text("לא ניתן לשנות מזהה (ID) של אריח קיים.", color = MaterialTheme.colorScheme.error)
                                                } else {
                                                    Text("השאר ריק כדי שהמערכת תיצור ID אקראי באופן אוטומטי.")
                                                }
                                            }
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.parent_category_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        var parentExpanded by remember { mutableStateOf(value = false) }
                                        Box {
                                            OutlinedTextField(
                                                value = categories.find { it.id == parentId }?.label ?: stringResource(R.string.root_home),
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                trailingIcon = {
                                                    IconButton(onClick = { parentExpanded = true }) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                    }
                                                },
                                            )
                                            DropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.root_home)) }, onClick = { parentId = null; parentExpanded = false })
                                                categories.filter { (it.isCategory) && (it.id != existingTile?.id) }.forEach { cat ->
                                                    DropdownMenuItem(text = { Text(cat.label) }, onClick = { parentId = cat.id; parentExpanded = false })
                                                }
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.cell_index_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        ExposedDropdownMenuBox(
                                            expanded = dropdownExpanded,
                                            onExpandedChange = { dropdownExpanded = it },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            OutlinedTextField(
                                                value = if (cellIndex.isEmpty()) stringResource(R.string.pick_location) else {
                                                    val tile = tilesInParent.find { it.cellIndex?.toString() == cellIndex }
                                                    if (tile != null) "$cellIndex - ${tile.label}" else cellIndex
                                                },
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            )
                                            ExposedDropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                            ) {
                                                (0 until maxCapacity).forEach { index ->
                                                    val idxStr = index.toString()
                                                    val tileAtIdx = tilesInParent.find { it.cellIndex == index }
                                                    val isOccupied = (tileAtIdx != null) && (tileAtIdx.id != existingTile?.id)

                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = if (tileAtIdx != null) "$index - ${tileAtIdx.label}" else idxStr,
                                                                color = if (isOccupied) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        },
                                                        onClick = {
                                                            if (isOccupied) {
                                                                pendingCellIndex = idxStr
                                                                showOverwriteDialog = true
                                                            } else {
                                                                cellIndex = idxStr
                                                            }
                                                            dropdownExpanded = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.jump_to_category), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        var expanded by remember { mutableStateOf(value = false) }
                                        Box {
                                            OutlinedTextField(
                                                value = categories.find { it.id == linkedCategoryId }?.label ?: stringResource(R.string.none_speech_only),
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                trailingIcon = {
                                                    IconButton(onClick = { expanded = true }) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                    }
                                                },
                                            )
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.none_speech_only)) }, onClick = { linkedCategoryId = null; expanded = false })
                                                categories.forEach { cat ->
                                                    DropdownMenuItem(text = { Text(cat.label) }, onClick = { linkedCategoryId = cat.id; expanded = false })
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = isCategory, onCheckedChange = { isCategory = it })
                                            Text(stringResource(R.string.category_folder), style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = isQuickFire, onCheckedChange = { isQuickFire = it })
                                            Text(stringResource(R.string.quick_response), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        // Section 5: Hebrew
                        item {
                            EditSection(
                                title = stringResource(R.string.section_hebrew),
                                icon = Icons.Default.Translate,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                iconColor = MaterialTheme.colorScheme.secondary,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.label_feminine_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = labelFeminine,
                                            onValueChange = { labelFeminine = it },
                                            placeholder = { Text(stringResource(R.string.feminine_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.tts_feminine_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = ttsTextFeminine,
                                            onValueChange = { ttsTextFeminine = it },
                                            placeholder = { Text(stringResource(R.string.tts_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    }
                                }
                            }
                        }


                        // Live Preview Card
                        item {
                            val tileColor = resolveFitzgeraldColor(partOfSpeech)
                            val displayLabel = label.ifBlank { stringResource(R.string.label_placeholder) }
                            val aspectRatio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 1.2f else 1.0f

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                Text(stringResource(R.string.live_preview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

                                TileUI(
                                    label = displayLabel,
                                    imageUri = imageUri,
                                    emoji = if (imageUri == null) emoji.ifBlank { "🍎" } else null,
                                    backgroundColor = if (partOfSpeech == "NONE") MaterialTheme.colorScheme.primary else tileColor,
                                    aspectRatio = aspectRatio,
                                    isCategory = isCategory,
                                    onClick = {
                                        viewModel.playPreviewAudio(ttsText, audioUri)
                                    },
                                    modifier = Modifier.width(200.dp),
                                    labelFontSize = 24.sp,
                                    showSpeakerIcon = true,
                                )
                            }
                        }

                        item { Spacer(Modifier.height(32.dp)) }
                    }

                    // Footer Actions
                    Surface(
                        tonalElevation = 2.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.cancel_changes))
                            }
                            Button(
                                onClick = {
                                    if (label.isNotBlank() && ttsText.isNotBlank()) {
                                        if (existingTile == null) {
                                            viewModel.addTile(
                                                id = tileId.ifBlank { null },
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
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text(
                                    text = if (existingTile == null) stringResource(R.string.save_tile) else stringResource(R.string.update_tile),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun EditSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = iconContainerColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun TileEditDialogPreview() {
    MyAACAppTheme {
        // Mocking the viewModel call for preview
        // TileEditDialog(...)
    }
}
