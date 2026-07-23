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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    state: ProfileManagerState,
    onAction: (ProfileManagerAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            R.string.profile_manager
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled
                                    .ArrowBack,
                            contentDescription =
                                stringResource(
                                    R.string.back
                                ),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!state.isBusy) {
                        onAction(
                            ProfileManagerAction
                                .OpenCreateDialog
                        )
                    }
                },
                containerColor =
                    if (state.isBusy) {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    } else {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    },
                contentColor =
                    if (state.isBusy) {
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(
                        R.string.create_new_profile
                    ),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.profiles,
                key = { profile ->
                    profile.profileId
                },
            ) { profile ->
                val isActive =
                    profile.profileId ==
                            state.activeProfile?.profileId

                ProfileItem(
                    profile = profile,
                    isActive = isActive,
                    onSwitch = { profileId ->
                        onAction(
                            ProfileManagerAction
                                .SwitchProfileClicked(
                                    profileId = profileId,
                                )
                        )
                    },
                    onDelete = { selectedProfile ->
                        onAction(
                            ProfileManagerAction
                                .DeleteProfileClicked(
                                    profile =
                                        selectedProfile,
                                )
                        )
                    },
                )
            }
        }
    }

    if (state.showCreateDialog) {
        CreateProfileDialog(
            profileName = state.profileName,
            creationMode = state.creationMode,
            canCreate = state.canCreateProfile,
            isCreating = state.isCreatingProfile,
            onProfileNameChange = { value ->
                onAction(
                    ProfileManagerAction
                        .ProfileNameChanged(value)
                )
            },
            onCreationModeChange = { value ->
                onAction(
                    ProfileManagerAction
                        .CreationModeChanged(value)
                )
            },
            onDismiss = {
                onAction(
                    ProfileManagerAction
                        .CloseCreateDialog
                )
            },
            onCreate = {
                onAction(
                    ProfileManagerAction
                        .CreateProfileClicked
                )
            },
        )
    }

    state.profilePendingDeletion?.let {
            selectedProfile ->

        AlertDialog(
            onDismissRequest = {
                if (!state.isDeletingProfile) {
                    onAction(
                        ProfileManagerAction
                            .CancelDeleteProfile
                    )
                }
            },
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
                        onAction(
                            ProfileManagerAction
                                .ConfirmDeleteProfile
                        )
                    },
                    enabled =
                        state.canDeletePendingProfile,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                        ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.delete
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(
                            ProfileManagerAction
                                .CancelDeleteProfile
                        )
                    },
                    enabled =
                        !state.isDeletingProfile,
                ) {
                    Text(
                        text = stringResource(
                            R.string.cancel
                        )
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
    profileName: String,
    creationMode: ProfileCreationMode,
    canCreate: Boolean,
    isCreating: Boolean,
    onProfileNameChange: (String) -> Unit,
    onCreationModeChange:
        (ProfileCreationMode) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isCreating) {
                onDismiss()
            }
        },
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
                    value = profileName,
                    onValueChange =
                        onProfileNameChange,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.profile_name
                            )
                        )
                    },
                    enabled = !isCreating,
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(
                        R.string.profile_creation_mode
                    ),
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_duplicate
                    ),
                    description = stringResource(
                        R.string
                            .profile_mode_duplicate_description
                    ),
                    selected =
                        creationMode ==
                                ProfileCreationMode
                                    .DUPLICATE_CURRENT,
                    onClick = {
                        if (!isCreating) {
                            onCreationModeChange(
                                ProfileCreationMode
                                    .DUPLICATE_CURRENT
                            )
                        }
                    },
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_default
                    ),
                    description = stringResource(
                        R.string
                            .profile_mode_default_description
                    ),
                    selected =
                        creationMode ==
                                ProfileCreationMode
                                    .DEFAULT_TEMPLATE,
                    onClick = {
                        if (!isCreating) {
                            onCreationModeChange(
                                ProfileCreationMode
                                    .DEFAULT_TEMPLATE
                            )
                        }
                    },
                )

                ProfileCreationModeOption(
                    title = stringResource(
                        R.string.profile_mode_blank
                    ),
                    description = stringResource(
                        R.string
                            .profile_mode_blank_description
                    ),
                    selected =
                        creationMode ==
                                ProfileCreationMode.BLANK,
                    onClick = {
                        if (!isCreating) {
                            onCreationModeChange(
                                ProfileCreationMode.BLANK
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled =
                    canCreate && !isCreating,
            ) {
                Text(
                    text = stringResource(
                        R.string.add
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text(
                    text = stringResource(
                        R.string.cancel
                    )
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