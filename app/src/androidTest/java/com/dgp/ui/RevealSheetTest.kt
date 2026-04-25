package com.dgp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dgp.DgpService
import com.dgp.ui.theme.EditorialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RevealSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val svc = DgpService(name = "github", type = "alnum")
    private val password = "abc12345"
    private val masked = "•".repeat(password.length)

    @Test
    fun revealSheet_quickTap_doesNotReveal() {
        composeTestRule.setContent {
            EditorialTheme { RevealSheetContent(svc, password, {}, {}, {}) }
        }
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithTag("reveal-target").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(80)
        composeTestRule.onNodeWithTag("reveal-target").performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(50)

        composeTestRule.onNodeWithText(masked).assertIsDisplayed()
        composeTestRule.onNodeWithText(password).assertDoesNotExist()
    }

    @Test
    fun revealSheet_pressAndHold_revealsAfterDelay() {
        composeTestRule.setContent {
            EditorialTheme { RevealSheetContent(svc, password, {}, {}, {}) }
        }
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithTag("reveal-target").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(200)

        composeTestRule.onNodeWithText(password).assertIsDisplayed()

        composeTestRule.onNodeWithTag("reveal-target").performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(50)

        composeTestRule.onNodeWithText(masked).assertIsDisplayed()
    }
}
