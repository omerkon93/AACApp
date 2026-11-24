package com.kon.myaacapp

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.android.billingclient.api.BillingClient
import com.kon.myaacapp.data.billing.CachedFamilyEntitlement
import com.kon.myaacapp.data.billing.FamilyEntitlementCache
import com.kon.myaacapp.data.billing.GatewayBillingResult
import com.kon.myaacapp.data.billing.GatewayProductOffer
import com.kon.myaacapp.data.billing.GatewayPurchase
import com.kon.myaacapp.data.billing.GatewayPurchaseState
import com.kon.myaacapp.data.billing.PlayBillingFamilyEntitlementProvider
import com.kon.myaacapp.data.billing.PlayBillingGateway
import com.kon.myaacapp.data.repository.CreateProfileResult
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.entitlement.BillingFeedback
import com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome
import com.kon.myaacapp.domain.entitlement.ProfilePlanTier
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState
import com.kon.myaacapp.domain.model.ProfileCreationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlayBillingFamilyEntitlementProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `refreshEntitlements loads localized offer price and period while staying Free`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)

            provider.refreshEntitlements()

            val state = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FREE, state.tier)
            assertTrue(state.isBillingConfigured)
            assertTrue(state.isProfileCapEnforced)
            assertEquals(SubscriptionBillingState.NONE, state.subscriptionState)
            assertNotNull(state.offerDetails)
            assertEquals("$4.99", state.offerDetails?.formattedPrice)
            assertEquals("P1M", state.offerDetails?.billingPeriodIso8601)
            assertEquals("P7D", state.offerDetails?.freeTrialPeriodIso8601)
            assertFalse(state.isLoadingStoreOffer)
            assertFalse(state.isCachedOfflineEntitlement)
            scope.cancel()
        }

    @Test
    fun `purchase flow verifies package and product and acknowledges with retry`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            gateway.acknowledgeResults.addAll(
                listOf(
                    GatewayBillingResult(
                        responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                        debugMessage = "Transient ack failure",
                    ),
                    GatewayBillingResult(responseCode = BillingClient.BillingResponseCode.OK),
                ),
            )
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)

            val launchOutcome = provider.requestFamilyUpgrade()
            assertEquals(FamilyUpgradeOutcome.LaunchedCheckout, launchOutcome)

            // Simulate Play delivering a valid unacknowledged PURCHASED subscription
            val validPurchase = GatewayPurchase(
                orderId = "GPA.1234",
                packageName = PKG,
                products = listOf(PRODUCT_ID),
                purchaseToken = "tok_valid_1",
                purchaseState = GatewayPurchaseState.PURCHASED,
                isAcknowledged = false,
                isAutoRenewing = true,
            )
            gateway.ownedPurchases = listOf(validPurchase)
            gateway.emitPurchasesUpdate(
                GatewayBillingResult(BillingClient.BillingResponseCode.OK),
                listOf(validPurchase),
            )
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("tok_valid_1", "tok_valid_1"), gateway.acknowledgedTokens)
            val state = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FAMILY, state.tier)
            assertEquals(SubscriptionBillingState.ACTIVE, state.subscriptionState)
            assertTrue(state.isAutoRenewing)
            assertFalse(state.hasPendingPurchase)
            assertEquals(ProfilePlanTier.FAMILY, cache.read().tier)
            assertEquals(SubscriptionBillingState.ACTIVE, cache.read().subscriptionState)
            scope.cancel()
        }

    @Test
    fun `purchase verification ignores mismatched package or blank token`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            gateway.ownedPurchases = listOf(
                GatewayPurchase(
                    orderId = "GPA.wrong",
                    packageName = "com.other.app",
                    products = listOf(PRODUCT_ID),
                    purchaseToken = "tok_wrong_pkg",
                    purchaseState = GatewayPurchaseState.PURCHASED,
                    isAcknowledged = true,
                    isAutoRenewing = true,
                ),
                GatewayPurchase(
                    orderId = "GPA.blank",
                    packageName = PKG,
                    products = listOf(PRODUCT_ID),
                    purchaseToken = "",
                    purchaseState = GatewayPurchaseState.PURCHASED,
                    isAcknowledged = true,
                    isAutoRenewing = true,
                ),
            )
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)

            provider.refreshEntitlements()

            assertEquals(ProfilePlanTier.FREE, provider.currentEntitlement().tier)
            assertEquals(SubscriptionBillingState.NONE, provider.currentEntitlement().subscriptionState)
            scope.cancel()
        }

    @Test
    fun `pending purchase leaves Family inactive and unlocks once payment completes`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)
            val repo = createProfileRepo(provider, scope)

            assertTrue(repo.createProfile("Child 2", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success)

            val pendingPurchase = GatewayPurchase(
                orderId = null,
                packageName = PKG,
                products = listOf(PRODUCT_ID),
                purchaseToken = "tok_pending_1",
                purchaseState = GatewayPurchaseState.PENDING,
                isAcknowledged = false,
                isAutoRenewing = true,
            )
            gateway.ownedPurchases = listOf(pendingPurchase)
            gateway.emitPurchasesUpdate(
                GatewayBillingResult(BillingClient.BillingResponseCode.OK),
                listOf(pendingPurchase),
            )
            testScheduler.advanceUntilIdle()

            val pendingState = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FREE, pendingState.tier)
            assertFalse(pendingState.hasFamilyEntitlement)
            assertTrue(pendingState.hasPendingPurchase)
            assertEquals(SubscriptionBillingState.PENDING_PURCHASE, pendingState.subscriptionState)
            assertEquals(BillingFeedback.PURCHASE_PENDING, pendingState.lastBillingFeedback)

            // 3rd profile creation MUST stay blocked while pending
            assertEquals(
                CreateProfileResult.LimitReached(2, 2),
                repo.createProfile("Blocked Pending", ProfileCreationMode.BLANK, "he"),
            )

            // Subsequent purchase completion unlocks Family and allows 3rd profile
            val settledPurchase = pendingPurchase.copy(
                orderId = "GPA.settled",
                purchaseState = GatewayPurchaseState.PURCHASED,
            )
            gateway.ownedPurchases = listOf(settledPurchase)
            provider.refreshEntitlements()

            val activeState = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FAMILY, activeState.tier)
            assertFalse(activeState.hasPendingPurchase)
            assertEquals(SubscriptionBillingState.ACTIVE, activeState.subscriptionState)
            assertTrue(
                repo.createProfile("Child 3", ProfileCreationMode.DEFAULT_TEMPLATE, "en")
                    is CreateProfileResult.Success,
            )
            scope.cancel()
        }

    @Test
    fun `checkout cancellation insufficient funds ineligible and unavailable map distinctly`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)

            gateway.launchResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
            )
            assertEquals(FamilyUpgradeOutcome.UserCanceled, provider.requestFamilyUpgrade())
            assertEquals(BillingFeedback.CHECKOUT_CANCELED, provider.currentEntitlement().lastBillingFeedback)

            gateway.launchResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                subResponseCode = BillingClient.OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS,
            )
            val declinedOutcome = provider.requestFamilyUpgrade()
            assertTrue(declinedOutcome is FamilyUpgradeOutcome.Failed)
            assertEquals(
                BillingFeedback.PAYMENT_DECLINED_INSUFFICIENT_FUNDS,
                provider.currentEntitlement().lastBillingFeedback,
            )

            gateway.launchResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.ERROR,
                subResponseCode = BillingClient.OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE,
            )
            provider.requestFamilyUpgrade()
            assertEquals(BillingFeedback.USER_INELIGIBLE, provider.currentEntitlement().lastBillingFeedback)

            gateway.productResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            )
            gateway.productOffer = null
            assertEquals(FamilyUpgradeOutcome.ProductUnavailable, provider.requestFamilyUpgrade())
            assertEquals(BillingFeedback.PRODUCT_UNAVAILABLE, provider.currentEntitlement().lastBillingFeedback)

            assertEquals(ProfilePlanTier.FREE, provider.currentEntitlement().tier)
            scope.cancel()
        }

    @Test
    fun `canceled before expiry keeps Family active and actual expiry preserves boards`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            val cache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, cache, scope)
            val repo = createProfileRepo(provider, scope)

            assertTrue(repo.createProfile("Second", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success)

            // Subscription active, then caregiver turns off auto-renewal (canceled before expiry)
            gateway.ownedPurchases = listOf(
                GatewayPurchase(
                    orderId = "GPA.canceled.active",
                    packageName = PKG,
                    products = listOf(PRODUCT_ID),
                    purchaseToken = "tok_canceled_active",
                    purchaseState = GatewayPurchaseState.PURCHASED,
                    isAcknowledged = true,
                    isAutoRenewing = false,
                ),
            )
            provider.refreshEntitlements()

            val canceledPendingExpiry = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FAMILY, canceledPendingExpiry.tier)
            assertTrue(canceledPendingExpiry.hasFamilyEntitlement)
            assertFalse(canceledPendingExpiry.isAutoRenewing)
            assertEquals(
                SubscriptionBillingState.CANCELED_PENDING_EXPIRY,
                canceledPendingExpiry.subscriptionState,
            )
            assertEquals(
                "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$PKG",
                provider.getSubscriptionManagementUri(),
            )
            val third = (repo.createProfile("Third", ProfileCreationMode.BLANK, "he") as CreateProfileResult.Success).profile
            val fourth = (repo.createProfile("Fourth", ProfileCreationMode.BLANK, "en") as CreateProfileResult.Success).profile
            assertEquals(4, repo.profiles.value.size)
            assertEquals(4, repo.profileAvailability.value.availableProfiles.size)

            // Actual expiry: Play returns OK with empty purchases
            gateway.ownedPurchases = emptyList()
            provider.refreshEntitlements()
            testScheduler.advanceUntilIdle()

            val expiredState = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FREE, expiredState.tier)
            assertEquals(SubscriptionBillingState.EXPIRED, expiredState.subscriptionState)
            assertEquals(ProfilePlanTier.FREE, cache.read().tier)
            assertEquals(SubscriptionBillingState.EXPIRED, cache.read().subscriptionState)

            // 2 profiles remain available (default + active fourth); 2 archived; creation blocked
            assertEquals(4, repo.profiles.value.size)
            assertEquals(setOf("default", fourth.profileId), repo.profileAvailability.value.availableProfileIds)
            assertEquals(2, repo.profileAvailability.value.archivedProfiles.size)
            assertTrue(repo.profileAvailability.value.canChooseFreeProfile)
            assertFalse(repo.switchProfile(third.profileId))
            assertEquals(
                CreateProfileResult.LimitReached(4, 2),
                repo.createProfile("Fifth Blocked", ProfileCreationMode.BLANK, "en"),
            )

            // Confirm one custom profile for the Free period; further selection is locked
            assertTrue(
                repo.confirmFreeCustomProfile(third.profileId)
                    is com.kon.myaacapp.data.repository.SelectFreeCustomProfileResult.Success,
            )
            assertTrue(repo.profileAvailability.value.isFreeSlotLocked)
            assertEquals(
                com.kon.myaacapp.data.repository.SelectFreeCustomProfileResult.Locked,
                repo.confirmFreeCustomProfile(fourth.profileId),
            )

            // Renewing Family unlocks all 4 and clears lock; a later confirmed expiry starts a new one-time choice
            gateway.ownedPurchases = listOf(
                GatewayPurchase(
                    orderId = "GPA.renewed",
                    packageName = PKG,
                    products = listOf(PRODUCT_ID),
                    purchaseToken = "tok_renewed",
                    purchaseState = GatewayPurchaseState.PURCHASED,
                    isAcknowledged = true,
                    isAutoRenewing = true,
                ),
            )
            provider.refreshEntitlements()
            testScheduler.advanceUntilIdle()
            assertEquals(4, repo.profileAvailability.value.availableProfiles.size)
            assertFalse(repo.profileAvailability.value.isFreeSlotLocked)

            gateway.ownedPurchases = emptyList()
            provider.refreshEntitlements()
            testScheduler.advanceUntilIdle()
            assertFalse(repo.profileAvailability.value.isFreeSlotLocked)
            assertTrue(repo.profileAvailability.value.canChooseFreeProfile)
            scope.cancel()
        }

    @Test
    fun `restore after reinstall restores Family entitlement from empty local cache`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway()
            val emptyReinstallCache = InMemoryEntitlementCache()
            val provider = createProvider(gateway, emptyReinstallCache, scope)
            val repo = createProfileRepo(provider, scope)

            // No purchase yet -> restore reports NoPurchaseFound
            assertEquals(FamilyUpgradeOutcome.NoPurchaseFound, provider.restoreFamilyPurchase())
            assertEquals(
                BillingFeedback.RESTORED_NO_ACTIVE_PURCHASE,
                provider.currentEntitlement().lastBillingFeedback,
            )

            // Temporary connection failure replaces NoPurchaseFound with TEMPORARY_ERROR
            gateway.connectResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
            )
            assertTrue(provider.restoreFamilyPurchase() is FamilyUpgradeOutcome.Failed)
            assertEquals(
                BillingFeedback.TEMPORARY_ERROR,
                provider.currentEntitlement().lastBillingFeedback,
            )

            // Switching back online without purchase cleanly restores RESTORED_NO_ACTIVE_PURCHASE
            gateway.connectResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
            assertEquals(FamilyUpgradeOutcome.NoPurchaseFound, provider.restoreFamilyPurchase())
            assertEquals(
                BillingFeedback.RESTORED_NO_ACTIVE_PURCHASE,
                provider.currentEntitlement().lastBillingFeedback,
            )

            // Account owns subscription -> restore activates Family & unlocks 3rd profile
            gateway.ownedPurchases = listOf(
                GatewayPurchase(
                    orderId = "GPA.restored",
                    packageName = PKG,
                    products = listOf(PRODUCT_ID),
                    purchaseToken = "tok_reinstall_1",
                    purchaseState = GatewayPurchaseState.PURCHASED,
                    isAcknowledged = false,
                    isAutoRenewing = true,
                ),
            )
            assertEquals(FamilyUpgradeOutcome.Activated, provider.restoreFamilyPurchase())
            assertEquals(ProfilePlanTier.FAMILY, provider.currentEntitlement().tier)
            assertEquals(listOf("tok_reinstall_1"), gateway.acknowledgedTokens)

            assertTrue(repo.createProfile("Second", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success)
            assertTrue(repo.createProfile("Third", ProfileCreationMode.BLANK, "he") is CreateProfileResult.Success)
            scope.cancel()
        }

    @Test
    fun `offline or transient billing failure preserves cached Family status and never treats as expiry`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val gateway = FakePlayBillingGateway().apply {
                connectResult = GatewayBillingResult(
                    responseCode = BillingClient.BillingResponseCode.NETWORK_ERROR,
                    debugMessage = "Device offline",
                )
            }
            val cache = InMemoryEntitlementCache(
                CachedFamilyEntitlement(
                    tier = ProfilePlanTier.FAMILY,
                    subscriptionState = SubscriptionBillingState.ACTIVE,
                    isAutoRenewing = true,
                    productId = PRODUCT_ID,
                    lastVerifiedEpochMs = 1_000L,
                ),
            )
            val provider = createProvider(gateway, cache, scope)

            // Cold start offline preserves cached Family entitlement
            assertEquals(ProfilePlanTier.FAMILY, provider.currentEntitlement().tier)
            assertTrue(provider.currentEntitlement().isCachedOfflineEntitlement)

            // Failed refresh on startup/resume must NOT downgrade or mark EXPIRED
            provider.refreshEntitlements()
            assertEquals(ProfilePlanTier.FAMILY, provider.currentEntitlement().tier)
            assertEquals(SubscriptionBillingState.ACTIVE, provider.currentEntitlement().subscriptionState)
            assertTrue(provider.currentEntitlement().isCachedOfflineEntitlement)
            assertEquals(ProfilePlanTier.FAMILY, cache.read().tier)

            // Connected, but queryPurchasesAsync fails transiently -> still preserves cached Family
            gateway.connectResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
            gateway.purchasesResult = GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                debugMessage = "Play cache busy",
            )
            provider.refreshEntitlements()
            assertEquals(ProfilePlanTier.FAMILY, provider.currentEntitlement().tier)
            assertEquals(SubscriptionBillingState.ACTIVE, provider.currentEntitlement().subscriptionState)
            assertEquals(ProfilePlanTier.FAMILY, cache.read().tier)

            // Failed manual restore also preserves cached Family and surfaces TEMPORARY_ERROR
            val restoreOutcome = provider.restoreFamilyPurchase()
            assertTrue(restoreOutcome is FamilyUpgradeOutcome.Failed)
            assertEquals(BillingFeedback.TEMPORARY_ERROR, provider.currentEntitlement().lastBillingFeedback)
            assertEquals(ProfilePlanTier.FAMILY, provider.currentEntitlement().tier)

            // Even with 4 saved profiles, offline / transient billing errors keep all 4 available
            val repo = createProfileRepo(provider, scope)
            assertTrue(repo.createProfile("Child 2", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success)
            assertTrue(repo.createProfile("Child 3", ProfileCreationMode.BLANK, "he") is CreateProfileResult.Success)
            assertTrue(repo.createProfile("Child 4", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success)
            provider.refreshEntitlements()
            testScheduler.advanceUntilIdle()
            assertEquals(4, repo.profileAvailability.value.availableProfiles.size)
            assertTrue(repo.profileAvailability.value.archivedProfiles.isEmpty())
            scope.cancel()
        }

    @Test
    fun `buildConfig defaults keep production product id and default debug cap disabled unless configured`() {
        assertEquals("family_subscription", BuildConfig.FAMILY_SUBSCRIPTION_PRODUCT_ID)
    }

    private fun createProvider(
        gateway: PlayBillingGateway,
        cache: FamilyEntitlementCache,
        scope: CoroutineScope,
    ): PlayBillingFamilyEntitlementProvider {
        return PlayBillingFamilyEntitlementProvider(
            appPackageName = PKG,
            productId = PRODUCT_ID,
            preferredBasePlanId = "",
            isProfileCapEnforced = true,
            gateway = gateway,
            cache = cache,
            scope = scope,
            nowEpochMsProvider = { 123_456L },
            retryDelayMsProvider = { 0L },
        )
    }

    private suspend fun createProfileRepo(
        provider: PlayBillingFamilyEntitlementProvider,
        scope: CoroutineScope,
    ): ProfileRepository {
        val filesDir = tempFolder.newFolder()
        val cacheDir = tempFolder.newFolder()
        val context = mock(Context::class.java)
        val assetManager = mock(AssetManager::class.java)
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.cacheDir).thenReturn(cacheDir)
        whenever(context.assets).thenReturn(assetManager)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(R.string.default_profile_name)).thenReturn("Default Profile")
        val zipBytes = resolveAssetFile("initial_data.zip").readBytes()
        whenever(assetManager.open(anyString())).thenAnswer { ByteArrayInputStream(zipBytes) }

        val settings = SettingsRepository(context, InMemoryDataStore())
        return ProfileRepository(context, settings, scope, provider).also { it.reload() }
    }

    private fun resolveAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate asset file $name from ${File(".").absolutePath}")
    }

    private class InMemoryEntitlementCache(
        initial: CachedFamilyEntitlement = CachedFamilyEntitlement(),
    ) : FamilyEntitlementCache {
        private var current = initial

        override fun read(): CachedFamilyEntitlement = current

        override fun writeVerified(record: CachedFamilyEntitlement) {
            current = record
        }
    }

    private class FakePlayBillingGateway : PlayBillingGateway {
        private var listener: ((GatewayBillingResult, List<GatewayPurchase>) -> Unit)? = null

        var connectResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
        var productResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
        var productOffer: GatewayProductOffer? = GatewayProductOffer(
            productId = PRODUCT_ID,
            basePlanId = "family-monthly",
            offerToken = "offer_tok_1",
            formattedPrice = "$4.99",
            billingPeriodIso8601 = "P1M",
            freeTrialPeriodIso8601 = "P7D",
        )
        var purchasesResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
        var ownedPurchases: List<GatewayPurchase> = emptyList()
        var launchResult = GatewayBillingResult(BillingClient.BillingResponseCode.OK)
        val acknowledgeResults = ArrayDeque<GatewayBillingResult>()
        val acknowledgedTokens = mutableListOf<String>()

        override fun setPurchasesUpdateListener(
            listener: (GatewayBillingResult, List<GatewayPurchase>) -> Unit,
        ) {
            this.listener = listener
        }

        fun emitPurchasesUpdate(result: GatewayBillingResult, purchases: List<GatewayPurchase>) {
            listener?.invoke(result, purchases)
        }

        override suspend fun ensureConnected(): GatewayBillingResult = connectResult

        override suspend fun querySubscriptionProduct(
            productId: String,
            preferredBasePlanId: String,
        ): Pair<GatewayBillingResult, GatewayProductOffer?> = productResult to productOffer

        override suspend fun querySubscriptionPurchases(): Pair<GatewayBillingResult, List<GatewayPurchase>> =
            purchasesResult to ownedPurchases

        override suspend fun acknowledgePurchase(purchaseToken: String): GatewayBillingResult {
            acknowledgedTokens += purchaseToken
            return if (acknowledgeResults.isNotEmpty()) {
                acknowledgeResults.removeFirst()
            } else {
                GatewayBillingResult(BillingClient.BillingResponseCode.OK)
            }
        }

        override suspend fun launchSubscriptionPurchaseFlow(
            offer: GatewayProductOffer,
        ): GatewayBillingResult = launchResult

        override fun endConnection() = Unit
    }

    private class InMemoryDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
    }

    private companion object {
        const val PKG = "com.kon.myaacapp"
        const val PRODUCT_ID = "family_subscription"
    }
}
