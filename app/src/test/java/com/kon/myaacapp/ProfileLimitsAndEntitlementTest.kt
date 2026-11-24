package com.kon.myaacapp

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.CreateProfileResult
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SelectFreeCustomProfileResult
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.entitlement.ConfigurableFamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementState
import com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome
import com.kon.myaacapp.domain.entitlement.ProfileLimitPolicy
import com.kon.myaacapp.domain.entitlement.ProfilePlanTier
import com.kon.myaacapp.domain.entitlement.StoreFamilyEntitlementProvider
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.UserProfile
import com.kon.myaacapp.ui.profile.ProfileManagerAction
import com.kon.myaacapp.ui.profile.ProfileManagerEffect
import com.kon.myaacapp.ui.profile.ProfileManagerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLimitsAndEntitlementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var initialZipBytes: ByteArray

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        context = mock(Context::class.java)
        assetManager = mock(AssetManager::class.java)
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.cacheDir).thenReturn(cacheDir)
        whenever(context.assets).thenReturn(assetManager)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(R.string.default_profile_name)).thenReturn("Default Profile")

        initialZipBytes = resolveAssetFile("initial_data.zip").readBytes()
        whenever(assetManager.open(anyString())).thenAnswer {
            ByteArrayInputStream(initialZipBytes)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `production StoreFamilyEntitlementProvider disables cap until billing is configured`() =
        runTest(testDispatcher) {
            val provider = StoreFamilyEntitlementProvider()
            val entitlement = provider.currentEntitlement()
            assertEquals(ProfilePlanTier.FREE, entitlement.tier)
            assertFalse(entitlement.isBillingConfigured)
            assertFalse(entitlement.isProfileCapEnforced)
            assertEquals(2, entitlement.freeProfileLimit)
            assertEquals(FamilyUpgradeOutcome.BillingUnavailable, provider.requestFamilyUpgrade())
            assertEquals(FamilyUpgradeOutcome.BillingUnavailable, provider.restoreFamilyPurchase())
            assertTrue(ProfileLimitPolicy.canCreateProfile(savedProfileCount = 5, entitlement = entitlement))

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, provider)
            repo.reload()

            assertTrue(
                repo.createProfile("Second", ProfileCreationMode.BLANK, "en") is CreateProfileResult.Success,
            )
            assertTrue(
                repo.createProfile("Third", ProfileCreationMode.DEFAULT_TEMPLATE, "he") is CreateProfileResult.Success,
            )
            assertEquals(3, repo.profiles.value.size)
            scope.cancel()
        }

    @Test
    fun `enforced Free tier allows two profiles across HE and EN and blocks third for all creation modes`() =
        runTest(testDispatcher) {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)

            repo.reload()
            assertEquals(1, repo.profiles.value.size)

            val second = repo.createProfile(
                name = "English Starter",
                creationMode = ProfileCreationMode.DEFAULT_TEMPLATE,
                boardLanguageCode = "en",
            )
            assertTrue(second is CreateProfileResult.Success)
            assertEquals("en", (second as CreateProfileResult.Success).profile.activeLanguageCode)
            testScheduler.advanceUntilIdle()
            assertEquals(2, repo.profiles.value.size)
            assertEquals("en", repo.activeProfile.value?.activeLanguageCode)

            listOf(
                ProfileCreationMode.DEFAULT_TEMPLATE,
                ProfileCreationMode.BLANK,
                ProfileCreationMode.DUPLICATE_CURRENT,
            ).forEach { mode ->
                val blocked = repo.createProfile(
                    name = "Third $mode",
                    creationMode = mode,
                    boardLanguageCode = "he",
                )
                assertEquals(
                    CreateProfileResult.LimitReached(savedProfileCount = 2, freeProfileLimit = 2),
                    blocked,
                )
            }
            assertEquals(2, repo.profiles.value.size)
            assertEquals(2, File(filesDir, "profiles").listFiles()?.count { it.extension == "json" })
            scope.cancel()
        }

    @Test
    fun `concurrent createProfile calls at 1 saved profile allow only one creation under Free cap`() =
        runBlocking {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)

            repo.reload()
            assertEquals(1, repo.profiles.value.size)

            val results = (1..4).map { idx ->
                async(Dispatchers.IO) {
                    repo.createProfile(
                        name = "Concurrent $idx",
                        creationMode = ProfileCreationMode.BLANK,
                        boardLanguageCode = if (idx % 2 == 0) "en" else "he",
                    )
                }
            }.awaitAll()

            assertEquals(1, results.count { it is CreateProfileResult.Success })
            assertEquals(3, results.count { it is CreateProfileResult.LimitReached })
            assertEquals(2, repo.profiles.value.size)
            assertEquals(2, File(filesDir, "profiles").listFiles()?.count { it.extension == "json" })
            scope.cancel()
        }

    @Test
    fun `Family creates four profiles and expiry keeps starter plus current custom available while archiving extras`() =
        runTest(testDispatcher) {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val dataStore = InMemoryDataStore()
            val settings = SettingsRepository(context, dataStore)
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)
            repo.reload()

            val second = repo.createProfile("Second EN", ProfileCreationMode.BLANK, "en") as CreateProfileResult.Success
            assertTrue(
                repo.createProfile("Blocked", ProfileCreationMode.BLANK, "he") is CreateProfileResult.LimitReached,
            )

            // Upgrade to Family and create 3rd and 4th profiles
            entitlementProvider.setFamilyEntitlementActive(true)
            val third = repo.createProfile(
                "Third Starter",
                ProfileCreationMode.DEFAULT_TEMPLATE,
                "en",
            ) as CreateProfileResult.Success
            val fourth = repo.createProfile(
                "Fourth HE",
                ProfileCreationMode.BLANK,
                "he",
            ) as CreateProfileResult.Success
            assertEquals(4, repo.profiles.value.size)
            assertEquals(4, repo.profileAvailability.value.availableProfiles.size)
            assertTrue(repo.profileAvailability.value.archivedProfiles.isEmpty())

            // Use second profile so it becomes current before expiry
            assertTrue(repo.switchProfile(second.profile.profileId))
            assertEquals(second.profile.profileId, repo.activeProfile.value?.profileId)

            // Expire Family back to Free -> default + second remain available; third & fourth archived
            entitlementProvider.setFamilyEntitlementActive(false)
            testScheduler.advanceUntilIdle()
            assertEquals(4, repo.profiles.value.size)
            assertEquals(
                setOf("default", second.profile.profileId),
                repo.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(third.profile.profileId, fourth.profile.profileId),
                repo.profileAvailability.value.archivedProfileIds,
            )
            // Current board stays active and editable
            assertEquals(second.profile.profileId, repo.activeProfile.value?.profileId)
            val editedSecond = second.profile.copy(
                layout = mapOf(
                    "root_yes" to TileLayoutState(
                        tileId = "yes",
                        parentId = null,
                        cellIndex = 0,
                    ),
                ),
            )
            repo.updateActiveProfile(editedSecond)
            assertEquals(1, repo.activeProfile.value?.layout?.size)

            // Direct switch to an archived profile is blocked until swapped in
            assertFalse(repo.switchProfile(third.profile.profileId))
            assertEquals(second.profile.profileId, repo.activeProfile.value?.profileId)

            // Confirming the Free choice makes the archived profile available and active.
            assertTrue(repo.profileAvailability.value.canChooseFreeProfile)
            assertFalse(repo.profileAvailability.value.isFreeSlotLocked)
            val confirmResult = repo.confirmFreeCustomProfile(third.profile.profileId)
            assertTrue(confirmResult is SelectFreeCustomProfileResult.Success)
            assertTrue((confirmResult as SelectFreeCustomProfileResult.Success).activeProfileChanged)
            assertEquals(
                setOf("default", third.profile.profileId),
                repo.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(second.profile.profileId, fourth.profile.profileId),
                repo.profileAvailability.value.archivedProfileIds,
            )
            assertEquals(third.profile.profileId, repo.activeProfile.value?.profileId)

            assertTrue(repo.profileAvailability.value.isFreeSlotLocked)
            assertFalse(repo.profileAvailability.value.canChooseFreeProfile)
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                repo.confirmFreeCustomProfile(second.profile.profileId),
            )
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                repo.confirmFreeCustomProfile(fourth.profile.profileId),
            )
            assertFalse(repo.switchProfile(second.profile.profileId))
            assertTrue(repo.switchProfile("default"))
            assertTrue(repo.switchProfile(third.profile.profileId))

            // Verify choice and lock persist across relaunch
            val relaunchedRepo = ProfileRepository(context, SettingsRepository(context, dataStore), scope, entitlementProvider)
            relaunchedRepo.reload()
            assertTrue(relaunchedRepo.profileAvailability.value.isFreeSlotLocked)
            assertFalse(relaunchedRepo.profileAvailability.value.canChooseFreeProfile)
            assertEquals(
                setOf("default", third.profile.profileId),
                relaunchedRepo.profileAvailability.value.availableProfileIds,
            )
            assertEquals(third.profile.profileId, relaunchedRepo.activeProfile.value?.profileId)
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                relaunchedRepo.confirmFreeCustomProfile(second.profile.profileId),
            )
            // Previous edited layout on archived second is still preserved on disk
            assertEquals(
                1,
                relaunchedRepo.profiles.value.first { it.profileId == second.profile.profileId }.layout.size,
            )

            // Creating 5th is blocked while count >= 2
            assertEquals(
                CreateProfileResult.LimitReached(savedProfileCount = 4, freeProfileLimit = 2),
                relaunchedRepo.createProfile("Fifth", ProfileCreationMode.DUPLICATE_CURRENT, "en"),
            )

            // Restore Family -> unlocks slot & all 4 become available again without import
            entitlementProvider.setFamilyEntitlementActive(true)
            testScheduler.advanceUntilIdle()
            assertFalse(relaunchedRepo.profileAvailability.value.isFreeSlotLocked)
            assertEquals(4, relaunchedRepo.profileAvailability.value.availableProfiles.size)
            assertTrue(relaunchedRepo.profileAvailability.value.archivedProfiles.isEmpty())
            assertTrue(relaunchedRepo.switchProfile(second.profile.profileId))
            assertEquals(1, relaunchedRepo.activeProfile.value?.layout?.size)

            // A later confirmed expiry starts a new one-time selection
            entitlementProvider.setFamilyEntitlementActive(false)
            testScheduler.advanceUntilIdle()
            assertFalse(relaunchedRepo.profileAvailability.value.isFreeSlotLocked)
            assertTrue(relaunchedRepo.profileAvailability.value.canChooseFreeProfile)
            assertTrue(
                relaunchedRepo.confirmFreeCustomProfile(fourth.profile.profileId)
                    is SelectFreeCustomProfileResult.Success,
            )
            assertTrue(relaunchedRepo.profileAvailability.value.isFreeSlotLocked)
            assertEquals(
                setOf("default", fourth.profile.profileId),
                relaunchedRepo.profileAvailability.value.availableProfileIds,
            )

            // Re-upgrade, expire cleanly, switch to default, and delete until < 2 remain -> Free creation unlocks
            entitlementProvider.setFamilyEntitlementActive(true)
            testScheduler.advanceUntilIdle()
            entitlementProvider.setFamilyEntitlementActive(false)
            testScheduler.advanceUntilIdle()
            assertTrue(relaunchedRepo.switchProfile("default"))
            relaunchedRepo.deleteProfile(fourth.profile.profileId)
            relaunchedRepo.deleteProfile(third.profile.profileId)
            assertEquals(2, relaunchedRepo.profiles.value.size)
            assertEquals(
                CreateProfileResult.LimitReached(savedProfileCount = 2, freeProfileLimit = 2),
                relaunchedRepo.createProfile("Still Blocked", ProfileCreationMode.BLANK, "he"),
            )

            relaunchedRepo.deleteProfile(second.profile.profileId)
            assertEquals(1, relaunchedRepo.profiles.value.size)
            val recreated = relaunchedRepo.createProfile("Recreated", ProfileCreationMode.BLANK, "en")
            assertTrue(recreated is CreateProfileResult.Success)
            assertEquals(2, relaunchedRepo.profiles.value.size)

            scope.cancel()
        }

    @Test
    fun `Family expiry while starter profile is active keeps starter and last-used custom profile available`() =
        runTest(testDispatcher) {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FAMILY,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)
            repo.reload()

            val c1 = (repo.createProfile("Custom 1", ProfileCreationMode.BLANK, "he") as CreateProfileResult.Success).profile
            val c2 = (repo.createProfile("Custom 2", ProfileCreationMode.BLANK, "en") as CreateProfileResult.Success).profile
            val c3 = (repo.createProfile("Custom 3", ProfileCreationMode.BLANK, "he") as CreateProfileResult.Success).profile

            // Use c2, then switch to default starter before expiry
            assertTrue(repo.switchProfile(c2.profileId))
            assertTrue(repo.switchProfile("default"))
            assertEquals("default", repo.activeProfile.value?.profileId)

            // Expire Family -> starter + last-used custom (c2) stay available; c1 & c3 archived
            entitlementProvider.setFamilyEntitlementActive(false)
            testScheduler.advanceUntilIdle()

            assertEquals("default", repo.activeProfile.value?.profileId)
            assertEquals(
                setOf("default", c2.profileId),
                repo.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(c1.profileId, c3.profileId),
                repo.profileAvailability.value.archivedProfileIds,
            )

            // Choosing and confirming custom profile while starter is active keeps starter active and locks slot
            val confirmResult = repo.confirmFreeCustomProfile(c1.profileId)
            assertTrue(confirmResult is SelectFreeCustomProfileResult.Success)
            assertFalse((confirmResult as SelectFreeCustomProfileResult.Success).activeProfileChanged)
            assertEquals("default", repo.activeProfile.value?.profileId)
            assertTrue(repo.profileAvailability.value.isFreeSlotLocked)
            assertFalse(repo.profileAvailability.value.canChooseFreeProfile)
            assertEquals(
                setOf("default", c1.profileId),
                repo.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(c2.profileId, c3.profileId),
                repo.profileAvailability.value.archivedProfileIds,
            )
            assertEquals(SelectFreeCustomProfileResult.Locked, repo.confirmFreeCustomProfile(c2.profileId))
            assertFalse(repo.switchProfile(c2.profileId))
            assertTrue(repo.switchProfile(c1.profileId))
            assertEquals(c1.profileId, repo.activeProfile.value?.profileId)
            scope.cancel()
        }

    @Test
    fun `restored backup with more than two profiles on Free allows one initial choice then locks and preserves absent choice`() =
        runTest(testDispatcher) {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val profilesDir = File(filesDir, "profiles").apply { mkdirs() }
            val restoredProfiles = listOf(
                UserProfile("default", "Default", "he", emptyMap()),
                UserProfile("restored_2", "Restored EN", "en", emptyMap()),
                UserProfile("restored_3", "Restored HE", "he", emptyMap()),
                UserProfile("restored_4", "Restored 4", "en", emptyMap()),
            )
            restoredProfiles.forEach { profile ->
                File(profilesDir, "profile_${profile.profileId}.json").writeText(
                    json.encodeToString(profile),
                )
            }

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val dataStore = InMemoryDataStore()
            val settings = SettingsRepository(context, dataStore)
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)
            repo.reload()

            assertEquals(4, repo.profiles.value.size)
            assertEquals(2, repo.profileAvailability.value.availableProfiles.size)
            assertEquals(2, repo.profileAvailability.value.archivedProfiles.size)
            assertTrue(repo.profileAvailability.value.canChooseFreeProfile)
            assertFalse(repo.profileAvailability.value.isFreeSlotLocked)
            assertEquals(
                setOf("default", "restored_2"),
                repo.profileAvailability.value.availableProfileIds,
            )

            // Cannot switch directly to archived restored_3 before selecting and confirming
            assertFalse(repo.switchProfile("restored_3"))
            assertTrue(
                repo.confirmFreeCustomProfile("restored_3") is SelectFreeCustomProfileResult.Success,
            )
            assertTrue(repo.profileAvailability.value.isFreeSlotLocked)
            assertFalse(repo.profileAvailability.value.canChooseFreeProfile)
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                repo.confirmFreeCustomProfile("restored_4"),
            )
            assertTrue(repo.switchProfile("restored_3"))
            assertEquals("restored_3", repo.activeProfile.value?.profileId)
            assertTrue(
                repo.createProfile("Extra", ProfileCreationMode.BLANK, "en") is CreateProfileResult.LimitReached,
            )

            // If another import replaces files without restored_3 while locked, keep starter usable and preserve locked choice
            profilesDir.listFiles()?.forEach { it.delete() }
            listOf(
                UserProfile("default", "Default", "he", emptyMap()),
                UserProfile("other_1", "Other 1", "en", emptyMap()),
                UserProfile("other_2", "Other 2", "he", emptyMap()),
            ).forEach { profile ->
                File(profilesDir, "profile_${profile.profileId}.json").writeText(
                    json.encodeToString(profile),
                )
            }
            repo.reload()
            assertEquals("default", repo.activeProfile.value?.profileId)
            assertEquals(setOf("default"), repo.profileAvailability.value.availableProfileIds)
            assertEquals(setOf("other_1", "other_2"), repo.profileAvailability.value.archivedProfileIds)
            assertTrue(repo.profileAvailability.value.isFreeSlotLocked)
            assertFalse(repo.profileAvailability.value.canChooseFreeProfile)
            assertEquals("restored_3", repo.profileAvailability.value.selectedCustomProfileId)
            assertFalse(repo.switchProfile("other_1"))
            assertEquals(SelectFreeCustomProfileResult.Locked, repo.confirmFreeCustomProfile("other_1"))

            // Upgrading/restoring Family unlocks all profiles without another import and clears lock
            entitlementProvider.setFamilyEntitlementActive(true)
            testScheduler.advanceUntilIdle()
            assertFalse(repo.profileAvailability.value.isFreeSlotLocked)
            assertEquals(3, repo.profileAvailability.value.availableProfiles.size)
            assertTrue(repo.profileAvailability.value.archivedProfiles.isEmpty())
            assertTrue(repo.switchProfile("other_2"))
            assertEquals("other_2", repo.activeProfile.value?.profileId)
            scope.cancel()
        }

    @Test
    fun `profileManagerViewModel exposes Free count explains limit blocks third and handles upgrade`() =
        runTest(testDispatcher) {
            val entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = false,
                    isProfileCapEnforced = true,
                ),
            )
            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)
            val aacRepo = mock(AACRepository::class.java)
            repo.reload()

            val vm = ProfileManagerViewModel(repo, aacRepo, entitlementProvider)
            val stateJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect()
            }
            val effects = mutableListOf<ProfileManagerEffect>()
            val effectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.effects.collect { effects += it }
            }
            testScheduler.advanceUntilIdle()

            assertEquals(1, vm.state.value.savedProfileCount)
            assertEquals(2, vm.state.value.freeProfileLimit)
            assertFalse(vm.state.value.isFreeLimitReached)

            fun awaitState(predicate: (com.kon.myaacapp.ui.profile.ProfileManagerState) -> Boolean) {
                val deadline = System.currentTimeMillis() + 5_000L
                while (!predicate(vm.state.value)) {
                    testScheduler.advanceUntilIdle()
                    if (predicate(vm.state.value)) return
                    if (System.currentTimeMillis() >= deadline) {
                        error("Timed out waiting for expected ViewModel state: ${vm.state.value}")
                    }
                    Thread.sleep(10)
                }
            }

            // Create 2nd profile through ViewModel
            vm.onAction(ProfileManagerAction.OpenCreateDialog)
            awaitState { it.showCreateDialog }
            vm.onAction(ProfileManagerAction.ProfileNameChanged("Maya"))
            vm.onAction(ProfileManagerAction.BoardLanguageChanged("en"))
            vm.onAction(ProfileManagerAction.CreateProfileClicked)
            awaitState { it.savedProfileCount == 2 && !it.isCreatingProfile }

            assertEquals(2, vm.state.value.savedProfileCount)
            assertTrue(vm.state.value.isFreeLimitReached)
            assertFalse(vm.state.value.canCreateAnotherProfile)
            assertTrue(ProfileManagerEffect.ProfileCreated in effects)

            // Trying to open create dialog at 2/2 opens limit explanation instead
            vm.onAction(ProfileManagerAction.OpenCreateDialog)
            awaitState { it.showLimitDialog }
            assertFalse(vm.state.value.showCreateDialog)
            assertTrue(vm.state.value.showLimitDialog)

            // Upgrade while billing unavailable surfaces honest notice without blocking
            vm.onAction(ProfileManagerAction.UpgradeToFamilyClicked)
            awaitState { it.showBillingUnavailableNotice && !it.isUpdatingEntitlement }
            assertTrue(vm.state.value.showBillingUnavailableNotice)
            assertFalse(vm.state.value.hasFamilyEntitlement)

            // Configure test billing & upgrade -> Family active, limit dialog closes, creation works
            entitlementProvider.setBillingConfigured(true)
            vm.onAction(ProfileManagerAction.UpgradeToFamilyClicked)
            awaitState { it.hasFamilyEntitlement && !it.showLimitDialog && !it.isUpdatingEntitlement }
            assertTrue(vm.state.value.hasFamilyEntitlement)
            assertFalse(vm.state.value.showLimitDialog)
            assertTrue(vm.state.value.canCreateAnotherProfile)

            vm.onAction(ProfileManagerAction.OpenCreateDialog)
            awaitState { it.showCreateDialog }
            vm.onAction(ProfileManagerAction.ProfileNameChanged("Third Family"))
            vm.onAction(ProfileManagerAction.CreateProfileClicked)
            awaitState { it.savedProfileCount == 3 && !it.isCreatingProfile }
            assertEquals(3, vm.state.value.savedProfileCount)

            // Race fallback: simulate dialog open when entitlement expires -> CreateProfileClicked closes dialog & shows limit
            vm.onAction(ProfileManagerAction.OpenCreateDialog)
            awaitState { it.showCreateDialog }
            vm.onAction(ProfileManagerAction.ProfileNameChanged("Raced Fourth"))
            entitlementProvider.setFamilyEntitlementActive(false)
            awaitState { !it.hasFamilyEntitlement }
            vm.onAction(ProfileManagerAction.CreateProfileClicked)
            awaitState { it.showLimitDialog && !it.showCreateDialog && !it.isCreatingProfile }
            assertFalse(vm.state.value.showCreateDialog)
            assertTrue(vm.state.value.showLimitDialog)
            assertEquals(3, vm.state.value.savedProfileCount)
            assertEquals(2, vm.state.value.availableProfileCount)
            assertEquals(1, vm.state.value.archivedProfileCount)

            // A draft leaves the repository and current communication board untouched.
            val previousCustom = vm.state.value.pendingFreeCustomProfile!!
            val archivedProfile = vm.state.value.archivedProfiles.single()
            assertTrue(vm.state.value.canChooseFreeProfile)
            assertFalse(vm.state.value.isFreeSlotLocked)
            effects.clear()
            vm.onAction(ProfileManagerAction.SelectFreeCustomProfileClicked(archivedProfile.profileId))
            awaitState { it.draftFreeProfileId == archivedProfile.profileId }
            assertEquals(archivedProfile.profileId, vm.state.value.pendingFreeCustomProfile?.profileId)
            assertEquals(previousCustom.profileId, repo.activeProfile.value?.profileId)
            assertEquals(setOf("default", previousCustom.profileId), repo.profileAvailability.value.availableProfileIds)
            assertFalse(repo.switchProfile(archivedProfile.profileId))
            repo.reload()
            assertEquals(previousCustom.profileId, repo.activeProfile.value?.profileId)
            assertFalse(ProfileManagerEffect.ProfileSwitched in effects)

            vm.onAction(ProfileManagerAction.DiscardFreeProfileChoice)
            awaitState { it.draftFreeProfileId == null }
            assertEquals(previousCustom.profileId, vm.state.value.pendingFreeCustomProfile?.profileId)

            vm.onAction(ProfileManagerAction.SelectFreeCustomProfileClicked(archivedProfile.profileId))
            awaitState { it.draftFreeProfileId == archivedProfile.profileId }
            vm.onAction(ProfileManagerAction.OpenConfirmFreeProfileDialog)
            awaitState { it.showConfirmFreeProfileDialog }
            vm.onAction(ProfileManagerAction.ConfirmFreeCustomProfileClicked(archivedProfile.profileId))
            awaitState {
                !it.isSwitchingProfile &&
                    it.isFreeSlotLocked &&
                    !it.showConfirmFreeProfileDialog &&
                    !it.canChooseFreeProfile
            }

            // Further selection or switching to the archived profile is rejected once locked
            effects.clear()
            vm.onAction(ProfileManagerAction.SelectFreeCustomProfileClicked(previousCustom.profileId))
            awaitState { it.errorMessage != null }
            testScheduler.advanceUntilIdle()
            assertTrue(effects.any { it is ProfileManagerEffect.ShowError })
            vm.onAction(ProfileManagerAction.ErrorConsumed)

            effects.clear()
            vm.onAction(ProfileManagerAction.SwitchProfileClicked(previousCustom.profileId))
            awaitState { it.errorMessage != null }
            testScheduler.advanceUntilIdle()
            assertTrue(effects.any { it is ProfileManagerEffect.ShowError })
            vm.onAction(ProfileManagerAction.ErrorConsumed)

            // Restoring Family makes all 3 available again and unlocks slot
            entitlementProvider.setFamilyEntitlementActive(true)
            awaitState { it.hasFamilyEntitlement && it.archivedProfileCount == 0 && !it.isFreeSlotLocked }
            assertEquals(3, vm.state.value.availableProfileCount)

            stateJob.cancel()
            effectJob.cancel()
            scope.cancel()
        }

    @Test
    fun `profileManagerViewModel handles pending cancel no-purchase and manage subscription`() =
        runTest(testDispatcher) {
            var nextUpgradeOutcome: FamilyUpgradeOutcome = FamilyUpgradeOutcome.Pending
            var nextRestoreOutcome: FamilyUpgradeOutcome = FamilyUpgradeOutcome.NoPurchaseFound
            lateinit var entitlementProvider: ConfigurableFamilyEntitlementProvider
            entitlementProvider = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                    offerDetails = com.kon.myaacapp.domain.entitlement.FamilyPlanOfferDetails(
                        productId = "family_subscription",
                        basePlanId = "monthly",
                        offerToken = "tok",
                        formattedPrice = "$4.99",
                        billingPeriodIso8601 = "P1M",
                    ),
                ),
                onUpgradeRequested = {
                    if (nextUpgradeOutcome == FamilyUpgradeOutcome.Pending) {
                        entitlementProvider.updateState {
                            it.copy(
                                hasPendingPurchase = true,
                                subscriptionState = com.kon.myaacapp.domain.entitlement.SubscriptionBillingState.PENDING_PURCHASE,
                                lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.PURCHASE_PENDING,
                            )
                        }
                    } else if (nextUpgradeOutcome == FamilyUpgradeOutcome.UserCanceled) {
                        entitlementProvider.updateState {
                            it.copy(
                                lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.CHECKOUT_CANCELED,
                            )
                        }
                    }
                    nextUpgradeOutcome
                },
                onRestoreRequested = { nextRestoreOutcome },
            )

            val scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val settings = SettingsRepository(context, InMemoryDataStore())
            val repo = ProfileRepository(context, settings, scope, entitlementProvider)
            val aacRepo = mock(AACRepository::class.java)
            repo.reload()

            val vm = ProfileManagerViewModel(repo, aacRepo, entitlementProvider)
            val stateJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
            val effects = mutableListOf<ProfileManagerEffect>()
            val effectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.effects.collect { effects += it }
            }
            testScheduler.advanceUntilIdle()

            fun awaitState(predicate: (com.kon.myaacapp.ui.profile.ProfileManagerState) -> Boolean) {
                val deadline = System.currentTimeMillis() + 5_000L
                while (!predicate(vm.state.value)) {
                    testScheduler.advanceUntilIdle()
                    if (predicate(vm.state.value)) return
                    if (System.currentTimeMillis() >= deadline) {
                        error("Timed out waiting for expected ViewModel state: ${vm.state.value}")
                    }
                    Thread.sleep(10)
                }
            }

            assertEquals("$4.99", vm.state.value.offerDetails?.formattedPrice)

            vm.onAction(ProfileManagerAction.UpgradeToFamilyClicked)
            awaitState { it.hasPendingPurchase && !it.isUpdatingEntitlement }
            assertFalse(vm.state.value.hasFamilyEntitlement)

            nextUpgradeOutcome = FamilyUpgradeOutcome.UserCanceled
            vm.onAction(ProfileManagerAction.UpgradeToFamilyClicked)
            awaitState {
                it.lastBillingFeedback ==
                    com.kon.myaacapp.domain.entitlement.BillingFeedback.CHECKOUT_CANCELED &&
                    !it.isUpdatingEntitlement
            }
            assertFalse(vm.state.value.hasFamilyEntitlement)

            vm.onAction(ProfileManagerAction.RestoreFamilyPurchaseClicked)
            awaitState { !it.isUpdatingEntitlement }
            assertFalse(vm.state.value.hasFamilyEntitlement)

            nextRestoreOutcome = FamilyUpgradeOutcome.Activated
            entitlementProvider.setFamilyEntitlementActive(true)
            awaitState { it.hasFamilyEntitlement }

            vm.onAction(ProfileManagerAction.ManageSubscriptionClicked)
            testScheduler.advanceUntilIdle()
            assertTrue(effects.any { it is ProfileManagerEffect.OpenSubscriptionManagement })

            stateJob.cancel()
            effectJob.cancel()
            scope.cancel()
        }

    private fun resolveAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate asset file $name from ${File(".").absolutePath}")
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
}
