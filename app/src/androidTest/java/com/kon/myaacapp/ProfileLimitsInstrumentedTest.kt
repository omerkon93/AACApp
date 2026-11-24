package com.kon.myaacapp

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.local.AACDatabase
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.CreateProfileResult
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SelectFreeCustomProfileResult
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.entitlement.ConfigurableFamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementState
import com.kon.myaacapp.domain.entitlement.ProfilePlanTier
import com.kon.myaacapp.domain.entitlement.StoreFamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState
import com.kon.myaacapp.domain.model.CreateTileRequest
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.domain.service.AppStartupCoordinator
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.usecase.tile.IncrementTileClickUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveTilesUseCase
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.service.audio.TilePlaybackPath
import com.kon.myaacapp.service.backup.BackupService
import com.kon.myaacapp.ui.communication.CommunicationSessionController
import com.kon.myaacapp.ui.communication.CommunicationViewModel
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
import com.kon.myaacapp.ui.profile.ProfileManagerScreen
import com.kon.myaacapp.ui.profile.ProfileManagerViewModel
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileLimitsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var targetContext: Context
    private lateinit var application: Application
    private lateinit var testRootDir: File
    private val createdStacks = mutableListOf<IsolatedStack>()

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        application = targetContext.applicationContext as Application
        testRootDir = File(
            targetContext.cacheDir,
            "profile_limits_test_${UUID.randomUUID()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        createdStacks.forEach { it.dispose(targetContext) }
        createdStacks.clear()
        testRootDir.deleteRecursively()
    }

    private fun createStack(
        userTag: String,
        entitlementProvider: FamilyEntitlementProvider,
    ): IsolatedStack {
        val runId = UUID.randomUUID().toString().take(8)
        val root = File(testRootDir, "user_${userTag}_$runId").apply { mkdirs() }
        val dbName = "limits_${userTag}_$runId.db"
        return openStackAt(root, dbName, entitlementProvider)
    }

    private fun openStackAt(
        root: File,
        dbName: String,
        entitlementProvider: FamilyEntitlementProvider,
    ): IsolatedStack {
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val dataStoreFile = File(root, "datastore/settings.preferences_pb")
        val context = IsolatedAppContext(targetContext, filesDir, cacheDir)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
        val database = Room.databaseBuilder(
            targetContext,
            AACDatabase::class.java,
            dbName,
        ).build()
        val settingsRepository = SettingsRepository(context, dataStore)
        val profileRepository = ProfileRepository(
            context = context,
            settingsRepository = settingsRepository,
            scope = scope,
            familyEntitlementProvider = entitlementProvider,
        )
        val aacRepository = AACRepository(
            aacTileDao = database.aacTileDao(),
            context = context,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )
        val backupService = BackupService(
            context = context,
            repository = aacRepository,
            database = database,
            settingsRepository = settingsRepository,
        )
        val backupManager = BackupManager(
            application = application,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            scope = scope,
        )
        val coordinator = AppStartupCoordinator(
            context = context,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
        )

        return IsolatedStack(
            rootDir = root,
            filesDir = filesDir,
            dbName = dbName,
            context = context,
            scope = scope,
            database = database,
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            aacRepository = aacRepository,
            backupService = backupService,
            backupManager = backupManager,
            coordinator = coordinator,
        ).also { createdStacks += it }
    }

    @Test
    fun freeLimit_familyUpgrade_expiryPreservesBoardsAndOfflineSpeech_andDeleteRestoresCreation() {
        val entitlementProvider = ConfigurableFamilyEntitlementProvider(
            FamilyEntitlementState(
                tier = ProfilePlanTier.FREE,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val stack = createStack("free_family_lifecycle", entitlementProvider)
        lateinit var profileVm: ProfileManagerViewModel
        lateinit var commVm: CommunicationViewModel

        runBlocking {
            assertTrue(stack.coordinator.initialize())
            stack.settingsRepository.updateInterfaceLanguageCode("he")
            stack.settingsRepository.updateTilesUseTts(true)
            stack.settingsRepository.updateSpeakOnTilePress(true)

            profileVm = ProfileManagerViewModel(
                profileRepository = stack.profileRepository,
                aacRepository = stack.aacRepository,
                familyEntitlementProvider = entitlementProvider,
            )
            commVm = CommunicationViewModel(
                application = application,
                settingsRepository = stack.settingsRepository,
                observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
                incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
                communicationSessionController = CommunicationSessionController(),
                audioService = AudioRecordingService(stack.context),
                profileRepository = stack.profileRepository,
            )
        }

        val uiLangState = MutableStateFlow("he")
        val showBoardState = MutableStateFlow(false)

        composeRule.setContent {
            val uiLang by uiLangState.collectAsState()
            val showBoard by showBoardState.collectAsState()
            val wrappedContext = LocaleHelper.wrap(targetContext, uiLang)
            val uiDirection = if (uiLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            val profileState by profileVm.state.collectAsState()
            val commState by commVm.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalResources provides wrappedContext.resources,
                    LocalConfiguration provides wrappedContext.resources.configuration,
                    LocalLayoutDirection provides uiDirection,
                ) {
                    if (showBoard) {
                        MainCommunicationScreen(
                            state = commState,
                            onAction = commVm::onAction,
                        )
                    } else {
                        ProfileManagerScreen(
                            state = profileState,
                            onAction = profileVm::onAction,
                            onNavigateBack = {},
                        )
                    }
                }
            }
        }

        val heContext = LocaleHelper.wrap(targetContext, "he")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(
                heContext.getString(R.string.profile_plan_free_count, 1, 2),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Create 2nd profile ("Maya EN") via UI
        composeRule.onNodeWithContentDescription(heContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(heContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Maya EN")
        composeRule.onNodeWithText(heContext.getString(R.string.profile_mode_default))
            .performScrollTo()
            .performClick()
        composeRule.onNode(
            hasText(heContext.getString(R.string.english)) and
                hasClickAction() and
                hasAnyAncestor(isDialog()),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(heContext.getString(R.string.add)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            stack.profileRepository.profiles.value.size == 2
        }
        composeRule.onNodeWithText(heContext.getString(R.string.profile_plan_free_count, 2, 2))
            .assertIsDisplayed()
        composeRule.onNodeWithText(heContext.getString(R.string.profile_plan_limit_reached_title))
            .assertIsDisplayed()

        // Tapping '+' at 2/2 opens limit explanation dialog instead of creation dialog
        composeRule.onNodeWithContentDescription(heContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(heContext.getString(R.string.close))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithText(heContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes().isEmpty(),
        )

        // Direct duplicate / blank repository calls are also blocked at 2/2
        runBlocking {
            assertTrue(
                stack.profileRepository.createProfile(
                    name = "Blocked Duplicate",
                    creationMode = ProfileCreationMode.DUPLICATE_CURRENT,
                    boardLanguageCode = "en",
                ) is CreateProfileResult.LimitReached,
            )
        }

        // Caregiver upgrades to Family inside the limit dialog
        composeRule.onNode(
            hasText(heContext.getString(R.string.profile_upgrade_to_family)) and
                hasAnyAncestor(isDialog()),
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(heContext.getString(R.string.profile_plan_family_active))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Create 3rd profile ("Dana HE") as Family
        composeRule.onNodeWithContentDescription(heContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(heContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Dana HE")
        composeRule.onNodeWithText(heContext.getString(R.string.profile_mode_default))
            .performScrollTo()
            .performClick()
        composeRule.onNode(
            hasText(heContext.getString(R.string.hebrew)) and
                hasClickAction() and
                hasAnyAncestor(isDialog()),
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(heContext.getString(R.string.add)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            stack.profileRepository.profiles.value.size == 3
        }

        // Expire Family back to Free and verify English localization + usability of all 3 profiles
        entitlementProvider.setFamilyEntitlementActive(false)
        uiLangState.value = "en"
        composeRule.waitForIdle()
        val enContext = LocaleHelper.wrap(targetContext, "en")

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_free_over_limit_count, 3, 2, 1),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(enContext.getString(R.string.profile_plan_limit_reached_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_plan_family_downgrade_disclosure, 2),
        ).assertIsDisplayed()

        // Active custom profile ("Dana HE") + Starter stay available; "Maya EN" is archived
        val mayaId = stack.profileRepository.profiles.value.first { it.profileName == "Maya EN" }.profileId
        val danaId = stack.profileRepository.profiles.value.first { it.profileName == "Dana HE" }.profileId
        assertEquals(setOf("default", danaId), stack.profileRepository.profileAvailability.value.availableProfileIds)
        assertEquals(setOf(mayaId), stack.profileRepository.profileAvailability.value.archivedProfileIds)

        // Verify 3rd profile ("Dana HE") opens, speaks Hebrew TTS, edits, and exports backup after expiry
        showBoardState.value = true
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("זמן ושגרה").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("זמן ושגרה").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("התארגנות בוקר").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("התארגנות בוקר").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("לשטוף פנים").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("לשטוף פנים").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            commVm.lastRequestedTtsLocale.value == Locale.forLanguageTag("he-IL")
        }

        runBlocking {
            val profiles = stack.profileRepository.profiles.value
            val mayaProfile = profiles.first { it.profileName == "Maya EN" }
            val danaProfile = profiles.first { it.profileName == "Dana HE" }

            // Edit Dana HE after expiry
            val updatedDana = danaProfile.copy(
                layout = danaProfile.layout + (
                    "custom_test_yes" to TileLayoutState(
                        tileId = "yes",
                        parentId = null,
                        cellIndex = 99,
                    )
                ),
            )
            stack.profileRepository.updateActiveProfile(updatedDana)
            assertTrue(
                stack.profileRepository.activeProfile.value!!.layout.containsKey("custom_test_yes"),
            )

            // Export full backup with all 3 profiles (including archived Maya EN)
            val backupZip = File(testRootDir, "post_expiry_3_profiles.zip")
            stack.backupManager.exportDatabase(
                uri = Uri.fromFile(backupZip),
                contentResolver = targetContext.contentResolver,
            )
            val exportResult = stack.backupManager.result.filterNotNull().first()
            assertTrue(exportResult.isSuccess)
            assertTrue(backupZip.length() > 0L)

            // Direct switch to archived Maya EN is blocked until caregiver swaps it in
            assertFalse(stack.profileRepository.switchProfile(mayaProfile.profileId))
        }

        // Caregiver drafts Maya EN in Profile Manager, then confirms once.
        showBoardState.value = false
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_free_choice_title),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(enContext.getString(R.string.profile_make_available)),
        )
        composeRule.onNodeWithText(enContext.getString(R.string.profile_make_available))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            profileVm.state.value.pendingFreeCustomProfile?.profileName == "Maya EN"
        }

        // Confirm the one-time Free custom profile choice ("Maya EN") via the confirmation dialog
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(enContext.getString(R.string.profile_free_choice_confirm_button, "Maya EN")),
        )
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_free_choice_confirm_button, "Maya EN"),
        ).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_free_choice_dialog_title),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_free_choice_confirm_action),
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            stack.profileRepository.profileAvailability.value.isFreeSlotLocked
        }
        assertTrue(
            composeRule.onAllNodesWithText(enContext.getString(R.string.profile_make_available))
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(enContext.getString(R.string.profile_status_available_with_family)),
        )
        composeRule.onNodeWithText(enContext.getString(R.string.profile_status_available_with_family))
            .assertIsDisplayed()

        // Verify Maya EN English communication works after swap
        showBoardState.value = true
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Time and routines").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Time and routines").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Morning routine").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Morning routine").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Wash face").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Wash face").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            commVm.lastRequestedTtsLocale.value == Locale.forLanguageTag("en")
        }

        // Switch back to Profile Manager, switch active to default, and delete Maya EN & Dana HE
        showBoardState.value = false
        runBlocking {
            assertTrue(stack.profileRepository.switchProfile("default"))
        }
        composeRule.waitForIdle()

        // Delete 1st non-default profile -> 2 remain -> creation still blocked
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(enContext.getString(R.string.delete_profile)),
        )
        composeRule.onAllNodes(
            hasContentDescription(enContext.getString(R.string.delete_profile)),
        )[0].performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(enContext.getString(R.string.delete))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(enContext.getString(R.string.delete)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            stack.profileRepository.profiles.value.size == 2
        }
        composeRule.onNodeWithText(enContext.getString(R.string.profile_plan_limit_reached_title))
            .assertIsDisplayed()

        // Delete 2nd non-default profile -> 1 remains -> Free creation unlocked again
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(enContext.getString(R.string.delete_profile)),
        )
        composeRule.onNodeWithContentDescription(enContext.getString(R.string.delete_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(enContext.getString(R.string.delete))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(enContext.getString(R.string.delete)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            stack.profileRepository.profiles.value.size == 1
        }
        composeRule.onNodeWithText(enContext.getString(R.string.profile_plan_free_count, 1, 2))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(enContext.getString(R.string.create_new_profile))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(enContext.getString(R.string.enter_profile_name))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun freeInstallationCanRestoreBackupWithMoreThanTwoProfilesIncludingImagesAndRecordings() =
        runBlocking {
            val sourceEntitlement = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FAMILY,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )
            val freeDestEntitlement = ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FREE,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            )

            val sourceStack = createStack("backup_source_3_profiles", sourceEntitlement)
            val destStack = createStack("free_dest_3_profiles", freeDestEntitlement)

            assertTrue(sourceStack.coordinator.initialize())
            assertTrue(sourceStack.profileRepository.createProfile("Child EN", ProfileCreationMode.DEFAULT_TEMPLATE, "en") is CreateProfileResult.Success)
            assertTrue(sourceStack.profileRepository.createProfile("Child HE 2", ProfileCreationMode.BLANK, "he") is CreateProfileResult.Success)
            sourceStack.aacRepository.reload()

            val customImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
            val customImageFile = File(sourceStack.filesDir, "images/custom_snack.png").apply {
                parentFile?.mkdirs()
                writeBytes(customImageBytes)
            }
            val customWavBytes = createTestWavBytes()
            val customAudioFile = File(sourceStack.filesDir, "audio_tiles/he/custom_snack.wav").apply {
                parentFile?.mkdirs()
                writeBytes(customWavBytes)
            }

            sourceStack.aacRepository.addTile(
                CreateTileRequest(
                    id = "custom_snack_test",
                    label = "פרי טעים",
                    ttsText = "אני רוצה פרי",
                    imageUri = customImageFile.absolutePath,
                    audioUri = customAudioFile.absolutePath,
                    isCustomAudio = true,
                    tileType = TileType.BASIC,
                    languageCode = "he",
                    cellIndex = 0,
                ),
            )

            val backupZip = File(testRootDir, "three_profiles_with_media.zip")
            sourceStack.backupManager.exportDatabase(
                uri = Uri.fromFile(backupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(sourceStack.backupManager.result.filterNotNull().first().isSuccess)

            // Restore into a Free installation
            assertTrue(destStack.coordinator.initialize())
            assertEquals(1, destStack.profileRepository.profiles.value.size)

            destStack.backupManager.importDatabase(
                uri = Uri.fromFile(backupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(destStack.backupManager.result.filterNotNull().first().isSuccess)

            // All 3 profiles + images + recordings are restored; 2 available & 1 archived on Free
            val restoredProfiles = destStack.profileRepository.profiles.value
            assertEquals(3, restoredProfiles.size)
            assertEquals(2, destStack.profileRepository.profileAvailability.value.availableProfiles.size)
            assertEquals(1, destStack.profileRepository.profileAvailability.value.archivedProfiles.size)

            val restoredSnack = destStack.aacRepository.baseDefinitions.value.firstOrNull {
                it.id == "custom_snack_test" && it.languageCode == "he"
            }
            assertNotNull(restoredSnack)
            val restoredImg = File(restoredSnack!!.imageUri!!)
            val restoredWav = File(restoredSnack.audioUri!!)
            assertTrue(restoredImg.isFile)
            assertTrue(restoredWav.isFile)
            assertArrayEquals(customImageBytes, restoredImg.readBytes())
            assertArrayEquals(customWavBytes, restoredWav.readBytes())

            // Archived profile must be selected/confirmed before switching; fresh Free install chooses once
            val childEn = restoredProfiles.first { it.profileName == "Child EN" }
            assertTrue(destStack.profileRepository.profileAvailability.value.canChooseFreeProfile)
            assertFalse(destStack.profileRepository.switchProfile(childEn.profileId))
            val confirmResult = destStack.profileRepository.confirmFreeCustomProfile(childEn.profileId)
            assertTrue(confirmResult is SelectFreeCustomProfileResult.Success)
            assertTrue(destStack.profileRepository.profileAvailability.value.isFreeSlotLocked)
            assertEquals("en", destStack.profileRepository.activeProfile.value?.activeLanguageCode)
            val childHe2 = restoredProfiles.first { it.profileName == "Child HE 2" }
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                destStack.profileRepository.confirmFreeCustomProfile(childHe2.profileId),
            )
            assertTrue(
                destStack.profileRepository.createProfile("New Blocked", ProfileCreationMode.BLANK, "en")
                    is CreateProfileResult.LimitReached,
            )
        }

    @Test
    fun productionDefaultUnconfiguredBillingDoesNotBlockProfileCreationOrOfflineCommunication() =
        runBlocking {
            val prodProvider = StoreFamilyEntitlementProvider()
            val stack = createStack("prod_unconfigured_billing", prodProvider)
            assertTrue(stack.coordinator.initialize())

            assertFalse(prodProvider.currentEntitlement().isBillingConfigured)
            assertFalse(prodProvider.currentEntitlement().isProfileCapEnforced)
            assertTrue(stack.profileRepository.createProfile("Second", ProfileCreationMode.DEFAULT_TEMPLATE, "en") is CreateProfileResult.Success)
            assertTrue(stack.profileRepository.createProfile("Third", ProfileCreationMode.DEFAULT_TEMPLATE, "he") is CreateProfileResult.Success)
            assertEquals(3, stack.profileRepository.profiles.value.size)
        }

    @Test
    fun profileManagerDisplaysLocalizedOfferPendingCanceledAndExpiredStatesInHebrewAndEnglish() {
        val entitlementProvider = ConfigurableFamilyEntitlementProvider(
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
                    freeTrialPeriodIso8601 = "P7D",
                ),
            ),
        )
        val stack = createStack("billing_ui_states", entitlementProvider)
        lateinit var profileVm: ProfileManagerViewModel

        runBlocking {
            assertTrue(stack.coordinator.initialize())
            profileVm = ProfileManagerViewModel(
                profileRepository = stack.profileRepository,
                aacRepository = stack.aacRepository,
                familyEntitlementProvider = entitlementProvider,
            )
        }

        val uiLangState = MutableStateFlow("en")
        composeRule.setContent {
            val uiLang by uiLangState.collectAsState()
            val wrappedContext = LocaleHelper.wrap(targetContext, uiLang)
            val uiDirection = if (uiLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            val profileState by profileVm.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalResources provides wrappedContext.resources,
                    LocalConfiguration provides wrappedContext.resources.configuration,
                    LocalLayoutDirection provides uiDirection,
                ) {
                    ProfileManagerScreen(
                        state = profileState,
                        onAction = profileVm::onAction,
                        onNavigateBack = {},
                    )
                }
            }
        }

        val enContext = LocaleHelper.wrap(targetContext, "en")
        val expectedEnPrice = enContext.getString(
            R.string.profile_plan_price_period,
            "$4.99",
            enContext.getString(R.string.profile_period_month),
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_price_summary, expectedEnPrice),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            enContext.getString(
                R.string.profile_plan_free_trial_note,
                enContext.getString(R.string.profile_period_days, 7),
            ),
        ).assertIsDisplayed()

        // Pending purchase leaves Family inactive and shows pending notice
        entitlementProvider.updateState {
            it.copy(
                tier = ProfilePlanTier.FREE,
                subscriptionState = SubscriptionBillingState.PENDING_PURCHASE,
                hasPendingPurchase = true,
                lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.PURCHASE_PENDING,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_billing_feedback_pending),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(profileVm.state.value.hasFamilyEntitlement)

        // Canceled before expiry keeps Family active and offers Manage subscription
        entitlementProvider.updateState {
            it.copy(
                tier = ProfilePlanTier.FAMILY,
                subscriptionState = SubscriptionBillingState.CANCELED_PENDING_EXPIRY,
                isAutoRenewing = false,
                hasPendingPurchase = false,
                lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.NONE,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_family_canceled_active_title),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(enContext.getString(R.string.profile_manage_subscription))
            .assertIsDisplayed()

        // Switch UI to Hebrew and verify expired state notice
        uiLangState.value = "he"
        entitlementProvider.setFamilyEntitlementActive(false)
        val heContext = LocaleHelper.wrap(targetContext, "he")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                heContext.getString(R.string.profile_plan_family_expired_notice, 2),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun backupFromFamilyDoesNotUnlockFamilyOnDifferentPlayAccountAndDistinguishesNoPurchaseFromConnectionError() {
        val sourceFamilyProvider = ConfigurableFamilyEntitlementProvider(
            FamilyEntitlementState(
                tier = ProfilePlanTier.FAMILY,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val sourceStack = createStack("backup_family_source", sourceFamilyProvider)
        val backupZip = File(testRootDir, "family_account_backup.zip")

        runBlocking {
            assertTrue(sourceStack.coordinator.initialize())
            assertTrue(
                sourceStack.profileRepository.createProfile(
                    "Child Two",
                    ProfileCreationMode.DEFAULT_TEMPLATE,
                    "en",
                ) is CreateProfileResult.Success,
            )
            assertTrue(
                sourceStack.profileRepository.createProfile(
                    "Child Three",
                    ProfileCreationMode.BLANK,
                    "he",
                ) is CreateProfileResult.Success,
            )
            sourceStack.backupManager.exportDatabase(
                uri = Uri.fromFile(backupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(sourceStack.backupManager.result.filterNotNull().first().isSuccess)
        }

        // Verify ZIP contains zero entitlement/shared_prefs files
        java.util.zip.ZipFile(backupZip).use { zip ->
            val entryNames = zip.entries().toList().map { it.name.lowercase() }
            assertFalse(entryNames.any { it.contains("shared_prefs") || it.contains("entitlement") || it.contains("billing") })
        }

        // Destination installation belongs to a DIFFERENT Play account with no Family purchase
        var simulateConnectionFailure = false
        val noPurchasePlayProvider = ConfigurableFamilyEntitlementProvider(
            initialState = FamilyEntitlementState(
                tier = ProfilePlanTier.FREE,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val destProvider = object : FamilyEntitlementProvider by noPurchasePlayProvider {
            override suspend fun restoreFamilyPurchase(): com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome {
                return if (simulateConnectionFailure) {
                    noPurchasePlayProvider.updateState {
                        it.copy(
                            lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.TEMPORARY_ERROR,
                        )
                    }
                    com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome.Failed(
                        reason = "Offline",
                        feedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.TEMPORARY_ERROR,
                    )
                } else {
                    noPurchasePlayProvider.updateState {
                        it.copy(
                            lastBillingFeedback = com.kon.myaacapp.domain.entitlement.BillingFeedback.RESTORED_NO_ACTIVE_PURCHASE,
                        )
                    }
                    com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome.NoPurchaseFound
                }
            }
        }
        val destStack = createStack("different_play_account_dest", destProvider)
        lateinit var destVm: ProfileManagerViewModel

        runBlocking {
            assertTrue(destStack.coordinator.initialize())
            destStack.backupManager.importDatabase(
                uri = Uri.fromFile(backupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(destStack.backupManager.result.filterNotNull().first().isSuccess)
            assertEquals(3, destStack.profileRepository.profiles.value.size)
            assertFalse(destProvider.currentEntitlement().hasFamilyEntitlement)
            assertTrue(
                destStack.profileRepository.createProfile(
                    "Fourth Should Be Blocked",
                    ProfileCreationMode.BLANK,
                    "en",
                ) is CreateProfileResult.LimitReached,
            )
            destVm = ProfileManagerViewModel(
                profileRepository = destStack.profileRepository,
                aacRepository = destStack.aacRepository,
                familyEntitlementProvider = destProvider,
            )
        }

        val enContext = LocaleHelper.wrap(targetContext, "en")
        composeRule.setContent {
            val wrappedContext = LocaleHelper.wrap(targetContext, "en")
            val profileState by destVm.state.collectAsState()
            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalResources provides wrappedContext.resources,
                    LocalConfiguration provides wrappedContext.resources.configuration,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    ProfileManagerScreen(
                        state = profileState,
                        onAction = destVm::onAction,
                        onNavigateBack = {},
                    )
                }
            }
        }

        // Restore purchase with no Family on this Play account -> prompt to check Play account
        composeRule.onNodeWithText(enContext.getString(R.string.profile_restore_family_purchase))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_billing_feedback_no_purchase_found),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(destVm.state.value.hasFamilyEntitlement)

        // Distinguish from temporary connection failure
        simulateConnectionFailure = true
        composeRule.onNodeWithText(enContext.getString(R.string.profile_restore_family_purchase))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_billing_feedback_temporary_error),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_billing_feedback_no_purchase_found),
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun acceptance_familyWithFiveProfiles_cancelExpiryConfirmOnceRelaunchBackupAndRestoreFamily() {
        val entitlementProvider = ConfigurableFamilyEntitlementProvider(
            FamilyEntitlementState(
                tier = ProfilePlanTier.FAMILY,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val stack = createStack("acceptance_5_profiles", entitlementProvider)
        lateinit var mayaId: String
        lateinit var noaId: String
        lateinit var liamId: String
        lateinit var omerId: String

        val customImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 7, 8, 9, 10)
        val customWavBytes = createTestWavBytes()
        val fullFiveBackupZip = File(testRootDir, "acceptance_5_profiles_full_backup.zip")
        val partialWithoutOmerBackupZip = File(testRootDir, "acceptance_partial_without_omer.zip")

        runBlocking {
            assertTrue(stack.coordinator.initialize())
            stack.settingsRepository.updateInterfaceLanguageCode("en")
            stack.settingsRepository.updateTilesUseTts(true)
            stack.settingsRepository.updateSpeakOnTilePress(true)

            val mayaRes = stack.profileRepository.createProfile(
                "Maya EN",
                ProfileCreationMode.DEFAULT_TEMPLATE,
                "en",
            ) as CreateProfileResult.Success
            mayaId = mayaRes.profile.profileId

            val noaRes = stack.profileRepository.createProfile(
                "Noa HE",
                ProfileCreationMode.BLANK,
                "he",
            ) as CreateProfileResult.Success
            noaId = noaRes.profile.profileId

            val liamRes = stack.profileRepository.createProfile(
                "Liam EN",
                ProfileCreationMode.DEFAULT_TEMPLATE,
                "en",
            ) as CreateProfileResult.Success
            liamId = liamRes.profile.profileId

            // Create 5th profile ("Omer Mix") with a custom tile + PNG image + custom WAV recording
            val omerRes = stack.profileRepository.createProfile(
                "Omer Mix",
                ProfileCreationMode.DUPLICATE_CURRENT,
                "he",
            ) as CreateProfileResult.Success
            omerId = omerRes.profile.profileId
            stack.aacRepository.reload()

            val customImageFile = File(stack.filesDir, "images/omer_snack.png").apply {
                parentFile?.mkdirs()
                writeBytes(customImageBytes)
            }
            val customAudioFile = File(stack.filesDir, "audio_tiles/he/omer_snack.wav").apply {
                parentFile?.mkdirs()
                writeBytes(customWavBytes)
            }
            stack.aacRepository.addTile(
                CreateTileRequest(
                    id = "omer_custom_snack",
                    label = "תפוח מיוחד",
                    ttsText = "תפוח מיוחד",
                    imageUri = customImageFile.absolutePath,
                    audioUri = customAudioFile.absolutePath,
                    isCustomAudio = true,
                    tileType = TileType.BASIC,
                    languageCode = "he",
                    cellIndex = 0,
                ),
            )

            // Also build a separate 5-profile backup whose active profile is Noa HE (different from Omer Mix)
            // to prove importing a backup into a locked Free installation does not replace Omer Mix.
            assertTrue(stack.profileRepository.switchProfile(noaId))
            stack.backupManager.exportDatabase(
                uri = Uri.fromFile(fullFiveBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(stack.backupManager.result.filterNotNull().first().isSuccess)

            // Leave Maya EN active before cancellation/expiry
            assertTrue(stack.profileRepository.switchProfile(mayaId))
            assertEquals(5, stack.profileRepository.profiles.value.size)
            assertEquals(5, stack.profileRepository.profileAvailability.value.availableProfiles.size)
        }

        // Export a partial backup (without Omer Mix) from another Family stack to verify absent-choice import behavior
        val partialSourceStack = createStack(
            "acceptance_partial_source",
            ConfigurableFamilyEntitlementProvider(
                FamilyEntitlementState(
                    tier = ProfilePlanTier.FAMILY,
                    isBillingConfigured = true,
                    isProfileCapEnforced = true,
                ),
            ),
        )
        runBlocking {
            assertTrue(partialSourceStack.coordinator.initialize())
            assertTrue(
                partialSourceStack.profileRepository.createProfile(
                    "Guest One",
                    ProfileCreationMode.BLANK,
                    "en",
                ) is CreateProfileResult.Success,
            )
            assertTrue(
                partialSourceStack.profileRepository.createProfile(
                    "Guest Two",
                    ProfileCreationMode.BLANK,
                    "he",
                ) is CreateProfileResult.Success,
            )
            partialSourceStack.backupManager.exportDatabase(
                uri = Uri.fromFile(partialWithoutOmerBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(partialSourceStack.backupManager.result.filterNotNull().first().isSuccess)
        }

        val profileVm = ProfileManagerViewModel(
            profileRepository = stack.profileRepository,
            aacRepository = stack.aacRepository,
            familyEntitlementProvider = entitlementProvider,
        )
        val commVm = CommunicationViewModel(
            application = application,
            settingsRepository = stack.settingsRepository,
            observeTilesUseCase = ObserveTilesUseCase(stack.aacRepository),
            incrementTileClickUseCase = IncrementTileClickUseCase(stack.aacRepository),
            communicationSessionController = CommunicationSessionController(),
            audioService = AudioRecordingService(stack.context),
            profileRepository = stack.profileRepository,
        )

        val uiLangState = MutableStateFlow("en")
        val showBoardState = MutableStateFlow(false)
        val composeActive = MutableStateFlow(true)
        val activeProfileVmState = MutableStateFlow(profileVm)
        val activeCommVmState = MutableStateFlow(commVm)

        composeRule.setContent {
            val activeCompose by composeActive.collectAsState()
            if (!activeCompose) return@setContent
            val uiLang by uiLangState.collectAsState()
            val showBoard by showBoardState.collectAsState()
            val currentProfileVm by activeProfileVmState.collectAsState()
            val currentCommVm by activeCommVmState.collectAsState()
            val wrappedContext = LocaleHelper.wrap(targetContext, uiLang)
            val uiDirection = if (uiLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            val profileState by currentProfileVm.state.collectAsState()
            val commState by currentCommVm.state.collectAsState()

            MyAACAppTheme {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext,
                    LocalResources provides wrappedContext.resources,
                    LocalConfiguration provides wrappedContext.resources.configuration,
                    LocalLayoutDirection provides uiDirection,
                ) {
                    if (showBoard) {
                        MainCommunicationScreen(
                            state = commState,
                            onAction = currentCommVm::onAction,
                        )
                    } else {
                        ProfileManagerScreen(
                            state = profileState,
                            onAction = currentProfileVm::onAction,
                            onNavigateBack = {},
                        )
                    }
                }
            }
        }

        val enContext = LocaleHelper.wrap(targetContext, "en")
        val heContext = LocaleHelper.wrap(targetContext, "he")

        // 1. Cancel renewal -> all 5 stay usable until expiry (also resilient to cached/offline mode)
        entitlementProvider.updateState {
            it.copy(
                tier = ProfilePlanTier.FAMILY,
                subscriptionState = SubscriptionBillingState.CANCELED_PENDING_EXPIRY,
                isAutoRenewing = false,
                isCachedOfflineEntitlement = true,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_family_canceled_active_title),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_plan_family_canceled_active_body, 2),
        ).assertIsDisplayed()
        assertEquals(5, stack.profileRepository.profileAvailability.value.availableProfiles.size)
        assertEquals(0, stack.profileRepository.profileAvailability.value.archivedProfiles.size)
        runBlocking {
            assertTrue(stack.profileRepository.switchProfile(liamId))
            assertTrue(stack.profileRepository.switchProfile(mayaId))
        }

        // 2. Confirmed expiry -> child keeps using current board ("Maya EN") without forced dialog; 2 available, 3 archived
        showBoardState.value = true
        entitlementProvider.updateState {
            it.copy(
                tier = ProfilePlanTier.FREE,
                subscriptionState = SubscriptionBillingState.EXPIRED,
                isAutoRenewing = false,
                isCachedOfflineEntitlement = false,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            stack.profileRepository.profileAvailability.value.archivedProfiles.size == 3 &&
                composeRule.onAllNodesWithText("Time and routines").fetchSemanticsNodes().isNotEmpty()
        }
        // No forced selection dialog on the communication board
        assertTrue(
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_free_choice_dialog_title),
            ).fetchSemanticsNodes().isEmpty(),
        )
        assertEquals(
            setOf("default", mayaId),
            stack.profileRepository.profileAvailability.value.availableProfileIds,
        )
        assertEquals(
            setOf(noaId, liamId, omerId),
            stack.profileRepository.profileAvailability.value.archivedProfileIds,
        )

        // Open Profile Manager and verify Hebrew & English fixed-slot disclosures and pre-confirmation preview
        showBoardState.value = false
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_free_over_limit_count, 5, 2, 3),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_plan_family_downgrade_disclosure, 2),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_upgrade_to_family),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_restore_family_purchase),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_free_choice_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_free_choice_confirm_button, "Maya EN"),
        ).assertIsDisplayed()

        uiLangState.value = "he"
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                heContext.getString(R.string.profile_plan_family_downgrade_disclosure, 2),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            heContext.getString(R.string.profile_free_choice_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            heContext.getString(R.string.profile_free_choice_confirm_button, "Maya EN"),
        ).assertIsDisplayed()
        uiLangState.value = "en"
        composeRule.waitForIdle()

        // 3. Before confirming, pick "Omer Mix" from Archived section, then confirm once
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_plan_free_over_limit_count, 5, 2, 3),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(enContext.getString(R.string.profile_section_archived, 3)),
        )
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_section_archived, 3),
        ).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("make_available_$omerId"))
        composeRule.onNodeWithText("Omer Mix").assertIsDisplayed()
        composeRule.onNodeWithTag("make_available_$omerId").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            profileVm.state.value.draftFreeProfileId == omerId
        }
        assertEquals(mayaId, stack.profileRepository.activeProfile.value?.profileId)
        runBlocking { assertFalse(stack.profileRepository.switchProfile(omerId)) }

        composeRule.onNodeWithContentDescription(enContext.getString(R.string.back)).performClick()
        composeRule.onNodeWithText(enContext.getString(R.string.profile_free_choice_discard)).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) { profileVm.state.value.draftFreeProfileId == null }
        assertEquals(mayaId, stack.profileRepository.activeProfile.value?.profileId)
        assertFalse(stack.profileRepository.profileAvailability.value.isFreeSlotLocked)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("make_available_$omerId"))
        composeRule.onNodeWithTag("make_available_$omerId").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            profileVm.state.value.draftFreeProfileId == omerId
        }
        pressBack()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_free_choice_dialog_title),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            enContext.getString(
                R.string.profile_free_choice_dialog_body,
                enContext.getString(R.string.default_profile_name),
                "Omer Mix",
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            enContext.getString(R.string.profile_free_choice_confirm_action),
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            stack.profileRepository.profileAvailability.value.isFreeSlotLocked
        }
        assertFalse(stack.profileRepository.profileAvailability.value.canChooseFreeProfile)
        // "Make available" is removed from all archived profiles and "Available with Family" is shown
        assertTrue(
            composeRule.onAllNodesWithText(
                enContext.getString(R.string.profile_make_available),
            ).fetchSemanticsNodes().isEmpty(),
        )
        listOf(mayaId, noaId, liamId).forEach { archivedId ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(
                hasTestTag("profile_status_$archivedId"),
            )
            composeRule.onNodeWithTag("profile_status_$archivedId")
                .assertTextEquals(enContext.getString(R.string.profile_status_available_with_family))
                .assertIsDisplayed()
        }

        // Verify Free switches between starter and confirmed profile while the other 3 stay archived across repeated attempts
        runBlocking {
            assertTrue(stack.profileRepository.switchProfile("default"))
            assertEquals("default", stack.profileRepository.activeProfile.value?.profileId)
            assertTrue(stack.profileRepository.switchProfile(omerId))
            assertEquals(omerId, stack.profileRepository.activeProfile.value?.profileId)

            listOf(mayaId, noaId, liamId).forEach { archivedId ->
                assertFalse(stack.profileRepository.switchProfile(archivedId))
                assertEquals(
                    SelectFreeCustomProfileResult.Locked,
                    stack.profileRepository.confirmFreeCustomProfile(archivedId),
                )
            }
            assertEquals(
                setOf("default", omerId),
                stack.profileRepository.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(mayaId, noaId, liamId),
                stack.profileRepository.profileAvailability.value.archivedProfileIds,
            )
        }

        // 4. Simulate relaunch and verify locked choice, archived profiles, and offline media persist
        val savedRootDir = stack.rootDir
        val savedDbName = stack.dbName

        composeActive.value = false
        composeRule.waitForIdle()
        runBlocking {
            stack.closeWithoutDeleting()
        }
        createdStacks.remove(stack)

        val relaunchedEntitlement = ConfigurableFamilyEntitlementProvider(
            FamilyEntitlementState(
                tier = ProfilePlanTier.FREE,
                subscriptionState = SubscriptionBillingState.EXPIRED,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val relaunchedStack = openStackAt(savedRootDir, savedDbName, relaunchedEntitlement)
        lateinit var relaunchedCommVm: CommunicationViewModel
        runBlocking {
            assertTrue(relaunchedStack.coordinator.initialize())
            assertEquals(5, relaunchedStack.profileRepository.profiles.value.size)
            assertTrue(relaunchedStack.profileRepository.profileAvailability.value.isFreeSlotLocked)
            assertFalse(relaunchedStack.profileRepository.profileAvailability.value.canChooseFreeProfile)
            assertEquals(omerId, relaunchedStack.profileRepository.activeProfile.value?.profileId)
            assertEquals(
                setOf("default", omerId),
                relaunchedStack.profileRepository.profileAvailability.value.availableProfileIds,
            )
            assertEquals(
                setOf(mayaId, noaId, liamId),
                relaunchedStack.profileRepository.profileAvailability.value.archivedProfileIds,
            )

            // Repeated switch attempts remain blocked after relaunch
            assertFalse(relaunchedStack.profileRepository.switchProfile(mayaId))
            assertFalse(relaunchedStack.profileRepository.switchProfile(noaId))
            assertFalse(relaunchedStack.profileRepository.switchProfile(liamId))

            // Importing the 5-profile backup (which had Noa HE active) on this SAME locked installation
            // neither replaces Omer Mix nor grants another selection
            relaunchedStack.backupManager.importDatabase(
                uri = Uri.fromFile(fullFiveBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(relaunchedStack.backupManager.result.filterNotNull().first().isSuccess)
            relaunchedStack.profileRepository.profileAvailability.first {
                it.isFreeSlotLocked && it.availableProfileIds == setOf("default", omerId)
            }
            assertFalse(relaunchedStack.profileRepository.profileAvailability.value.canChooseFreeProfile)
            assertEquals(omerId, relaunchedStack.profileRepository.activeProfile.value?.profileId)
            assertEquals(
                setOf(mayaId, noaId, liamId),
                relaunchedStack.profileRepository.profileAvailability.value.archivedProfileIds,
            )
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                relaunchedStack.profileRepository.confirmFreeCustomProfile(noaId),
            )

            // Switch between starter and chosen profile still works after backup import
            assertTrue(relaunchedStack.profileRepository.switchProfile("default"))
            assertTrue(relaunchedStack.profileRepository.switchProfile(omerId))

            val persistedSnack = relaunchedStack.aacRepository.baseDefinitions.value.firstOrNull {
                it.id == "omer_custom_snack" && it.languageCode == "he"
            }
            assertNotNull(persistedSnack)
            assertArrayEquals(customImageBytes, File(persistedSnack!!.imageUri!!).readBytes())
            assertArrayEquals(customWavBytes, File(persistedSnack.audioUri!!).readBytes())

            relaunchedCommVm = CommunicationViewModel(
                application = application,
                settingsRepository = relaunchedStack.settingsRepository,
                observeTilesUseCase = ObserveTilesUseCase(relaunchedStack.aacRepository),
                incrementTileClickUseCase = IncrementTileClickUseCase(relaunchedStack.aacRepository),
                communicationSessionController = CommunicationSessionController(),
                audioService = AudioRecordingService(relaunchedStack.context),
                profileRepository = relaunchedStack.profileRepository,
            )
        }

        // Tap "Omer Mix"'s custom tile offline in UI and verify custom WAV recording plays
        activeCommVmState.value = relaunchedCommVm
        showBoardState.value = true
        composeActive.value = true
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("תפוח מיוחד").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("תפוח מיוחד").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            relaunchedCommVm.lastPlaybackEvent.value?.tileId == "omer_custom_snack"
        }
        val playbackEvent = relaunchedCommVm.lastPlaybackEvent.value!!
        assertEquals(TilePlaybackPath.AUDIO_RECORDING, playbackEvent.path)
        assertTrue(playbackEvent.isCustomAudio)
        assertTrue(playbackEvent.fileExists)
        assertFalse(playbackEvent.usedFallback)

        // Verify importing a backup WITHOUT Omer Mix on the SAME locked installation keeps starter usable & preserves locked choice
        runBlocking {
            relaunchedStack.backupManager.importDatabase(
                uri = Uri.fromFile(partialWithoutOmerBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(relaunchedStack.backupManager.result.filterNotNull().first().isSuccess)
            relaunchedStack.profileRepository.profileAvailability.first {
                it.isFreeSlotLocked && it.availableProfileIds == setOf("default")
            }
            assertEquals("default", relaunchedStack.profileRepository.activeProfile.value?.profileId)
            assertEquals(omerId, relaunchedStack.profileRepository.profileAvailability.value.selectedCustomProfileId)
            assertFalse(relaunchedStack.profileRepository.profileAvailability.value.canChooseFreeProfile)
            assertEquals(2, relaunchedStack.profileRepository.profileAvailability.value.archivedProfiles.size)

            // Re-import the 5-profile backup back onto this installation and restore Family -> all 5 work again
            relaunchedStack.backupManager.importDatabase(
                uri = Uri.fromFile(fullFiveBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(relaunchedStack.backupManager.result.filterNotNull().first().isSuccess)
            relaunchedEntitlement.setFamilyEntitlementActive(true)
            relaunchedStack.profileRepository.profileAvailability.first {
                !it.isFreeSlotLocked && it.availableProfiles.size == 5 && it.archivedProfiles.isEmpty()
            }
            listOf("default", mayaId, noaId, liamId, omerId).forEach { id ->
                assertTrue(relaunchedStack.profileRepository.switchProfile(id))
            }
            val restoredSnack = relaunchedStack.aacRepository.baseDefinitions.value.firstOrNull {
                it.id == "omer_custom_snack" && it.languageCode == "he"
            }
            assertNotNull(restoredSnack)
            assertArrayEquals(customImageBytes, File(restoredSnack!!.imageUri!!).readBytes())
            assertArrayEquals(customWavBytes, File(restoredSnack.audioUri!!).readBytes())
        }

        // 5. Fresh Free installation importing all 5 profiles can make its initial choice once,
        // and restoring Family unlocks all 5 immediately without another import
        val destEntitlement = ConfigurableFamilyEntitlementProvider(
            FamilyEntitlementState(
                tier = ProfilePlanTier.FREE,
                isBillingConfigured = true,
                isProfileCapEnforced = true,
            ),
        )
        val destFreeStack = createStack("acceptance_dest_free", destEntitlement)
        runBlocking {
            assertTrue(destFreeStack.coordinator.initialize())
            destFreeStack.backupManager.importDatabase(
                uri = Uri.fromFile(fullFiveBackupZip),
                contentResolver = targetContext.contentResolver,
            )
            assertTrue(destFreeStack.backupManager.result.filterNotNull().first().isSuccess)

            assertEquals(5, destFreeStack.profileRepository.profiles.value.size)
            assertEquals(2, destFreeStack.profileRepository.profileAvailability.value.availableProfiles.size)
            assertEquals(3, destFreeStack.profileRepository.profileAvailability.value.archivedProfiles.size)
            assertTrue(destFreeStack.profileRepository.profileAvailability.value.canChooseFreeProfile)
            assertFalse(destFreeStack.profileRepository.profileAvailability.value.isFreeSlotLocked)

            // Confirm initial choice once on fresh Free installation -> locks subsequent changes
            assertTrue(
                destFreeStack.profileRepository.confirmFreeCustomProfile(omerId)
                    is SelectFreeCustomProfileResult.Success,
            )
            assertTrue(destFreeStack.profileRepository.profileAvailability.value.isFreeSlotLocked)
            assertEquals(
                SelectFreeCustomProfileResult.Locked,
                destFreeStack.profileRepository.confirmFreeCustomProfile(mayaId),
            )

            // Restore Family -> all 5 profiles become available again without another import;
            // later confirmed expiry starts a new one-time selection
            destEntitlement.setFamilyEntitlementActive(true)
            destFreeStack.profileRepository.profileAvailability.first {
                !it.isFreeSlotLocked && it.availableProfiles.size == 5 && it.archivedProfiles.isEmpty()
            }
            assertTrue(destFreeStack.profileRepository.switchProfile(noaId))
            assertTrue(destFreeStack.profileRepository.switchProfile(liamId))
            assertTrue(destFreeStack.profileRepository.switchProfile(mayaId))

            destEntitlement.setFamilyEntitlementActive(false)
            destFreeStack.profileRepository.profileAvailability.first {
                !it.isFreeSlotLocked && it.canChooseFreeProfile && it.availableProfiles.size == 2
            }
        }
    }

    private fun createTestWavBytes(): ByteArray {
        val sampleRate = 44100
        val sampleCount = 2205
        val dataSize = sampleCount * 2
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * 2
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(intToLittleEndian(totalDataLen))
        out.write("WAVEfmt ".toByteArray())
        out.write(intToLittleEndian(16))
        out.write(byteArrayOf(1, 0, 1, 0))
        out.write(intToLittleEndian(sampleRate))
        out.write(intToLittleEndian(byteRate))
        out.write(byteArrayOf(2, 0, 16, 0))
        out.write("data".toByteArray())
        out.write(intToLittleEndian(dataSize))
        for (i in 0 until sampleCount) {
            val sample: Short = if (i % 2 == 0) 8000 else -8000
            out.write(sample.toInt() and 0xFF)
            out.write((sample.toInt() shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private class IsolatedAppContext(
        base: Context,
        private val isolatedFilesDir: File,
        private val isolatedCacheDir: File,
    ) : ContextWrapper(base) {
        override fun getFilesDir(): File = isolatedFilesDir
        override fun getCacheDir(): File = isolatedCacheDir
        override fun getApplicationContext(): Context = this
    }

    private class IsolatedStack(
        val rootDir: File,
        val filesDir: File,
        val dbName: String,
        val context: Context,
        private val scope: CoroutineScope,
        private val database: AACDatabase,
        val settingsRepository: SettingsRepository,
        val profileRepository: ProfileRepository,
        val aacRepository: AACRepository,
        val backupService: BackupService,
        val backupManager: BackupManager,
        val coordinator: AppStartupCoordinator,
    ) {
        suspend fun closeWithoutDeleting() {
            runCatching { database.close() }
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }

        fun dispose(targetContext: Context) {
            runCatching { database.close() }
            runCatching { targetContext.deleteDatabase(dbName) }
            scope.cancel()
        }
    }
}
