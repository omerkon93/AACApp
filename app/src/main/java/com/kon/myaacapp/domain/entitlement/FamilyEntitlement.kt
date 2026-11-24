package com.kon.myaacapp.domain.entitlement

import com.kon.myaacapp.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ProfilePlanTier {
    FREE,
    FAMILY,
}

enum class SubscriptionBillingState {
    UNKNOWN,
    NONE,
    ACTIVE,
    CANCELED_PENDING_EXPIRY,
    PENDING_PURCHASE,
    EXPIRED,
}

enum class BillingFeedback {
    NONE,
    PURCHASE_PENDING,
    CHECKOUT_CANCELED,
    PAYMENT_DECLINED_INSUFFICIENT_FUNDS,
    USER_INELIGIBLE,
    PRODUCT_UNAVAILABLE,
    TEMPORARY_ERROR,
    BILLING_UNAVAILABLE,
    RESTORED_NO_ACTIVE_PURCHASE,
}

data class FamilyPlanOfferDetails(
    val productId: String,
    val basePlanId: String = "",
    val offerToken: String = "",
    val formattedPrice: String,
    val billingPeriodIso8601: String,
    val freeTrialPeriodIso8601: String? = null,
)

data class FamilyEntitlementState(
    val tier: ProfilePlanTier = ProfilePlanTier.FREE,
    val isBillingConfigured: Boolean = false,
    val isProfileCapEnforced: Boolean = false,
    val freeProfileLimit: Int = ProfileLimitPolicy.FREE_PROFILE_LIMIT,
    val subscriptionState: SubscriptionBillingState = SubscriptionBillingState.UNKNOWN,
    val offerDetails: FamilyPlanOfferDetails? = null,
    val isAutoRenewing: Boolean = false,
    val hasPendingPurchase: Boolean = false,
    val lastBillingFeedback: BillingFeedback = BillingFeedback.NONE,
    val isCachedOfflineEntitlement: Boolean = false,
    val isLoadingStoreOffer: Boolean = false,
) {
    val hasFamilyEntitlement: Boolean
        get() = tier == ProfilePlanTier.FAMILY
}

data class ProfileAvailabilityPartition(
    val availableProfiles: List<UserProfile> = emptyList(),
    val archivedProfiles: List<UserProfile> = emptyList(),
    val availableProfileIds: Set<String> = emptySet(),
    val selectedCustomProfileId: String? = null,
    val isFreeSlotLocked: Boolean = false,
    val canChooseFreeProfile: Boolean = false,
) {
    val archivedProfileIds: Set<String>
        get() = archivedProfiles.mapTo(HashSet()) { it.profileId }

    fun isAvailable(profileId: String): Boolean = profileId in availableProfileIds

    fun isArchived(profileId: String): Boolean = profileId in archivedProfileIds
}

object ProfileLimitPolicy {
    const val FREE_PROFILE_LIMIT = 2
    const val DEFAULT_PROFILE_ID = "default"

    fun canCreateProfile(
        savedProfileCount: Int,
        entitlement: FamilyEntitlementState,
    ): Boolean {
        if (!entitlement.isProfileCapEnforced) {
            return true
        }
        if (entitlement.hasFamilyEntitlement) {
            return true
        }
        return savedProfileCount < entitlement.freeProfileLimit
    }

    fun isFreeLimitReached(
        savedProfileCount: Int,
        entitlement: FamilyEntitlementState,
    ): Boolean {
        return entitlement.isProfileCapEnforced &&
            !entitlement.hasFamilyEntitlement &&
            savedProfileCount >= entitlement.freeProfileLimit
    }

