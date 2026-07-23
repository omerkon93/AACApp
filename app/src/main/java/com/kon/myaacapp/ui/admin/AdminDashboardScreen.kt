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
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.service.audio.AudioPreviewManager
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.ui.admin.components.TileActionDialog
import com.kon.myaacapp.ui.admin.components.TilePickerDialog
import com.kon.myaacapp.ui.admin.grid.AdminEditableGridScreen
import com.kon.myaacapp.ui.admin.grid.AdminGridRoute
import com.kon.myaacapp.ui.admin.grid.AdminGridViewModelFactory
import com.kon.myaacapp.ui.admin.layout.AdminLayoutSettingsScreen
import com.kon.myaacapp.ui.admin.layout.LayoutSettingsRoute
import com.kon.myaacapp.ui.admin.layout.LayoutSettingsViewModelFactory
import com.kon.myaacapp.ui.admin.list.AdminListRoute
import com.kon.myaacapp.ui.admin.list.AdminListView
import com.kon.myaacapp.ui.admin.list.AdminListViewModelFactory
import com.kon.myaacapp.ui.admin.navigation.AdminBottomNavigation
import com.kon.myaacapp.ui.admin.statistics.AdminStatisticsRoute
import com.kon.myaacapp.ui.admin.statistics.AdminStatisticsScreen
import com.kon.myaacapp.ui.admin.statistics.AdminStatisticsViewModelFactory
import com.kon.myaacapp.ui.admin.system.AdminSystemSettings
import com.kon.myaacapp.ui.admin.system.SystemSettingsRoute
import com.kon.myaacapp.ui.admin.system.SystemSettingsViewModelFactory
import com.kon.myaacapp.ui.editor.TileEditDialogContent
import com.kon.myaacapp.ui.editor.TileEditorRoute
import com.kon.myaacapp.ui.editor.TileEditorViewModelFactory


