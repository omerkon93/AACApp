package com.kon.myaacapp

import android.content.Context
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageDownloadHelperTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var splitInstallManager: SplitInstallManager

    private lateinit var helper: LanguageDownloadHelper
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockedFactory: MockedStatic<SplitInstallManagerFactory>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        mockedFactory = mockStatic(SplitInstallManagerFactory::class.java)
        mockedFactory.`when`<SplitInstallManager> { 
            SplitInstallManagerFactory.create(context) 
        }.thenReturn(splitInstallManager)
        
        helper = LanguageDownloadHelper(context)
    }

    @After
    fun tearDown() {
        mockedFactory.close()
        Dispatchers.resetMain()
    }

    private fun <T> mockTask(result: T): Task<T> {
        val task = mock(Task::class.java) as Task<T>
        whenever(task.addOnSuccessListener(any())).thenAnswer { invocation ->
            val listener = invocation.arguments[0] as OnSuccessListener<T>
            listener.onSuccess(result)
            task
        }
        return task
    }

    @Test
    fun `test normalization of hebrew via LocaleHelper`() {
        whenever(splitInstallManager.installedLanguages).thenReturn(setOf("iw"))
        
        assertTrue(helper.isLanguageInstalled("he"))
        assertTrue(helper.isLanguageInstalled("iw"))
    }

    @Test
    fun `test downloadLanguage already installed`() = runTest {
        whenever(splitInstallManager.installedLanguages).thenReturn(setOf("en"))
        
        var called = false
        helper.downloadLanguage("en") { success ->
            called = success
        }
        
        assertTrue(called)
        verify(splitInstallManager, never()).startInstall(any())
    }

    @Test
    fun `test downloadLanguage starts install`() = runTest {
        whenever(splitInstallManager.installedLanguages).thenReturn(emptySet())
        val task = mockTask(1)
        whenever(splitInstallManager.startInstall(any())).thenReturn(task)

        helper.downloadLanguage("fr") { }

        verify(splitInstallManager).startInstall(any())
    }

    @Test
    fun `test listener handles success`() = runTest {
        whenever(splitInstallManager.installedLanguages).thenReturn(emptySet())
        val task = mockTask(1)
        whenever(splitInstallManager.startInstall(any())).thenReturn(task)

        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        verify(splitInstallManager).registerListener(listenerCaptor.capture())

        var completionSuccess: Boolean? = null
        helper.downloadLanguage("de") { success ->
            completionSuccess = success
        }

        val listener = listenerCaptor.firstValue
        
        val successState = mock(SplitInstallSessionState::class.java)
        whenever(successState.status()).thenReturn(SplitInstallSessionStatus.INSTALLED)
        whenever(successState.sessionId()).thenReturn(1)
        
        listener.onStateUpdate(successState)
        
        assertEquals(true, completionSuccess)
        assertEquals(DownloadStatus.Idle, helper.downloadStatus.value)
    }

    @Test
    fun `test multiple concurrent downloads call correct callbacks`() = runTest {
        whenever(splitInstallManager.installedLanguages).thenReturn(emptySet())
        
        val task1 = mockTask(101)
        val task2 = mockTask(102)
        
        whenever(splitInstallManager.startInstall(any())).thenReturn(task1, task2)

        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        verify(splitInstallManager).registerListener(listenerCaptor.capture())

        var completion1: Boolean? = null
        var completion2: Boolean? = null

        helper.downloadLanguage("de") { success -> completion1 = success }
        helper.downloadLanguage("fr") { success -> completion2 = success }

        val listener = listenerCaptor.firstValue
        
        // Complete session 102 first
        val state2 = mock(SplitInstallSessionState::class.java)
        whenever(state2.status()).thenReturn(SplitInstallSessionStatus.INSTALLED)
        whenever(state2.sessionId()).thenReturn(102)
        listener.onStateUpdate(state2)
        
        assertEquals(null, completion1)
        assertEquals(true, completion2)

        // Complete session 101
        val state1 = mock(SplitInstallSessionState::class.java)
        whenever(state1.status()).thenReturn(SplitInstallSessionStatus.INSTALLED)
        whenever(state1.sessionId()).thenReturn(101)
        listener.onStateUpdate(state1)
        
        assertEquals(true, completion1)
    }
}
