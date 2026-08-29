package com.dchernykh.hex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dchernykh.hex.game.BoardSize
import com.dchernykh.hex.game.Level
import com.dchernykh.hex.game.Mode
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * What no JVM test can check: that the game actually runs on a watch.
 *
 * Launching the activity exercises the manifest, the theme, the launcher icon, the
 * hexagon canvas, the whole Compose tree and the DataStore-backed settings in one
 * go - the parts excused from the coverage floor precisely because they need a
 * device. The rules and the opponent are covered far more cheaply by the unit
 * tests, so this walks the menu and starts a board rather than playing one out.
 *
 * Every label is read from the resources, so the test says the same thing on a
 * watch set to any of the eleven languages.
 */
class GameScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun onScreen(label: String) = rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun opensOnTheMenu() {
        rule.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.play)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.hint)).assertIsDisplayed()
    }

    @Test
    fun walksTheBoardSizes() {
        // Board sizes cycle whatever mode the watch is in, so this one needs no
        // setting up - but like every test here it starts from what it finds
        // rather than from what it would like.

        val labels = BoardSize.entries.map { it.label }
        rule.waitUntil { labels.any(::onScreen) }
        val before = labels.first(::onScreen)

        rule.onNodeWithText(before).performClick()
        rule.waitUntil { !onScreen(before) }

        assertNotEquals(before, labels.first(::onScreen))
    }

    @Test
    fun walksTheModesAndHidesTheLevelWithTheWatch() {
        // Started from whichever mode the watch was last left on, because the
        // settings are stored and a test that assumed one of them would pass or
        // fail depending on what ran before it.
        val modes = Mode.entries.map { text(it.labelRes) }
        rule.waitUntil { modes.any(::onScreen) }
        walkToMode(Mode.COMPUTER)

        // Against the watch there is a level to set; between two people there is
        // nothing for it to mean, so the button is not there at all.
        rule.waitUntil { Level.entries.any { onScreen(text(it.labelRes)) } }

        walkToMode(Mode.TWO_PLAYERS)
        rule.waitUntil { Level.entries.none { onScreen(text(it.labelRes)) } }
    }

    /** Tap the mode button until it shows the one wanted. */
    private fun walkToMode(wanted: Mode) {
        repeat(Mode.entries.size) {
            if (onScreen(text(wanted.labelRes))) return
            val showing = Mode.entries.first { onScreen(text(it.labelRes)) }
            rule.onNodeWithText(text(showing.labelRes)).performClick()
            rule.waitUntil { !onScreen(text(showing.labelRes)) }
        }
        rule.onNodeWithText(text(wanted.labelRes)).assertIsDisplayed()
    }

    @Test
    fun walksThePieRule() {
        rule.waitUntil { onScreen(text(R.string.swap_on)) || onScreen(text(R.string.swap_off)) }
        val before = if (onScreen(text(R.string.swap_on))) R.string.swap_on else R.string.swap_off

        rule.onNodeWithText(text(before)).performClick()
        rule.waitUntil { !onScreen(text(before)) }
    }

    @Test
    fun startsABoardAndComesBack() {
        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.waitForIdle()

        // The play screen carries the way out in its lower cap.
        rule.waitUntil { onScreen(text(R.string.menu)) || onScreen(text(R.string.swap)) }

        if (onScreen(text(R.string.menu))) {
            rule.onNodeWithText(text(R.string.menu)).performClick()
            rule.waitUntil { onScreen(text(R.string.play)) }
        }
    }
}
