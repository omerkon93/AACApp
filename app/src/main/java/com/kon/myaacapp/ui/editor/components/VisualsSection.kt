package com.kon.myaacapp.ui.editor.components

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImageOptions
import com.kon.myaacapp.R
import com.kon.myaacapp.service.image.CustomCropImageContract
import com.kon.myaacapp.service.image.CustomCropImageContractOptions
import com.kon.myaacapp.service.image.ImageStorageService
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualsSection(
    emoji: String,
    onEmojiChange: (String) -> Unit,
    imageUri: String?,
    onImageUriChange: (String?) -> Unit,
    partOfSpeech: String,
    onPartOfSpeechChange: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val imageStorageService = remember(appContext) { ImageStorageService(appContext) }
    val orientation = LocalConfiguration.current.orientation

    var showEmojiPicker by remember { mutableStateOf(false) }
    var tempEmoji by remember { mutableStateOf("") }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val focusRequester = remember { FocusRequester() }

    val cropLauncher = rememberLauncherForActivityResult(CustomCropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { croppedUri ->
                imageStorageService.saveImage(croppedUri)?.let { savedPath ->
                    onImageUriChange(savedPath)
                    onEmojiChange("")
                }
            }
        }
    }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                tempCameraUri?.let { uri ->
                    cropLauncher.launch(
                        CustomCropImageContractOptions(
                            uri = uri,
                            cropImageOptions = CropImageOptions(
                                aspectRatioX = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 12 else 10,
                                aspectRatioY = 10,
                                fixAspectRatio = true,
                            )
                        )
                    )
                }
            }
        }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                cropLauncher.launch(
                    CustomCropImageContractOptions(
                        uri = uri,
                        cropImageOptions = CropImageOptions(
                            aspectRatioX = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 12 else 10,
                            aspectRatioY = 10,
                            fixAspectRatio = true,
                        )
                    )
                )
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                val uri = imageStorageService.getTempUri()
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            }
        }

    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = {
                Text(
                    stringResource(R.string.enter_emoji),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = tempEmoji,
                        onValueChange = { if (it.length <= 4) tempEmoji = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.type_emoji_here)) },
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 24.sp
                        ),
                        singleLine = true,
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempEmoji.isNotBlank()) {
                            onEmojiChange(tempEmoji)
                            onImageUriChange(null)
                            showEmojiPicker = false
                        }
                    },
                    enabled = tempEmoji.isNotBlank(),
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEmojiPicker = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    EditSection(
        title = stringResource(R.string.section_visuals),
        icon = Icons.Default.Palette,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        iconColor = MaterialTheme.colorScheme.tertiary,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                stringResource(R.string.tile_appearance),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(100.dp).clickable {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
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
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text(
                            stringResource(R.string.take_photo),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).height(100.dp).clickable {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = Color.Transparent,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Text(
                            stringResource(R.string.pick_from_gallery),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).height(100.dp)
                        .clickable { tempEmoji = emoji; showEmojiPicker = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(emoji.ifBlank { "🍎" }, fontSize = 32.sp)
                        Text(
                            stringResource(R.string.change_emoji),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.part_of_speech_label),
                style = MaterialTheme.typography.labelLarge
            )
            val posOptions = listOf("NONE", "NOUN", "VERB", "ADJECTIVE", "PRONOUN", "SOCIAL")
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

                    val selectedBgColor = if (isSelected) {
                        if (pos == "NONE") Color.LightGray.copy(alpha = 0.3f) else color.copy(alpha = 0.3f)
                    } else Color.Transparent

                    val selectedBorderColor = if (isSelected) {
                        if (pos == "NONE") Color.DarkGray else color
                    } else MaterialTheme.colorScheme.outlineVariant

                    Surface(
                        modifier = Modifier.clickable { onPartOfSpeechChange(pos) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = selectedBgColor,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, selectedBorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(16.dp).background(color, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Text(
                                stringResource(labelRes),
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