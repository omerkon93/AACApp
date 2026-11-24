package com.kon.myaacapp.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.domain.entitlement.BillingFeedback
import com.kon.myaacapp.domain.entitlement.FamilyPlanOfferDetails
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    state: ProfileManagerState,
    onAction: (ProfileManagerAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val isCreateBlocked = !state.canCreateAnotherProfile
    val fabMuted = state.isBusy || isCreateBlocked
    var leaveAfterChoice by remember { mutableStateOf(false) }
    val handleBack: () -> Unit = {
        if (state.draftFreeProfileId == null) {
            onNavigateBack()
        } else if (!state.isBusy) {
            leaveAfterChoice = true
            onAction(ProfileManagerAction.OpenConfirmFreeProfileDialog)
        }
    }

    BackHandler(enabled = state.draftFreeProfileId != null, onBack = handleBack)
    LaunchedEffect(state.isFreeSlotLocked, leaveAfterChoice) {
        if (state.isFreeSlotLocked && leaveAfterChoice) {
            leaveAfterChoice = false
            onNavigateBack()
        }
    }

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
                        onClick = handleBack,
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
                    if (fabMuted) {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    } else {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    },
                contentColor =
                    if (fabMuted) {
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
            item(key = "profile_plan_summary") {
                ProfilePlanSummaryCard(
                    state = state,
                    onUpgradeClick = {
                        onAction(
                            ProfileManagerAction.UpgradeToFamilyClicked
                        )
                    },
                    onRestoreClick = {
                        onAction(
                            ProfileManagerAction.RestoreFamilyPurchaseClicked
                        )
                    },
                    onManageSubscriptionClick = {
                        onAction(
                            ProfileManagerAction.ManageSubscriptionClicked
                        )
                    },
                )
            }

            if (state.isProfileLimitEnforced && !state.hasFamilyEntitlement) {
                val pendingCustom = state.pendingFreeCustomProfile
                if (state.canChooseFreeProfile && pendingCustom != null) {
                    val starterProfileName =
                        state.profiles
                            .firstOrNull { it.profileId == "default" }
                            ?.profileName
                    item(key = "free_profile_choice_card") {
                        val starterName =
                            starterProfileName ?: stringResource(R.string.default_profile_name)
                        FreeProfileChoiceCard(
                            starterName = starterName,
                            pendingCustomName = pendingCustom.profileName,
                            archivedCount = state.archivedProfileCount,
                            isBusy = state.isBusy,
                            onConfirmClick = {
                                onAction(ProfileManagerAction.OpenConfirmFreeProfileDialog)
                            },
                        )
                    }
                }

                item(key = "available_profiles_header") {
                    Text(
                        text = stringResource(
                            R.string.profile_section_available,
                            state.availableProfileCount,
                            state.freeProfileLimit,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }

                items(
                    items = state.availableProfiles,
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
                        isArchived = false,
                        canMakeAvailable = false,
                        showPlanBadges = true,
                        isBusy = state.isBusy,
                        onSwitch = { profileId ->
                            onAction(
                                ProfileManagerAction
                                    .SwitchProfileClicked(
                                        profileId = profileId,
                                    )
                            )
                        },
                        onMakeAvailable = {},
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

                if (state.hasArchivedProfiles) {
                    item(key = "archived_profiles_header") {
                        Column(
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.profile_section_archived,
                                    state.archivedProfileCount,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(
                                    if (state.canChooseFreeProfile) {
                                        R.string.profile_section_archived_hint
                                    } else {
                                        R.string.profile_section_archived_locked_hint
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(
                        items = state.archivedProfiles,
                        key = { profile ->
                            "archived_${profile.profileId}"
                        },
                    ) { profile ->
                        ProfileItem(
                            profile = profile,
                            isActive = false,
                            isArchived = true,
                            canMakeAvailable = state.canChooseFreeProfile,
                            showPlanBadges = true,
                            isBusy = state.isBusy,
                            onSwitch = {},
                            onMakeAvailable = { profileId ->
                                onAction(
                                    ProfileManagerAction
                                        .SelectFreeCustomProfileClicked(
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
            } else {
                val displayProfiles = state.availableProfiles.ifEmpty { state.profiles }
                items(
                    items = displayProfiles,
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
                        isArchived = false,
                        showPlanBadges = false,
                        isBusy = state.isBusy,
                        onSwitch = { profileId ->
                            onAction(
                                ProfileManagerAction
                                    .SwitchProfileClicked(
                                        profileId = profileId,
                                    )
                            )
                        },
                        onMakeAvailable = {},
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
    }

    if (state.showCreateDialog) {
        val activeBoardLang = LocaleHelper.normalize(
            state.activeProfile?.activeLanguageCode?.ifBlank { "he" } ?: "he",
        )
        CreateProfileDialog(
            profileName = state.profileName,
            creationMode = state.creationMode,
            boardLanguageCode = state.boardLanguageCode,
            sourceBoardLanguageCode = activeBoardLang,
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
            onBoardLanguageChange = { value ->
                onAction(
                    ProfileManagerAction
                        .BoardLanguageChanged(value)
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

    if (state.showLimitDialog) {
        ProfileLimitReachedDialog(
            state = state,
            onUpgrade = {
                onAction(ProfileManagerAction.UpgradeToFamilyClicked)
            },
            onRestore = {
                onAction(ProfileManagerAction.RestoreFamilyPurchaseClicked)
            },
            onDismiss = {
                onAction(ProfileManagerAction.DismissLimitDialog)
            },
        )
    }

    val dialogPendingCustom = state.pendingFreeCustomProfile
    if (state.showConfirmFreeProfileDialog && state.canChooseFreeProfile && dialogPendingCustom != null) {
        val starterName =
            state.profiles
                .firstOrNull { it.profileId == "default" }
                ?.profileName
                ?: stringResource(R.string.default_profile_name)
        ConfirmFreeCustomProfileDialog(
            starterName = starterName,
            chosenProfileName = dialogPendingCustom.profileName,
            isBusy = state.isBusy,
            onConfirm = {
                onAction(
                    ProfileManagerAction.ConfirmFreeCustomProfileClicked(
                        profileId = dialogPendingCustom.profileId,
                    ),
                )
            },
            onDismiss = {
                leaveAfterChoice = false
                onAction(ProfileManagerAction.DismissConfirmFreeProfileDialog)
            },
            onDiscard = if (leaveAfterChoice) {
                {
                    leaveAfterChoice = false
                    onAction(ProfileManagerAction.DiscardFreeProfileChoice)
                    onNavigateBack()
                }
            } else {
                null
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

@Composable
private fun ProfilePlanSummaryCard(
    state: ProfileManagerState,
    onUpgradeClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
) {
    val containerColor = when {
        state.hasFamilyEntitlement ->
            MaterialTheme.colorScheme.secondaryContainer

        state.isFreeLimitReached ->
            MaterialTheme.colorScheme.tertiaryContainer

        else ->
            MaterialTheme.colorScheme.surfaceVariant
    }
    val formattedOfferPrice = state.offerDetails?.let { formatOfferPriceAndPeriod(it) }
    val trialNote = state.offerDetails?.freeTrialPeriodIso8601?.let { formatBillingPeriod(it) }
    val feedbackText = resolveBillingFeedbackMessage(state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                state.hasFamilyEntitlement -> {
                    val isCanceledPendingExpiry =
                        state.subscriptionState == SubscriptionBillingState.CANCELED_PENDING_EXPIRY

                    Text(
                        text = stringResource(
                            if (isCanceledPendingExpiry) {
                                R.string.profile_plan_family_canceled_active_title
                            } else {
                                R.string.profile_plan_family_active
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_plan_family_summary,
                            state.savedProfileCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (isCanceledPendingExpiry) {
                        Text(
                            text = stringResource(
                                R.string.profile_plan_family_canceled_active_body,
                                state.freeProfileLimit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (formattedOfferPrice != null) {
                        Text(
                            text = stringResource(
                                R.string.profile_plan_price_summary,
                                formattedOfferPrice,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.isCachedOfflineEntitlement) {
                        Text(
                            text = stringResource(R.string.profile_plan_cached_offline_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isBillingConfigured) {
                        OutlinedButton(
                            onClick = onManageSubscriptionClick,
                            enabled = !state.isBusy,
                        ) {
                            Text(text = stringResource(R.string.profile_manage_subscription))
                        }
                    }
                }

                state.isProfileLimitEnforced -> {
                    val countLabel = if (state.savedProfileCount > state.freeProfileLimit) {
                        stringResource(
                            R.string.profile_plan_free_over_limit_count,
                            state.savedProfileCount,
                            state.availableProfileCount,
                            state.archivedProfileCount,
                        )
                    } else {
                        stringResource(
                            R.string.profile_plan_free_count,
                            state.savedProfileCount,
                            state.freeProfileLimit,
                        )
                    }
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    if (state.isFreeLimitReached) {
                        Text(
                            text = stringResource(R.string.profile_plan_limit_reached_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_plan_limit_reached_body,
                                state.freeProfileLimit,
                                state.savedProfileCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.profile_plan_free_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (state.subscriptionState == SubscriptionBillingState.EXPIRED) {
                        Text(
                            text = stringResource(
                                R.string.profile_plan_family_expired_notice,
                                state.freeProfileLimit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.profile_plan_family_downgrade_disclosure,
                            state.freeProfileLimit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (formattedOfferPrice != null) {
                        Text(
                            text = stringResource(
                                R.string.profile_plan_price_summary,
                                formattedOfferPrice,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        if (trialNote != null) {
                            Text(
                                text = stringResource(
                                    R.string.profile_plan_free_trial_note,
                                    trialNote,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (state.isLoadingStoreOffer) {
                        Text(
                            text = stringResource(R.string.profile_plan_loading_offer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onUpgradeClick,
                            enabled = !state.isBusy,
                        ) {
                            Text(
                                text = if (formattedOfferPrice != null) {
                                    stringResource(
                                        R.string.profile_upgrade_to_family_with_price,
                                        formattedOfferPrice,
                                    )
                                } else {
                                    stringResource(R.string.profile_upgrade_to_family)
                                },
                            )
                        }

                        OutlinedButton(
                            onClick = onRestoreClick,
                            enabled = !state.isBusy,
                        ) {
                            Text(text = stringResource(R.string.profile_restore_family_purchase))
                        }
                    }

                    if (feedbackText != null && !state.showLimitDialog) {
                        Text(
                            text = feedbackText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(
                            R.string.profile_plan_saved_count,
                            state.savedProfileCount,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.profile_plan_free_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileLimitReachedDialog(
    state: ProfileManagerState,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formattedOfferPrice = state.offerDetails?.let { formatOfferPriceAndPeriod(it) }
    val trialNote = state.offerDetails?.freeTrialPeriodIso8601?.let { formatBillingPeriod(it) }
    val feedbackText = resolveBillingFeedbackMessage(state)

    AlertDialog(
        onDismissRequest = {
            if (!state.isUpdatingEntitlement) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.profile_plan_limit_reached_title
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.profile_plan_limit_reached_body,
                        state.freeProfileLimit,
                        state.savedProfileCount,
                    ),
                )
                if (state.subscriptionState == SubscriptionBillingState.EXPIRED) {
                    Text(
                        text = stringResource(
                            R.string.profile_plan_family_expired_notice,
                            state.freeProfileLimit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.profile_plan_family_downgrade_disclosure,
                        state.freeProfileLimit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (formattedOfferPrice != null) {
                    Text(
                        text = stringResource(
                            R.string.profile_plan_price_summary,
                            formattedOfferPrice,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (trialNote != null) {
                        Text(
                            text = stringResource(
                                R.string.profile_plan_free_trial_note,
                                trialNote,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (state.isLoadingStoreOffer) {
                    Text(
                        text = stringResource(R.string.profile_plan_loading_offer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (feedbackText != null) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                enabled = !state.isUpdatingEntitlement,
            ) {
                Text(
                    text = if (formattedOfferPrice != null) {
                        stringResource(
                            R.string.profile_upgrade_to_family_with_price,
                            formattedOfferPrice,
                        )
                    } else {
                        stringResource(R.string.profile_upgrade_to_family)
                    },
                )
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = !state.isUpdatingEntitlement,
                ) {
                    Text(
                        text = stringResource(
                            R.string.profile_restore_family_purchase
                        ),
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isUpdatingEntitlement,
                ) {
                    Text(
                        text = stringResource(
                            R.string.close
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun formatOfferPriceAndPeriod(
    offer: FamilyPlanOfferDetails,
): String {
    val periodLabel = formatBillingPeriod(offer.billingPeriodIso8601)
    return if (periodLabel != null) {
        stringResource(
            R.string.profile_plan_price_period,
            offer.formattedPrice,
            periodLabel,
        )
    } else {
        offer.formattedPrice
    }
}

@Composable
private fun formatBillingPeriod(isoPeriod: String): String? {
    val trimmed = isoPeriod.trim().uppercase()
    if (!trimmed.startsWith("P") || trimmed.length < 3) {
        return null
    }
    val unit = trimmed.last()
    val count = trimmed.substring(1, trimmed.length - 1).toIntOrNull() ?: return null
    if (count <= 0) return null

    return when (unit) {
        'D' -> if (count == 1) {
            stringResource(R.string.profile_period_day)
        } else {
            stringResource(R.string.profile_period_days, count)
        }

        'W' -> if (count == 1) {
            stringResource(R.string.profile_period_week)
        } else {
            stringResource(R.string.profile_period_weeks, count)
        }

        'M' -> if (count == 1) {
            stringResource(R.string.profile_period_month)
        } else {
            stringResource(R.string.profile_period_months, count)
        }

        'Y' -> if (count == 1) {
            stringResource(R.string.profile_period_year)
        } else {
            stringResource(R.string.profile_period_years, count)
        }

        else -> null
    }
}

@Composable
private fun resolveBillingFeedbackMessage(state: ProfileManagerState): String? {
    if (state.hasPendingPurchase || state.lastBillingFeedback == BillingFeedback.PURCHASE_PENDING) {
        return stringResource(R.string.profile_billing_feedback_pending)
    }
    return when (state.lastBillingFeedback) {
        BillingFeedback.NONE -> {
            if (state.showBillingUnavailableNotice) {
                if (state.isBillingConfigured) {
                    stringResource(R.string.profile_billing_play_unavailable_message)
                } else {
                    stringResource(R.string.profile_billing_unavailable_message)
                }
            } else {
                null
            }
        }

        BillingFeedback.PURCHASE_PENDING ->
            stringResource(R.string.profile_billing_feedback_pending)

        BillingFeedback.CHECKOUT_CANCELED ->
            stringResource(R.string.profile_billing_feedback_canceled)

        BillingFeedback.PAYMENT_DECLINED_INSUFFICIENT_FUNDS ->
            stringResource(R.string.profile_billing_feedback_insufficient_funds)

        BillingFeedback.USER_INELIGIBLE ->
            stringResource(R.string.profile_billing_feedback_user_ineligible)

        BillingFeedback.PRODUCT_UNAVAILABLE ->
            stringResource(R.string.profile_billing_feedback_product_unavailable)

        BillingFeedback.TEMPORARY_ERROR ->
            stringResource(R.string.profile_billing_feedback_temporary_error)

        BillingFeedback.BILLING_UNAVAILABLE -> {
            if (state.isBillingConfigured) {
                stringResource(R.string.profile_billing_play_unavailable_message)
            } else {
                stringResource(R.string.profile_billing_unavailable_message)
            }
        }

        BillingFeedback.RESTORED_NO_ACTIVE_PURCHASE ->
            stringResource(R.string.profile_billing_feedback_no_purchase_found)
    }
}

@Composable
private fun FreeProfileChoiceCard(
    starterName: String,
    pendingCustomName: String,
    archivedCount: Int,
    isBusy: Boolean,
    onConfirmClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_free_choice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.profile_free_choice_body,
                    starterName,
                    pendingCustomName,
                    archivedCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onConfirmClick,
                enabled = !isBusy,
            ) {
                Text(
                    text = stringResource(
                        R.string.profile_free_choice_confirm_button,
                        pendingCustomName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ConfirmFreeCustomProfileDialog(
    starterName: String,
    chosenProfileName: String,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.profile_free_choice_dialog_title),
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.profile_free_choice_dialog_body,
                    starterName,
                    chosenProfileName,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
            ) {
                Text(
                    text = stringResource(R.string.profile_free_choice_confirm_action),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard ?: onDismiss,
                enabled = !isBusy,
            ) {
                Text(
                    text = stringResource(
                        if (onDiscard != null) R.string.profile_free_choice_discard else R.string.cancel,
                    ),
                )
            }
        },
    )
}

// FIX: Modified signature to accept parameters in the lambdas.
// This allows the parent to pass stable function references.
@Composable
fun ProfileItem(
    profile: UserProfile,
    isActive: Boolean,
    isArchived: Boolean = false,
    canMakeAvailable: Boolean = false,
    showPlanBadges: Boolean = false,
    isBusy: Boolean = false,
    onSwitch: (String) -> Unit,
    onMakeAvailable: (String) -> Unit = {},
    onDelete: (UserProfile) -> Unit,
) {
    val containerColors = when {
        isActive ->
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)

        isArchived ->
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

        else ->
            CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = containerColors,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = when {
                        isActive -> Icons.Default.Star
                        isArchived -> Icons.Default.Inventory2
                        else -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile.profileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                    val boardLanguageLabel =
                        if (LocaleHelper.normalize(profile.activeLanguageCode) == "en") {
                            stringResource(R.string.english)
                        } else {
                            stringResource(R.string.hebrew)
                        }
                    Text(
                        text = boardLanguageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isActive) {
                        Text(
                            text = stringResource(R.string.active_profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (showPlanBadges) {
                        val statusLabel = when {
                            isArchived && !canMakeAvailable ->
                                stringResource(R.string.profile_status_available_with_family)
                            isArchived ->
                                stringResource(R.string.profile_status_archived)
                            profile.profileId == "default" ->
                                stringResource(R.string.profile_status_starter)
                            else ->
                                stringResource(R.string.profile_status_available)
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("profile_status_${profile.profileId}"),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isArchived) {
                    if (canMakeAvailable) {
                        OutlinedButton(
                            onClick = { onMakeAvailable(profile.profileId) },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("make_available_${profile.profileId}"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(text = stringResource(R.string.profile_make_available))
                        }
                    }
                    if (profile.profileId != "default") {
                        IconButton(
                            onClick = { onDelete(profile) },
                            enabled = !isBusy,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_profile),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else if (!isActive) {
                    IconButton(
                        onClick = { onSwitch(profile.profileId) },
                        enabled = !isBusy,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.switch_profile),
                        )
                    }
                    if (profile.profileId != "default") {
                        IconButton(
                            onClick = { onDelete(profile) },
                            enabled = !isBusy,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_profile),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
    boardLanguageCode: String,
    sourceBoardLanguageCode: String,
    canCreate: Boolean,
    isCreating: Boolean,
    onProfileNameChange: (String) -> Unit,
    onCreationModeChange:
        (ProfileCreationMode) -> Unit,
    onBoardLanguageChange: (String) -> Unit,
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
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

                Text(
                    text = stringResource(
                        R.string.profile_board_language
                    ),
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (creationMode == ProfileCreationMode.DUPLICATE_CURRENT) {
                    val sourceLanguageLabel = if (sourceBoardLanguageCode == "en") {
                        stringResource(R.string.english)
                    } else {
                        stringResource(R.string.hebrew)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.profile_duplicate_keeps_language,
                                sourceLanguageLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            R.string.profile_board_language_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BoardLanguageOptionCard(
                            label = stringResource(R.string.hebrew),
                            selected = boardLanguageCode == "he",
                            enabled = !isCreating,
                            onClick = { onBoardLanguageChange("he") },
                            modifier = Modifier.weight(1f),
                        )
                        BoardLanguageOptionCard(
                            label = stringResource(R.string.english),
                            selected = boardLanguageCode == "en",
                            enabled = !isCreating,
                            onClick = { onBoardLanguageChange("en") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
private fun BoardLanguageOptionCard(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
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
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = if (enabled) onClick else null,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
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
