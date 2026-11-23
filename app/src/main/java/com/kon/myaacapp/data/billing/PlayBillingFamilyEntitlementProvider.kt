package com.kon.myaacapp.data.billing

import android.util.Log
import com.android.billingclient.api.BillingClient
import com.kon.myaacapp.domain.entitlement.BillingFeedback
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementState
import com.kon.myaacapp.domain.entitlement.FamilyPlanOfferDetails
import com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome
import com.kon.myaacapp.domain.entitlement.ProfileLimitPolicy
import com.kon.myaacapp.domain.entitlement.ProfilePlanTier
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlayBillingFamilyEntitlementProvider(
    private val appPackageName: String,
    private val productId: String,
    private val preferredBasePlanId: String = "",
    private val isProfileCapEnforced: Boolean,
    private val gateway: PlayBillingGateway,
    private val cache: FamilyEntitlementCache,
    private val scope: CoroutineScope,
    private val nowEpochMsProvider: () -> Long = { System.currentTimeMillis() },
    private val retryDelayMsProvider: (Int) -> Long = { attempt ->
        2_000L * (1 shl (attempt - 1))
    },
) : FamilyEntitlementProvider {

    private val operationMutex = Mutex()
    private val _entitlementState: MutableStateFlow<FamilyEntitlementState>

    init {
        val cached = cache.read()
        val cachedActive = cached.tier == ProfilePlanTier.FAMILY
        _entitlementState = MutableStateFlow(
            FamilyEntitlementState(
                tier = cached.tier,
                isBillingConfigured = true,
                isProfileCapEnforced = isProfileCapEnforced,
                freeProfileLimit = ProfileLimitPolicy.FREE_PROFILE_LIMIT,
                subscriptionState = cached.subscriptionState,
                isAutoRenewing = cached.isAutoRenewing,
                hasPendingPurchase = false,
                isCachedOfflineEntitlement = cachedActive,
            ),
        )

        gateway.setPurchasesUpdateListener { billingResult, purchases ->
            scope.launch {
                handlePurchasesUpdated(billingResult, purchases)
            }
        }
    }

    override val entitlementState: StateFlow<FamilyEntitlementState> =
        _entitlementState.asStateFlow()

    override suspend fun refreshEntitlements() {
        operationMutex.withLock {
            refreshInternal(
                surfaceErrorFeedback = false,
                markLoadingOffer = _entitlementState.value.offerDetails == null,
            )
        }
    }

    override suspend fun requestFamilyUpgrade(): FamilyUpgradeOutcome =
        operationMutex.withLock {
            val connectionResult = gateway.ensureConnected()
            if (!connectionResult.isOk) {
                val feedback = mapErrorFeedback(connectionResult)
                _entitlementState.update { it.copy(lastBillingFeedback = feedback) }
                return@withLock if (feedback == BillingFeedback.BILLING_UNAVAILABLE) {
                    FamilyUpgradeOutcome.BillingUnavailable
                } else {
                    FamilyUpgradeOutcome.Failed(
                        reason = connectionResult.debugMessage.ifBlank { null },
                        feedback = feedback,
                    )
                }
            }

            val (offerResult, queriedOffer) = gateway.querySubscriptionProduct(
                productId = productId,
                preferredBasePlanId = preferredBasePlanId,
            )

            if (!offerResult.isOk || queriedOffer == null) {
                val feedback = if (offerResult.responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
                    BillingFeedback.PRODUCT_UNAVAILABLE
                } else {
                    mapErrorFeedback(offerResult)
                }
                _entitlementState.update { it.copy(lastBillingFeedback = feedback) }
                return@withLock when (feedback) {
                    BillingFeedback.PRODUCT_UNAVAILABLE -> FamilyUpgradeOutcome.ProductUnavailable
                    BillingFeedback.BILLING_UNAVAILABLE -> FamilyUpgradeOutcome.BillingUnavailable
                    else -> FamilyUpgradeOutcome.Failed(
                        reason = offerResult.debugMessage.ifBlank { null },
                        feedback = feedback,
                    )
                }
            }

            _entitlementState.update { current ->
                current.copy(
                    offerDetails = queriedOffer.toDomainOffer(),
                    lastBillingFeedback = BillingFeedback.NONE,
                )
            }

            // Refresh existing purchases first so an already-owned subscription restores instead of failing.
            val (preCheckResult, existingPurchases) = gateway.querySubscriptionPurchases()
            if (preCheckResult.isOk) {
                applyAuthoritativePurchases(existingPurchases)
                val current = _entitlementState.value
                if (current.hasFamilyEntitlement) {
                    return@withLock FamilyUpgradeOutcome.Activated
                }
                if (current.hasPendingPurchase) {
                    return@withLock FamilyUpgradeOutcome.Pending
                }
            }

            val launchResult = gateway.launchSubscriptionPurchaseFlow(queriedOffer)
            when (launchResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> FamilyUpgradeOutcome.LaunchedCheckout
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _entitlementState.update {
                        it.copy(lastBillingFeedback = BillingFeedback.CHECKOUT_CANCELED)
                    }
                    FamilyUpgradeOutcome.UserCanceled
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    val (recheckResult, refreshed) = gateway.querySubscriptionPurchases()
                    if (recheckResult.isOk) {
                        applyAuthoritativePurchases(refreshed)
                        if (_entitlementState.value.hasFamilyEntitlement) {
                            return@withLock FamilyUpgradeOutcome.Activated
                        }
                    }
                    FamilyUpgradeOutcome.Failed(
                        reason = launchResult.debugMessage.ifBlank { null },
                        feedback = BillingFeedback.TEMPORARY_ERROR,
                    )
                }

                else -> {
                    val feedback = mapErrorFeedback(launchResult)
                    _entitlementState.update { it.copy(lastBillingFeedback = feedback) }
                    when (feedback) {
                        BillingFeedback.BILLING_UNAVAILABLE -> FamilyUpgradeOutcome.BillingUnavailable
                        BillingFeedback.PRODUCT_UNAVAILABLE -> FamilyUpgradeOutcome.ProductUnavailable
                        else -> FamilyUpgradeOutcome.Failed(
                            reason = launchResult.debugMessage.ifBlank { null },
                            feedback = feedback,
                        )
                    }
                }
            }
        }

    override suspend fun restoreFamilyPurchase(): FamilyUpgradeOutcome =
        operationMutex.withLock {
            val connectionResult = gateway.ensureConnected()
            if (!connectionResult.isOk) {
                val feedback = mapErrorFeedback(connectionResult)
                _entitlementState.update { it.copy(lastBillingFeedback = feedback) }
                return@withLock if (feedback == BillingFeedback.BILLING_UNAVAILABLE) {
                    FamilyUpgradeOutcome.BillingUnavailable
                } else {
                    FamilyUpgradeOutcome.Failed(
                        reason = connectionResult.debugMessage.ifBlank { null },
                        feedback = feedback,
                    )
                }
            }

            val (purchasesResult, purchases) = gateway.querySubscriptionPurchases()
            if (!purchasesResult.isOk) {
                // Failed billing query must not clear cached Family entitlement.
                val feedback = mapErrorFeedback(purchasesResult)
                _entitlementState.update { it.copy(lastBillingFeedback = feedback) }
                return@withLock if (feedback == BillingFeedback.BILLING_UNAVAILABLE) {
                    FamilyUpgradeOutcome.BillingUnavailable
                } else {
                    FamilyUpgradeOutcome.Failed(
                        reason = purchasesResult.debugMessage.ifBlank { null },
                        feedback = feedback,
                    )
                }
            }

            applyAuthoritativePurchases(purchases)
            val updated = _entitlementState.value
            when {
                updated.hasFamilyEntitlement -> FamilyUpgradeOutcome.Activated
                updated.hasPendingPurchase -> FamilyUpgradeOutcome.Pending
                else -> {
                    _entitlementState.update {
                        it.copy(lastBillingFeedback = BillingFeedback.RESTORED_NO_ACTIVE_PURCHASE)
                    }
                    FamilyUpgradeOutcome.NoPurchaseFound
                }
            }
        }

    override fun clearBillingFeedback() {
        _entitlementState.update { it.copy(lastBillingFeedback = BillingFeedback.NONE) }
    }

    override fun getSubscriptionManagementUri(): String {
        return "https://play.google.com/store/account/subscriptions?sku=$productId&package=$appPackageName"
    }

    private suspend fun handlePurchasesUpdated(
        billingResult: GatewayBillingResult,
        purchases: List<GatewayPurchase>,
    ) {
        operationMutex.withLock {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    applyAuthoritativePurchases(purchases)
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _entitlementState.update { current ->
                        current.copy(lastBillingFeedback = BillingFeedback.CHECKOUT_CANCELED)
                    }
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    val (queryResult, owned) = gateway.querySubscriptionPurchases()
                    if (queryResult.isOk) {
                        applyAuthoritativePurchases(owned)
                    }
                }

                else -> {
                    val feedback = mapErrorFeedback(billingResult)
                    _entitlementState.update { current ->
                        current.copy(lastBillingFeedback = feedback)
                    }
                }
            }
        }
    }

    private suspend fun refreshInternal(
        surfaceErrorFeedback: Boolean,
        markLoadingOffer: Boolean,
    ) {
        if (markLoadingOffer) {
            _entitlementState.update { it.copy(isLoadingStoreOffer = true) }
        }

        val connectionResult = gateway.ensureConnected()
        if (!connectionResult.isOk) {
            _entitlementState.update { current ->
                current.copy(
                    isLoadingStoreOffer = false,
                    isCachedOfflineEntitlement = current.tier == ProfilePlanTier.FAMILY,
                    lastBillingFeedback = if (surfaceErrorFeedback) {
                        mapErrorFeedback(connectionResult)
                    } else {
                        current.lastBillingFeedback
                    },
                )
            }
            return
        }

        val (offerResult, queriedOffer) = gateway.querySubscriptionProduct(
            productId = productId,
            preferredBasePlanId = preferredBasePlanId,
        )
        if (offerResult.isOk && queriedOffer != null) {
            _entitlementState.update { current ->
                current.copy(
                    offerDetails = queriedOffer.toDomainOffer(),
                    isLoadingStoreOffer = false,
                )
            }
        } else {
            _entitlementState.update { it.copy(isLoadingStoreOffer = false) }
        }

        val (purchasesResult, purchases) = gateway.querySubscriptionPurchases()
        if (!purchasesResult.isOk) {
            // Transient/offline query failure must NOT treat existing entitlement as expired.
            _entitlementState.update { current ->
                current.copy(
                    isCachedOfflineEntitlement = current.tier == ProfilePlanTier.FAMILY,
                    lastBillingFeedback = if (surfaceErrorFeedback) {
                        mapErrorFeedback(purchasesResult)
                    } else {
                        current.lastBillingFeedback
                    },
                )
            }
            return
        }

        applyAuthoritativePurchases(purchases)
    }

    private suspend fun applyAuthoritativePurchases(purchases: List<GatewayPurchase>) {
        val matchingPurchases = purchases.filter { purchase ->
            purchase.products.contains(productId) &&
                (purchase.packageName.isBlank() || purchase.packageName == appPackageName)
        }

        val verifiedPurchased = matchingPurchases.firstOrNull { purchase ->
            purchase.purchaseState == GatewayPurchaseState.PURCHASED &&
                isClientPurchaseValid(purchase)
        }

        if (verifiedPurchased != null) {
            if (!verifiedPurchased.isAcknowledged) {
                acknowledgeWithRetry(verifiedPurchased.purchaseToken)
            }

            val subState = if (verifiedPurchased.isAutoRenewing) {
                SubscriptionBillingState.ACTIVE
            } else {
                SubscriptionBillingState.CANCELED_PENDING_EXPIRY
            }

            cache.writeVerified(
                CachedFamilyEntitlement(
                    tier = ProfilePlanTier.FAMILY,
                    subscriptionState = subState,
                    isAutoRenewing = verifiedPurchased.isAutoRenewing,
                    productId = productId,
                    lastVerifiedEpochMs = nowEpochMsProvider(),
                ),
            )

            _entitlementState.update { current ->
                current.copy(
                    tier = ProfilePlanTier.FAMILY,
                    subscriptionState = subState,
                    isAutoRenewing = verifiedPurchased.isAutoRenewing,
                    hasPendingPurchase = false,
                    isCachedOfflineEntitlement = false,
                    lastBillingFeedback = BillingFeedback.NONE,
                )
            }
            return
        }

        val pendingPurchase = matchingPurchases.firstOrNull { purchase ->
            purchase.purchaseState == GatewayPurchaseState.PENDING
        }

        if (pendingPurchase != null) {
            // Pending transactions MUST leave Family inactive until Play confirms payment.
            cache.writeVerified(
                CachedFamilyEntitlement(
                    tier = ProfilePlanTier.FREE,
                    subscriptionState = SubscriptionBillingState.PENDING_PURCHASE,
                    isAutoRenewing = false,
                    productId = productId,
                    lastVerifiedEpochMs = nowEpochMsProvider(),
                ),
            )

            _entitlementState.update { current ->
                current.copy(
                    tier = ProfilePlanTier.FREE,
                    subscriptionState = SubscriptionBillingState.PENDING_PURCHASE,
                    isAutoRenewing = false,
                    hasPendingPurchase = true,
                    isCachedOfflineEntitlement = false,
                    lastBillingFeedback = BillingFeedback.PURCHASE_PENDING,
                )
            }
            return
        }

        // Authoritative OK response from Play with no active/pending Family subscription.
        val hadPreviousFamilyEntitlement =
            _entitlementState.value.tier == ProfilePlanTier.FAMILY ||
                cache.read().tier == ProfilePlanTier.FAMILY

        val resolvedSubState = if (hadPreviousFamilyEntitlement) {
            SubscriptionBillingState.EXPIRED
        } else {
            SubscriptionBillingState.NONE
        }

        cache.writeVerified(
            CachedFamilyEntitlement(
                tier = ProfilePlanTier.FREE,
                subscriptionState = resolvedSubState,
                isAutoRenewing = false,
                productId = productId,
                lastVerifiedEpochMs = nowEpochMsProvider(),
            ),
        )

        _entitlementState.update { current ->
            current.copy(
                tier = ProfilePlanTier.FREE,
                subscriptionState = resolvedSubState,
                isAutoRenewing = false,
                hasPendingPurchase = false,
                isCachedOfflineEntitlement = false,
            )
        }
    }

    private fun isClientPurchaseValid(purchase: GatewayPurchase): Boolean {
        if (purchase.purchaseToken.isBlank()) {
            return false
        }
        if (!purchase.products.contains(productId)) {
            return false
        }
        if (purchase.packageName.isNotBlank() && purchase.packageName != appPackageName) {
            return false
        }
        return true
    }

    private suspend fun acknowledgeWithRetry(purchaseToken: String) {
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            val ackResult = gateway.acknowledgePurchase(purchaseToken)
            when (ackResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> return
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                    Log.w(TAG, "Acknowledge returned ITEM_NOT_OWNED; refreshing purchases")
                    gateway.querySubscriptionPurchases()
                    return
                }

                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingClient.BillingResponseCode.NETWORK_ERROR,
                BillingClient.BillingResponseCode.ERROR,
                -> {
                    if (attempt < maxAttempts) {
                        delay(retryDelayMsProvider(attempt))
                    }
                }

                else -> {
                    Log.e(
                        TAG,
                        "Non-retriable acknowledgement error ${ackResult.responseCode}: ${ackResult.debugMessage}",
                    )
                    return
                }
            }
        }
    }

    private fun mapErrorFeedback(result: GatewayBillingResult): BillingFeedback {
        when (result.subResponseCode) {
            BillingClient.OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS ->
                return BillingFeedback.PAYMENT_DECLINED_INSUFFICIENT_FUNDS

            BillingClient.OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE ->
                return BillingFeedback.USER_INELIGIBLE
        }

        return when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingFeedback.CHECKOUT_CANCELED
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> BillingFeedback.BILLING_UNAVAILABLE

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingFeedback.PRODUCT_UNAVAILABLE
            else -> BillingFeedback.TEMPORARY_ERROR
        }
    }

    private fun GatewayProductOffer.toDomainOffer(): FamilyPlanOfferDetails {
        return FamilyPlanOfferDetails(
            productId = productId,
            basePlanId = basePlanId,
            offerToken = offerToken,
            formattedPrice = formattedPrice,
            billingPeriodIso8601 = billingPeriodIso8601,
            freeTrialPeriodIso8601 = freeTrialPeriodIso8601,
        )
    }

    private companion object {
        const val TAG = "PlayBillingEntitlement"
    }
}
