package com.kon.myaacapp.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.ui.admin.components.TileActionDialog
import com.kon.myaacapp.ui.admin.components.TilePickerDialog
import com.kon.myaacapp.ui.admin.grid.AdminEditableGridScreen
import com.kon.myaacapp.ui.admin.list.AdminListView
import com.kon.myaacapp.ui.admin.layout.AdminLayoutSettingsScreen
import com.kon.myaacapp.ui.admin.navigation.AdminBottomNavigation
import com.kon.myaacapp.ui.admin.statistics.AdminStatisticsScreen
import com.kon.myaacapp.ui.admin.system.AdminSystemSettings
import com.kon.myaacapp.ui.editor.TileEditDialog

// 👉 1. Added LAYOUT to enum
enum class AdminTab {
    HOME, SETTINGS, LAYOUT, STATISTICS, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfiles: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.HOME) }
    var showTileDialog by remember { mutableStateOf(false) }
    var editingTile by remember { mutableStateOf<AACTile?>(null) }
    var tileToDelete by remember { mutableStateOf<AACTile?>(null) }
    var initialCellIndex by remember { mutableStateOf<Int?>(null) }
    var showTilePicker by remember { mutableStateOf(false) }
    var tileForAction by remember { mutableStateOf<AACTile?>(null) }

    val langCode by viewModel.languageCode.collectAsState()
    val currentParentId by viewModel.currentParentId.collectAsState()
    val layoutDir = if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

    val zeroInsets = remember { WindowInsets(0.dp) }

    val onEditTileGrid = remember { { tile: AACTile? -> tileForAction = tile } }
    val onCreateTileGrid = remember {
        { cellIndex: Int ->
            editingTile = null
            initialCellIndex = cellIndex
            showTilePicker = true
        }
    }

    val onEditTileList = remember {
        { tile: AACTile? ->
            editingTile = tile
            initialCellIndex = tile?.cellIndex
            showTileDialog = true
        }
    }
    val onDeleteTileList = remember { { tile: AACTile -> tileToDelete = tile } }

    val onResetHome = remember { { viewModel.resetToHome() } }
    val onNavBack = remember { { viewModel.navigateBack() } }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                AdminTab.HOME -> if (currentParentId == null) stringResource(R.string.edit_main_screen) else stringResource(R.string.edit_category)
                                AdminTab.SETTINGS -> stringResource(R.string.admin_tab_settings)
                                AdminTab.LAYOUT -> "תצוגה" // Replace with stringResource(R.string.admin_tab_layout) if you add it to strings.xml
                                AdminTab.STATISTICS -> stringResource(R.string.admin_tab_statistics)
                                AdminTab.SYSTEM -> stringResource(R.string.admin_tab_system)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (selectedTab == AdminTab.HOME && currentParentId != null) {
                            IconButton(onClick = onResetHome) {
                                Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home))
                            }
                            IconButton(onClick = onNavBack) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.up))
                            }
                        }
                    },
                    windowInsets = zeroInsets
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
                            onEditTile = onEditTileGrid,
                            onCreateTile = onCreateTileGrid
                        )
                    }

                    AdminTab.SETTINGS -> {
                        AdminListView(
                            viewModel = viewModel,
                            onEditTile = onEditTileList,
                            onDeleteTile = onDeleteTileList
                        )
                    }

                    // 👉 2. Load layout settings tab
                    AdminTab.LAYOUT -> {
                        AdminLayoutSettingsScreen(viewModel = viewModel)
                    }

                    AdminTab.STATISTICS -> {
                        AdminStatisticsScreen(viewModel = viewModel)
                    }

                    AdminTab.SYSTEM -> {
                        AdminSystemSettings(
                            viewModel = viewModel,
                            onNavigateToProfiles = onNavigateToProfiles
                        )
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
                    showTileDialog = true
                } else {
                    viewModel.attachTileToCategory(tile.id, currentParentId, initialCellIndex)
                    initialCellIndex = null
                }
            },
        )
    }

    if (tileToDelete != null) {
        val dismissDelete = remember { { tileToDelete = null } }
        AlertDialog(
            onDismissRequest = dismissDelete,
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
                TextButton(onClick = dismissDelete) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTileDialog) {
        TileEditDialog(
            viewModel = viewModel,
            existingTile = editingTile,
            initialCellIndex = initialCellIndex,
            onDismiss = {
                showTileDialog = false
                editingTile = null
                initialCellIndex = null
            },
        )
    }

    if (tileForAction != null) {
        val onOpenAction: (() -> Unit)? = remember(tileForAction) {
            if (tileForAction?.isCategory == true) {
                {
                    viewModel.setCategory(tileForAction?.id)
                    tileForAction = null
                }
            } else null
        }

        TileActionDialog(
            tile = tileForAction!!,
            onDismiss = remember { { tileForAction = null } },
            onEdit = remember {
                {
                    editingTile = tileForAction
                    initialCellIndex = tileForAction?.cellIndex
                    tileForAction = null
                    showTileDialog = true
                }
            },
            onRemove = {
                tileForAction?.id?.let { id ->
                    viewModel.removeTileFromCategory(id, currentParentId)
                }
                tileForAction = null
            },
            onOpen = onOpenAction
        )
    }
}