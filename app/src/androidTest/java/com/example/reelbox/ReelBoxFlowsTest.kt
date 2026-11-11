package com.example.reelbox

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.reelbox.util.formatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flows on the device (definition of done): home -> details -> play, resume after relaunch,
 * My List add/remove, search. Each test starts from an empty database with a fresh default profile.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReelBoxFlowsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val app get() = ApplicationProvider.getApplicationContext<ReelBoxApp>()
    private fun pressBack() { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack() }

    companion object {
        /**
         * MIUI (the attached Xiaomi) blocks activity starts from a background process unless the app holds
         * its "display pop-up windows while running in the background" op (MIUIOP 10021). The test process
         * launches MainActivity from the background, and a fresh install resets the op, so grant it here
         * via the shell. On other devices the unknown op is rejected harmlessly.
         */
        @JvmStatic @BeforeClass fun allowBackgroundActivityStart() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.executeShellCommand("appops set ${BuildConfig.APPLICATION_ID} 10021 allow")
        }
    }

    @Before fun resetData() = runBlocking {
        val c = app.container
        c.database.clearAllTables()
        c.profileRepository.ensureDefaultProfile()
    }

    @After fun close() { scenario?.close() }

    /** Launches the app and picks [profileId] (default: the first profile) on "Who's watching?". */
    private fun launch(profileId: Long? = null) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("profiles"), 15_000)
        val id = profileId ?: runBlocking { app.container.profileRepository.profiles.first().first().id }
        compose.waitUntilAtLeastOneExists(hasTestTag("profile:$id"), 10_000)
        compose.onNodeWithTag("profile:$id").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("home"), 15_000)
        compose.waitUntilAtLeastOneExists(hasTestTag("hero"), 15_000)
    }

    @Test fun homeToDetailsToPlay() {
        launch()
        compose.onAllNodesWithTag("poster:sintel").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("details:sintel"), 10_000)
        compose.onNodeWithTag("details:title").assertTextEquals("Sintel")
        compose.onNodeWithTag("details:play").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("player"), 10_000)
        compose.waitUntilAtLeastOneExists(hasTestTag("player:title"), 10_000)
        compose.onNodeWithTag("player:title").assertTextEquals("Sintel")
    }

    @Test fun resumeAfterRelaunch() {
        launch()
        compose.onAllNodesWithTag("poster:sintel").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("details:play"), 10_000)
        compose.onNodeWithTag("details:play").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("player"), 10_000)
        Thread.sleep(7_000) // past the first periodic save (5 s)
        pressBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("details:sintel"), 10_000)
        scenario!!.close() // activity destroyed; relaunch reads progress from the database
        launch()
        compose.waitUntilAtLeastOneExists(hasTestTag("row:continue"), 10_000)
        compose.onAllNodesWithTag("poster:sintel").onFirst().performClick() // first Sintel card = Continue Watching
        compose.waitUntilAtLeastOneExists(hasTestTag("player:elapsed"), 10_000)
        val saved = runBlocking { app.container.progressRepository.get("sintel") }!!
        assertTrue("saved position ${saved.positionMs}", saved.positionMs >= 5_000)
        val elapsed = compose.onNodeWithTag("player:elapsed").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        val (m, s) = elapsed.split(":").map { it.toInt() }
        assertTrue("resumed at $elapsed, saved ${formatTime(saved.positionMs)}", (m * 60 + s) * 1000L >= saved.positionMs - 5_000)
    }

    @Test fun myListAddAndRemove() {
        launch()
        compose.onNodeWithTag("hero:mylist").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("row:mylist"), 10_000)
        compose.onAllNodesWithTag("poster:sintel").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("details:mylist"), 10_000)
        compose.onNodeWithTag("details:mylist").performClick()
        compose.waitUntil(5_000) { runBlocking { app.container.myListRepository.titleIds.first() }.size == 2 }
        val ids = runBlocking { app.container.myListRepository.titleIds.first() }
        assertTrue("newest first: $ids", ids.first() == "sintel")
        pressBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("hero:mylist"), 10_000)
        compose.onNodeWithTag("hero:mylist").performClick() // remove the hero title again
        compose.waitUntil(5_000) { runBlocking { app.container.myListRepository.titleIds.first() } == listOf("sintel") }
    }

    @Test fun kidsProfileSeesOnlyKidsTitles() {
        val kidsId = runBlocking { app.container.profileRepository.create("Kids", 4, isKids = true)!! }
        launch(kidsId)
        assertTrue(compose.onAllNodesWithTag("row:continue").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("tab:search").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("search:field"), 10_000)
        compose.onNodeWithTag("search:field").performTextInput("Steel")
        compose.waitUntilAtLeastOneExists(hasTestTag("search:noresults"), 5_000)
    }

    @Test fun searchTitleGenreAndNoResults() {
        launch()
        compose.onNodeWithTag("tab:search").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("search:field"), 10_000)
        compose.onNodeWithTag("search:field").performTextInput("sint")
        compose.waitUntilAtLeastOneExists(hasTestTag("poster:sintel"), 5_000)
        assertTrue(compose.onAllNodesWithTag("poster:spring").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("search:field").performTextClearance()
        compose.onNodeWithTag("search:field").performTextInput("Sci-Fi")
        compose.waitUntilAtLeastOneExists(hasTestTag("poster:tears-of-steel"), 5_000)
        assertTrue(compose.onAllNodesWithTag("poster:sintel").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("search:field").performTextClearance()
        compose.onNodeWithTag("search:field").performTextInput("zzzz")
        compose.waitUntilAtLeastOneExists(hasTestTag("search:noresults"), 5_000)
    }
}
