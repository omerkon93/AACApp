package com.kon.myaacapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

enum class AdminTab {
    HOME, SETTINGS, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AdminTab.HOME) }
    var showTileDialog by remember { mutableStateOf(false) }
    var editingTile by remember { mutableStateOf<AACTile?>(null) }
    var tileToDelete by remember { mutableStateOf<AACTile?>(null) }
    var initialCellIndex by remember { mutableStateOf<Int?>(null) }
    var showTilePicker by remember { mutableStateOf(false) }
    
    val langCode by viewModel.languageCode.collectAsState()
    val layoutDir = if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            when (selectedTab) {
                                AdminTab.HOME -> stringResource(R.string.admin_tab_home)
                                AdminTab.SETTINGS -> stringResource(R.string.admin_tab_settings)
                                AdminTab.SYSTEM -> stringResource(R.string.admin_tab_system)
                            },
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            },
            bottomBar = {
                AdminBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    AdminTab.HOME -> {
                        AdminEditableGridScreen(
                            viewModel = viewModel,
                            onEditTile = { tile ->
                                editingTile = tile
                                initialCellIndex = tile?.cellIndex
                                showTileDialog = true
                            },
                            onCreateTile = { cellIndex ->
                                editingTile = null
                                initialCellIndex = cellIndex
                                showTilePicker = true
                            }
                        )
                    }
                    AdminTab.SETTINGS -> {
                        AdminListView(
                            viewModel = viewModel,
                            onEditTile = { tile ->
                                editingTile = tile
                                initialCellIndex = tile.cellIndex
                                showTileDialog = true
                            },
                            onDeleteTile = { tile ->
                                tileToDelete = tile
                            }
                        )
                    }
                    AdminTab.SYSTEM -> {
                        AdminSystemSettings(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showTilePicker) {
        TilePickerDialog(
            viewModel = viewModel,
            onDismiss = { showTilePicker = false },
            onTileSelected = { tile ->
                showTilePicker = false
                if (tile == null) {
                    // Create New
                    showTileDialog = true
                } else {
                    // Move existing tile to this cell
                    val currentParentId = viewModel.currentParentId.value
                    viewModel.updateTile(tile.copy(
                        parentId = currentParentId,
                        cellIndex = initialCellIndex
                    ))
                    initialCellIndex = null
                }
            }
        )
    }

    if (tileToDelete != null) {
        AlertDialog(
            onDismissRequest = { tileToDelete = null },
            title = { Text(stringResource(R.string.delete_tile_title)) },
            text = { Text(stringResource(R.string.delete_tile_confirm_msg, tileToDelete?.label ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        tileToDelete?.let { viewModel.deleteTile(it) }
                        tileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showTileDialog) {
        // Wrap the existing TileEditDialog
        TileEditDialog(
            viewModel = viewModel,
            existingTile = editingTile, 
            initialCellIndex = initialCellIndex,
            onDismiss = {
                showTileDialog = false
                editingTile = null
                initialCellIndex = null
            }
        )
    }
}

@Composable
fun TilePickerDialog(
    viewModel: AACViewModel,
    onDismiss: () -> Unit,
    onTileSelected: (AACTile?) -> Unit
) {
    val allTiles by viewModel.allTiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredTiles = remember(searchQuery, allTiles) {
        if (searchQuery.isBlank()) {
            allTiles
        } else {
            allTiles.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.ttsText.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_or_create_tile), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_existing_tile)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onTileSelected(null) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text(stringResource(R.string.new_tile), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    items(filteredTiles) { tile ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onTileSelected(tile) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (tile.imageUri != null) {
                                    AsyncImage(
                                        model = tile.imageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = tile.emoji ?: "✨",
                                        modifier = Modifier.align(Alignment.Center),
                                        fontSize = 24.sp
                                    )
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        tile.label,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun AdminBottomNavigation(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == AdminTab.HOME,
            onClick = { onTabSelected(AdminTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_home)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SETTINGS,
            onClick = { onTabSelected(AdminTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_tiles)) }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SYSTEM,
            onClick = { onTabSelected(AdminTab.SYSTEM) },
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_system)) }
        )
    }
}

@Composable
fun AdminSystemSettings(viewModel: AACViewModel) {
    val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()
    val langCode by viewModel.languageCode.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let { viewModel.exportDatabase(it, contentResolver) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importDatabase(it, contentResolver) }
    }

    LaunchedEffect(importExportStatus) {
        importExportStatus?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearImportExportStatus()
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetToDefault(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.general_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.speak_on_press))
                    Switch(
                        checked = speakOnTilePress,
                        onCheckedChange = { viewModel.updateSpeakOnTilePress(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grammatical Gender Selector
                val userGender by viewModel.userGender.collectAsState()
                Text(stringResource(R.string.grammatical_gender), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = userGender == Gender.MALE,
                        onClick = { viewModel.updateUserGender(Gender.MALE) },
                        label = { Text(stringResource(R.string.male)) }
                    )
                    FilterChip(
                        selected = userGender == Gender.FEMALE,
                        onClick = { viewModel.updateUserGender(Gender.FEMALE) },
                        label = { Text(stringResource(R.string.female)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Language Selector
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = langCode == "he",
                        onClick = { 
                            scope.launch {
                                viewModel.updateLanguageCode("he")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        label = { Text(stringResource(R.string.hebrew)) }
                    )
                    FilterChip(
                        selected = langCode == "en",
                        onClick = { 
                            scope.launch {
                                viewModel.updateLanguageCode("en")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        label = { Text(stringResource(R.string.english)) }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.backup_and_restore), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { exportLauncher.launch("myaac_backup_${System.currentTimeMillis()}.zip") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_db))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_db))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        }
    }
}