    fun partitionProfiles(
        profiles: List<UserProfile>,
        activeProfileId: String?,
        selectedCustomProfileId: String?,
        isFreeSlotLocked: Boolean = false,
        entitlement: FamilyEntitlementState,
        defaultProfileId: String = DEFAULT_PROFILE_ID,
    ): ProfileAvailabilityPartition {
        if (profiles.isEmpty()) {
            return ProfileAvailabilityPartition()
        }

        if (!entitlement.isProfileCapEnforced || entitlement.hasFamilyEntitlement) {
            val allIds = profiles.mapTo(LinkedHashSet()) { it.profileId }
            val resolvedCustom =
                activeProfileId?.takeIf { it != defaultProfileId && it in allIds }
                    ?: selectedCustomProfileId?.takeIf { it != defaultProfileId && it in allIds }
                    ?: profiles.firstOrNull { it.profileId != defaultProfileId }?.profileId
            return ProfileAvailabilityPartition(
                availableProfiles = profiles,
                archivedProfiles = emptyList(),
                availableProfileIds = allIds,
                selectedCustomProfileId = resolvedCustom,
                isFreeSlotLocked = false,
                canChooseFreeProfile = false,
            )
        }

        val starterProfile = profiles.firstOrNull { it.profileId == defaultProfileId }
        val customProfiles = profiles.filter { it.profileId != defaultProfileId }
        val customIds = customProfiles.mapTo(HashSet()) { it.profileId }

        if (starterProfile != null) {
            val maxCustomCount = (entitlement.freeProfileLimit - 1).coerceAtLeast(0)

            if (isFreeSlotLocked) {
                val lockedCustomId = selectedCustomProfileId?.takeIf { it != defaultProfileId }
                val availableCustom =
                    if (maxCustomCount > 0 && lockedCustomId != null && lockedCustomId in customIds) {
                        listOf(customProfiles.first { it.profileId == lockedCustomId })
                    } else {
                        emptyList()
                    }
                val available = listOf(starterProfile) + availableCustom
                val availableIds = available.mapTo(LinkedHashSet()) { it.profileId }
                val archived = customProfiles.filterNot { it.profileId in availableIds }
                return ProfileAvailabilityPartition(
                    availableProfiles = available,
                    archivedProfiles = archived,
                    availableProfileIds = availableIds,
                    selectedCustomProfileId = lockedCustomId,
                    isFreeSlotLocked = true,
                    canChooseFreeProfile = false,
                )
            }

            if (maxCustomCount == 0 || customProfiles.isEmpty()) {
                return ProfileAvailabilityPartition(
                    availableProfiles = listOf(starterProfile),
                    archivedProfiles = customProfiles,
                    availableProfileIds = setOf(starterProfile.profileId),
                    selectedCustomProfileId = null,
                    isFreeSlotLocked = false,
                    canChooseFreeProfile = false,
                )
            }

            val primaryCustomId =
                activeProfileId?.takeIf { it != defaultProfileId && it in customIds }
                    ?: selectedCustomProfileId?.takeIf { it != defaultProfileId && it in customIds }
                    ?: customProfiles.first().profileId

            val chosenCustom = customProfiles.filter { it.profileId == primaryCustomId } +
                customProfiles.filterNot { it.profileId == primaryCustomId }
            val availableCustom = chosenCustom.take(maxCustomCount)
            val availableCustomIds = availableCustom.mapTo(HashSet()) { it.profileId }

            val available = listOf(starterProfile) + availableCustom
            val archived = customProfiles.filterNot { it.profileId in availableCustomIds }
            return ProfileAvailabilityPartition(
                availableProfiles = available,
                archivedProfiles = archived,
                availableProfileIds = available.mapTo(LinkedHashSet()) { it.profileId },
                selectedCustomProfileId = availableCustom.firstOrNull()?.profileId,
                isFreeSlotLocked = false,
                canChooseFreeProfile = archived.isNotEmpty(),
            )
        }

        // Defensive fallback if no starter profile is present.
        val limit = entitlement.freeProfileLimit.coerceAtLeast(1)
        val prioritized = buildList {
            if (isFreeSlotLocked) {
                selectedCustomProfileId?.let { lockedId ->
                    profiles.firstOrNull { it.profileId == lockedId }?.let(::add)
                }
            } else {
                activeProfileId?.let { activeId ->
                    profiles.firstOrNull { it.profileId == activeId }?.let(::add)
                }
                selectedCustomProfileId?.let { selectedId ->
                    profiles.firstOrNull { it.profileId == selectedId && it !in this }?.let(::add)
                }
                profiles.forEach { profile ->
                    if (profile !in this) {
                        add(profile)
                    }
                }
            }
        }
        val available = prioritized.take(limit)
        val availableIds = available.mapTo(LinkedHashSet()) { it.profileId }
        val archived = profiles.filterNot { it.profileId in availableIds }
        return ProfileAvailabilityPartition(
            availableProfiles = available,
            archivedProfiles = archived,
            availableProfileIds = availableIds,
            selectedCustomProfileId = if (isFreeSlotLocked) {
                selectedCustomProfileId
            } else {
                available.firstOrNull()?.profileId
            },
            isFreeSlotLocked = isFreeSlotLocked,
            canChooseFreeProfile = !isFreeSlotLocked && archived.isNotEmpty(),
        )
    }
}

sealed interface FamilyUpgradeOutcome {
    data object Activated : FamilyUpgradeOutcome

    data object LaunchedCheckout : FamilyUpgradeOutcome

    data object Pending : FamilyUpgradeOutcome

    data object UserCanceled : FamilyUpgradeOutcome

    data object BillingUnavailable : FamilyUpgradeOutcome

    data object ProductUnavailable : FamilyUpgradeOutcome

    data object NoPurchaseFound : FamilyUpgradeOutcome

    data class Failed(
        val reason: String? = null,
        val feedback: BillingFeedback = BillingFeedback.TEMPORARY_ERROR,
    ) : FamilyUpgradeOutcome
}

interface FamilyEntitlementProvider {
    val entitlementState: StateFlow<FamilyEntitlementState>

