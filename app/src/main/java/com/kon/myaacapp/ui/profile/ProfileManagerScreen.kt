package com.kon.myaacapp.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    viewModel: AACViewModel,
    onNavigateBack: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember {
        mutableStateOf<UserProfile?>(null)
    }
    // OPTIMIZATION: Hoist and memoize lambdas to guarantee stable function references.
    // This prevents Compose of churning memory and forces ProfileItem to skip recomposition.
    val onSwitchProfile = remember(viewModel) { { id: String -> viewModel.switchProfile(id) } }
    val onDeleteRequest: (UserProfile) -> Unit = remember {
        { profile ->
            profileToDelete = profile
        }
    }
    val onDismissCreate = remember { { showCreateDialog = false } }
    val onCreateProfile:
                (String, ProfileCreationMode) -> Unit =
        remember(viewModel) {
            { name, creationMode ->
                viewModel.createProfile(
                    name = name,
                    creationMode = creationMode,
                )

                showCreateDialog = false
            }
        }

    val onDismissDelete = remember { { profileToDelete = null } }
    val onConfirmDelete = remember(viewModel) {
        {
            profileToDelete?.let { viewModel.deleteProfile(it.profileId) }
            profileToDelete = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.profile_manager)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_new_profile)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // OPTIMIZATION: Provide a structural key to the list
            items(
                items = profiles,
                key = { it.profileId }
            ) { profile ->
                val isActive = profile.profileId == activeProfile?.profileId

                ProfileItem(
                    profile = profile,
                    isActive = isActive,
                    // We pass the stable reference directly
                    onSwitch = onSwitchProfile,
                    onDelete = onDeleteRequest
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = onDismissCreate,
            onCreate = onCreateProfile
        )
    }

    profileToDelete?.let { selectedProfile ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = {
                Text(
                    text = stringResource(
                        R.string.delete_profile
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_profile_confirm,
                        selectedProfile.profileName,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(
                            selectedProfile.profileId
                        )
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.delete)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDelete
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            },
        )
    }
}

// FIX: Modified signature to accept parameters in the lambdas.
// This allows the parent to pass stable function references.
@Composable
fun ProfileItem(
    profile: UserProfile,
    isActive: Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (UserProfile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    if (isActive) Icons.Default.Star else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile.profileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isActive) {
                        Text(
                            text = stringResource(R.string.active_profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row {
                if (!isActive) {
                    IconButton(onClick = { onSwitch(profile.profileId) }) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.switch_profile)
                        )
                    }
                    IconButton(onClick = { onDelete(profile) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_profile),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        creationMode: ProfileCreationMode,
    ) -> Unit,
) {
    var name by remember {
        mutableStateOf("")
    }

    var selectedMode by remember {
        mutableStateOf(
            ProfileCreationMode.DUPLICATE_CURRENT
        )
    }

    val normalizedName = name.trim()
    val canCreate = normalizedName.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.create_new_profile
                )
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.enter_profile_name
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { updatedName ->
                        name = updatedName
                    },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.profile_name
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(
                        R.string.profile_creation_mode
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_duplicate
                    ),
                    description = stringResource(
                        R.string.profile_mode_duplicate_description
                    ),
                    selected = selectedMode ==
                            ProfileCreationMode.DUPLICATE_CURRENT,
                    onClick = {
                        selectedMode =
                            ProfileCreationMode.DUPLICATE_CURRENT
                    },
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_default
                    ),
                    description = stringResource(
                        R.string.profile_mode_default_description
                    ),
                    selected = selectedMode ==
                            ProfileCreationMode.DEFAULT_TEMPLATE,
                    onClick = {
                        selectedMode =
                            ProfileCreationMode.DEFAULT_TEMPLATE
                    },
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_blank
                    ),
                    description = stringResource(
                        R.string.profile_mode_blank_description
                    ),
                    selected = selectedMode ==
                            ProfileCreationMode.BLANK,
                    onClick = {
                        selectedMode =
                            ProfileCreationMode.BLANK
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        normalizedName,
                        selectedMode,
                    )
                },
                enabled = canCreate,
            ) {
                Text(
                    text = stringResource(R.string.add)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = stringResource(R.string.cancel)
                )
            }
        },
    )
}

@Composable
private fun ProfileCreationModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) {
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                )
            }
        }
    }
}