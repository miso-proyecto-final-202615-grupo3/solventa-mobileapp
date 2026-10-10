package com.solventa.app.feature.biometric

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(status: UnlockStatus, onRetry: () -> Unit = {}, onUsePassword: () -> Unit = {}) {
        composeRule.setContent {
            SolventaTheme { UnlockScreen(status = status, onRetry = onRetry, onUsePassword = onUsePassword) }
        }
    }

    @Test
    fun intentoFallidoMuestraContadorYPermiteReintentar() {
        var retried = false
        show(UnlockStatus.Failed(1, 3), onRetry = { retried = true })

        composeRule.onNodeWithTag(UnlockTestTags.ATTEMPT_BADGE)
            .assertTextEquals(context.getString(R.string.unlock_failed_attempt, 1, 3))
        composeRule.onNodeWithTag(UnlockTestTags.RETRY).performClick()

        assertTrue(retried)
    }

    @Test
    fun bloqueoTemporalMuestraCuentaRegresivaYContrasena() {
        var usedPassword = false
        show(UnlockStatus.LockedOut(299), onUsePassword = { usedPassword = true })

        composeRule.onNodeWithTag(UnlockTestTags.COUNTDOWN).assertTextEquals("4:59")
        composeRule.onNodeWithTag(UnlockTestTags.USE_PASSWORD).performClick()

        assertTrue(usedPassword)
    }

    @Test
    fun cambioDeBiometriaPideContrasena() {
        show(UnlockStatus.BiometricChanged)

        composeRule.onNodeWithText(context.getString(R.string.unlock_changed_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(UnlockTestTags.USE_PASSWORD).assertIsDisplayed()
    }
}
