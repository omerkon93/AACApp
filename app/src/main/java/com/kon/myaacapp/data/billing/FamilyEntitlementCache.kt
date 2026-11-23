package com.kon.myaacapp.data.billing

import android.content.Context
import android.content.SharedPreferences
import com.kon.myaacapp.domain.entitlement.ProfilePlanTier
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState

data class CachedFamilyEntitlement(
    val tier: ProfilePlanTier = ProfilePlanTier.FREE,
    val subscriptionState: SubscriptionBillingState = SubscriptionBillingState.UNKNOWN,
    val isAutoRenewing: Boolean = false,
    val productId: String? = null,
    val lastVerifiedEpochMs: Long = 0L,
)

interface FamilyEntitlementCache {
    fun read(): CachedFamilyEntitlement

    fun writeVerified(record: CachedFamilyEntitlement)
}

/**
 * Stores the last Play-verified Family entitlement strictly in local SharedPreferences
 * (outside `files/` so manual board backups never transfer store entitlements across devices).
 */
class SharedPrefsFamilyEntitlementCache(
    context: Context,
) : FamilyEntitlementCache {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): CachedFamilyEntitlement {
        val tierName = prefs.getString(KEY_TIER, ProfilePlanTier.FREE.name)
        val tier = runCatching { ProfilePlanTier.valueOf(tierName ?: ProfilePlanTier.FREE.name) }
            .getOrDefault(ProfilePlanTier.FREE)
        val subStateName = prefs.getString(
            KEY_SUBSCRIPTION_STATE,
            SubscriptionBillingState.UNKNOWN.name,
        )
        val subscriptionState = runCatching {
            SubscriptionBillingState.valueOf(subStateName ?: SubscriptionBillingState.UNKNOWN.name)
        }.getOrDefault(SubscriptionBillingState.UNKNOWN)

        return CachedFamilyEntitlement(
            tier = tier,
            subscriptionState = subscriptionState,
            isAutoRenewing = prefs.getBoolean(KEY_AUTO_RENEWING, false),
            productId = prefs.getString(KEY_PRODUCT_ID, null),
            lastVerifiedEpochMs = prefs.getLong(KEY_LAST_VERIFIED_MS, 0L),
        )
    }

    override fun writeVerified(record: CachedFamilyEntitlement) {
        prefs.edit()
            .putString(KEY_TIER, record.tier.name)
            .putString(KEY_SUBSCRIPTION_STATE, record.subscriptionState.name)
            .putBoolean(KEY_AUTO_RENEWING, record.isAutoRenewing)
            .putString(KEY_PRODUCT_ID, record.productId)
            .putLong(KEY_LAST_VERIFIED_MS, record.lastVerifiedEpochMs)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "myaac_family_entitlement_cache"
        const val KEY_TIER = "tier"
        const val KEY_SUBSCRIPTION_STATE = "subscription_state"
        const val KEY_AUTO_RENEWING = "is_auto_renewing"
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_LAST_VERIFIED_MS = "last_verified_ms"
    }
}