enum class AdminTab {
    HOME,
    SETTINGS,
    LAYOUT,
    STATISTICS,
    SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    state: AdminDashboardState,
    onAction: (AdminDashboardAction) -> Unit,
    tileEditorViewModelFactory: TileEditorViewModelFactory,
    adminGridViewModelFactory: AdminGridViewModelFactory,
    layoutSettingsViewModelFactory: LayoutSettingsViewModelFactory,
    adminListViewModelFactory: AdminListViewModelFactory,
    adminStatisticsViewModelFactory: AdminStatisticsViewModelFactory,
    systemSettingsViewModelFactory: SystemSettingsViewModelFactory,
    audioRecordingService: AudioRecordingService,
    audioPreviewManager: AudioPreviewManager,
    onCommunicationReset: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToProfiles: () -> Unit,
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(AdminTab.HOME)
    }

    /*
     * Tile editor and permanent-delete state.
     *
     * Both the grid and list now use the domain CombinedTile model.
     * A null editingTile means that a new tile is being created.
     */
    var showTileDialog by remember {
        mutableStateOf(false)
    }

    var editorSessionKey by rememberSaveable {
        mutableStateOf(0)
    }

    var editingTile by remember {
        mutableStateOf<CombinedTile?>(null)
    }

    var tileToDelete by remember {
        mutableStateOf<CombinedTile?>(null)
    }

    /*
     * New grid/action state.
     *
     * AdminEditableGridScreen returns CombinedTile directly.
     */
    var tileForAction by remember {
        mutableStateOf<CombinedTile?>(null)
    }

    var initialCellIndex by remember {
        mutableStateOf<Int?>(null)
    }

    var showTilePicker by remember {
        mutableStateOf(false)
    }

    val langCode = state.languageCode

    val currentParentId =
        state.currentParentId

    val allCategories =
        state.allCategories

    val gridColumns =
        state.gridColumns

    val gridRows =
        state.gridRows

    val gridTileScale =
        state.gridTileScale

    val gridTileContainerScale =
        state.gridTileContainerScale

    val layoutDir = if (langCode == "he") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val zeroInsets = remember {
        WindowInsets(0.dp)
    }

    val onEditTileGrid: (CombinedTile) -> Unit = remember {
        { tile ->
            tileForAction = tile
        }
    }

    val onCreateTileGrid: (Int) -> Unit = remember {
        { cellIndex ->
            editingTile = null
            initialCellIndex = cellIndex
            showTilePicker = true
        }
    }

    val onEditTileList: (CombinedTile?) -> Unit = remember {
        { tile ->
            editingTile = tile
            initialCellIndex =
                tile?.layoutState?.cellIndex

            editorSessionKey += 1
            showTileDialog = true
        }
    }

    val onDeleteTileList: (CombinedTile) -> Unit = remember {
        { tile ->
            tileToDelete = tile
        }
    }

    val onResetHome: () -> Unit =
        remember(onAction) {
            {
                onAction(
                    AdminDashboardAction.ResetToHome
                )
            }
        }

    val onNavBack: () -> Unit =
        remember(onAction) {
            {
                onAction(
                    AdminDashboardAction.NavigateUp
                )
            }
        }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDir
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                AdminTab.HOME -> {
                                    if (currentParentId == null) {
                                        stringResource(R.string.edit_main_screen)
                                    } else {
                                        stringResource(R.string.edit_category)
                                    }
                                }

                                AdminTab.SETTINGS -> {
                                    stringResource(R.string.admin_tab_settings)
                                }

                                AdminTab.LAYOUT -> {
                                    "תצוגה"
                                }

                                AdminTab.STATISTICS -> {
                                    stringResource(
                                        R.string.admin_tab_statistics
                                    )
                                }

                                AdminTab.SYSTEM -> {
                                    stringResource(R.string.admin_tab_system)
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(
                                    R.string.back
                                )
                            )
                        }
                    },
                    actions = {
                        if (
                            selectedTab == AdminTab.HOME &&
                            currentParentId != null
                        ) {
                            IconButton(
                                onClick = onResetHome
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = stringResource(
                                        R.string.home
                                    )
                                )
                            }

                            IconButton(
                                onClick = onNavBack
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(
                                        R.string.up
                                    )
                                )
                            }
                        }
                    },
                    windowInsets = zeroInsets
                )
            },
            bottomBar = {
                AdminBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding)
            ) {
                when (selectedTab) {
                    AdminTab.HOME -> {
                        AdminGridRoute(
                            currentParentId = currentParentId,
                            languageCode = langCode,
                            gridColumns = gridColumns,
                            gridRows = gridRows,
                            gridTileScale = gridTileScale,
                            gridTileContainerScale =
                                gridTileContainerScale,
                            viewModelFactory =
                                adminGridViewModelFactory,
                            onEditTile = onEditTileGrid,
                            onCreateTile = onCreateTileGrid,
                        ) { state, onAction ->
                            AdminEditableGridScreen(
                                state = state,
                                onAction = onAction,
                            )
                        }
                    }

                    AdminTab.SETTINGS -> {
                        AdminListRoute(
                            languageCode = langCode,
                            audioService = audioRecordingService,
                            viewModelFactory =
                                adminListViewModelFactory,
                            onCreateTile = {
                                onEditTileList(null)
                            },
                            onEditTile = { tile ->
                                onEditTileList(tile)
                            },
                            onDeleteTile = { tile ->
                                onDeleteTileList(tile)
                            },
                            onPlayPreview =
                                audioPreviewManager::playPreview,
                        ) { state, onAction ->
                            AdminListView(
                                state = state,
                                onAction = onAction,
                            )
                        }
                    }

                    AdminTab.LAYOUT -> {
                        LayoutSettingsRoute(
                            viewModelFactory =
                                layoutSettingsViewModelFactory,
                        ) { state, onAction ->
                            AdminLayoutSettingsScreen(
                                state = state,
                                onAction = onAction,
                            )
                        }
                    }

                    AdminTab.STATISTICS -> {
                        AdminStatisticsRoute(
                            languageCode = langCode,
                            viewModelFactory =
                                adminStatisticsViewModelFactory,
                        ) { state, onAction ->
                            AdminStatisticsScreen(
                                state = state,
                                onAction = onAction,
                            )
                        }
                    }

                    AdminTab.SYSTEM -> {
                        SystemSettingsRoute(
                            viewModelFactory =
                                systemSettingsViewModelFactory,
                            onNavigateToProfiles =
                                onNavigateToProfiles,
                            onSystemDataChanged =
                                onCommunicationReset,
                        ) { state, onAction ->
                            AdminSystemSettings(
                                state = state,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTilePicker) {
        TilePickerDialog(
            allTiles = state.allTiles,
            onDismiss = {
                showTilePicker = false
                initialCellIndex = null
            },
            onTileSelected = { tile ->
                showTilePicker = false

                if (tile == null) {
                    editingTile = null
                    editorSessionKey += 1
                    showTileDialog = true
                } else {
                    onAction(
                        AdminDashboardAction.AttachTileToCategory(
                            tileId = tile.id,
                            cellIndex = initialCellIndex,
                        )
                    )

                    initialCellIndex = null
                }
            }
        )
    }

    tileToDelete?.let { deleteTile ->
        AlertDialog(
            onDismissRequest = {
                tileToDelete = null
            },
            title = {
                Text(
                    text = stringResource(
                        R.string.delete_tile_title
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_tile_confirm_msg,
                        deleteTile.label
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(
                            AdminDashboardAction.DeleteTile(
                                tile = deleteTile,
                            )
                        )

                        tileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.delete)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        tileToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            }
        )
    }

    if (showTileDialog) {
        TileEditorRoute(
            editorSessionKey = editorSessionKey,
            existingTile = editingTile,
            initialCellIndex = initialCellIndex,
            currentParentId = currentParentId,
            languageCode = langCode,
            categories = allCategories,
            viewModelFactory =
                tileEditorViewModelFactory,
            onDismiss = {
                showTileDialog = false
                editingTile = null
                initialCellIndex = null
            },
        ) { state, onAction ->
            TileEditDialogContent(
                state = state,
                audioService = audioRecordingService,
                onPlayPreview =
                    audioPreviewManager::playPreview,
                onAction = onAction,
            )
        }
    }

    tileForAction?.let { actionTile ->
        val definition = actionTile.definition

        val onOpenAction: (() -> Unit)? = remember(actionTile) {
            if (definition.resolvedType == TileType.FOLDER) {
                {
                    onAction(
                        AdminDashboardAction.OpenCategory(
                            categoryId = definition.id,
                        )
                    )

                    tileForAction = null
                }
            } else {
                null
            }
        }

        TileActionDialog(
            tile = actionTile,
            onDismiss = {
                tileForAction = null
            },
            onEdit = {
                editingTile = actionTile
                initialCellIndex =
                    actionTile.layoutState.cellIndex

                tileForAction = null
                editorSessionKey += 1
                showTileDialog = true
            },
            onRemove = {
                onAction(
                    AdminDashboardAction.RemoveTileFromCategory(
                        tileId = definition.id,
                    )
                )

                tileForAction = null
            },
            onOpen = onOpenAction
        )
    }
}