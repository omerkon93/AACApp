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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            when (selectedTab) {
                                AdminTab.HOME -> "עורך לוח"
                                AdminTab.SETTINGS -> "הגדרות אריחים"
                                AdminTab.SYSTEM -> "הגדרות מערכת"
                            },
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
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
            title = { Text("מחיקת אריח?") },
            text = { Text("האם אתה בטוח שברצונך למחוק את '${tileToDelete?.label}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        tileToDelete?.let { viewModel.deleteTile(it) }
                        tileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("מחק")
                }
            },
            dismissButton = {
                TextButton(onClick = { tileToDelete = null }) {
                    Text("ביטול")
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
        title = { Text("בחר אריח או צור חדש", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("חיפוש אריח קיים...") },
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
                                    Text("חדש", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    items(filteredTiles) { tile ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onTileSelected(tile) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
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
            TextButton(onClick = onDismiss) { Text("ביטול") }
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
            label = { Text("בית") }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SETTINGS,
            onClick = { onTabSelected(AdminTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("אריחים") }
        )
        NavigationBarItem(
            selected = selectedTab == AdminTab.SYSTEM,
            onClick = { onTabSelected(AdminTab.SYSTEM) },
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text("מערכת") }
        )
    }
}

@Composable
fun AdminSystemSettings(viewModel: AACViewModel) {
    val speakOnTilePress by viewModel.speakOnTilePress.collectAsState()
    val importExportStatus by viewModel.importExportStatus.collectAsState()
    val context = LocalContext.current
    val contentResolver = context.contentResolver

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
                Text("הגדרות כלליות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("הקרא בלחיצה על אריח")
                    Switch(
                        checked = speakOnTilePress,
                        onCheckedChange = { viewModel.updateSpeakOnTilePress(it) }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("גיבוי ושחזור", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { exportLauncher.launch("myaac_backup_${System.currentTimeMillis()}.zip") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ייצוא מסד נתונים (Backup)")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ייבוא מסד נתונים (Restore)")
                }
            }
        }
    }
}
