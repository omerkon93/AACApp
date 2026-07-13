package com.kon.myaacapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val onCreateProfile = remember(viewModel) {
        { name: String ->
            viewModel.createProfile(name)
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
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new_profile)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_profile_name))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}