    fun currentEntitlement(): FamilyEntitlementState = entitlementState.value

    suspend fun refreshEntitlements() {}

    suspend fun requestFamilyUpgrade(): FamilyUpgradeOutcome

    suspend fun restoreFamilyPurchase(): FamilyUpgradeOutcome

    fun clearBillingFeedback() {}

    fun getSubscriptionManagementUri(): String =
        "https://play.google.com/store/account/subscriptions"
}

/**
 * Fallback entitlement provider used in unit/UI tests or builds without Play Billing.
 * Keeps [isBillingConfigured] and [isProfileCapEnforced] false so caregivers are not
 * locked behind an unavailable purchase flow.
 */
class StoreFamilyEntitlementProvider : FamilyEntitlementProvider {
    private val _entitlementState = MutableStateFlow(
        FamilyEntitlementState(
            tier = ProfilePlanTier.FREE,
            isBillingConfigured = false,
            isProfileCapEnforced = false,
            freeProfileLimit = ProfileLimitPolicy.FREE_PROFILE_LIMIT,
        ),
    )

    override val entitlementState: StateFlow<FamilyEntitlementState> =
        _entitlementState.asStateFlow()

    override suspend fun requestFamilyUpgrade(): FamilyUpgradeOutcome {
        return FamilyUpgradeOutcome.BillingUnavailable
    }

    override suspend fun restoreFamilyPurchase(): FamilyUpgradeOutcome {
        return FamilyUpgradeOutcome.BillingUnavailable
    }
}

/**
 * Deterministic entitlement provider for testing Free/Family rules, expiry/downgrade,
 * localized offer formatting, pending purchases, and billing-availability transitions
 * without requiring Play Store billing.
 */
class ConfigurableFamilyEntitlementProvider(
    initialState: FamilyEntitlementState = FamilyEntitlementState(
        tier = ProfilePlanTier.FREE,
        isBillingConfigured = true,
        isProfileCapEnforced = true,
        freeProfileLimit = ProfileLimitPolicy.FREE_PROFILE_LIMIT,
    ),
    private val onUpgradeRequested: (suspend () -> FamilyUpgradeOutcome)? = null,
    private val onRestoreRequested: (suspend () -> FamilyUpgradeOutcome)? = null,
    private val onRefreshRequested: (suspend () -> Unit)? = null,
) : FamilyEntitlementProvider {
    private val _entitlementState = MutableStateFlow(initialState)

    override val entitlementState: StateFlow<FamilyEntitlementState> =
        _entitlementState.asStateFlow()

    fun setFamilyEntitlementActive(active: Boolean) {
        _entitlementState.update { current ->
            current.copy(
                tier = if (active) ProfilePlanTier.FAMILY else ProfilePlanTier.FREE,
                subscriptionState = if (active) {
                    SubscriptionBillingState.ACTIVE
                } else if (current.tier == ProfilePlanTier.FAMILY) {
                    SubscriptionBillingState.EXPIRED
                } else {
                    SubscriptionBillingState.NONE
                },
                isAutoRenewing = active,
                hasPendingPurchase = false,
            )
        }
    }

    fun setProfileCapEnforced(enforced: Boolean) {
        _entitlementState.update { current ->
            current.copy(isProfileCapEnforced = enforced)
        }
    }

    fun setBillingConfigured(configured: Boolean) {
        _entitlementState.update { current ->
            current.copy(isBillingConfigured = configured)
        }
    }

    fun setOfferDetails(offerDetails: FamilyPlanOfferDetails?) {
        _entitlementState.update { current ->
            current.copy(offerDetails = offerDetails)
        }
    }

    fun updateState(transform: (FamilyEntitlementState) -> FamilyEntitlementState) {
        _entitlementState.update(transform)
    }

    override suspend fun refreshEntitlements() {
        onRefreshRequested?.invoke()
    }

    override suspend fun requestFamilyUpgrade(): FamilyUpgradeOutcome {
        val customHandler = onUpgradeRequested
        if (customHandler != null) {
            return customHandler()
        }
        if (!_entitlementState.value.isBillingConfigured) {
            return FamilyUpgradeOutcome.BillingUnavailable
        }
        setFamilyEntitlementActive(true)
        return FamilyUpgradeOutcome.Activated
    }

    override suspend fun restoreFamilyPurchase(): FamilyUpgradeOutcome {
        val customHandler = onRestoreRequested
        if (customHandler != null) {
            return customHandler()
        }
        if (!_entitlementState.value.isBillingConfigured) {
            return FamilyUpgradeOutcome.BillingUnavailable
        }
        setFamilyEntitlementActive(true)
        return FamilyUpgradeOutcome.Activated
    }

    override fun clearBillingFeedback() {
        _entitlementState.update { current ->
            current.copy(lastBillingFeedback = BillingFeedback.NONE)
        }
    }
}
