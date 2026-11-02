package com.solventa.app.feature.activation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.core.liveness.FaceHint
import com.solventa.app.core.liveness.GuideState
import com.solventa.app.core.liveness.LivenessChallenge
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivationScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun rechazoMuestraIntentosRestantesYReintentar() {
        var retried = false
        composeRule.setContent {
            SolventaTheme {
                ActivationResultScreen(ActivationOutcome.REJECTED, 2, onRetry = { retried = true }, onContinue = {}, onBackToLogin = {})
            }
        }

        composeRule.onNodeWithTag(ActivationTestTags.REMAINING).assertIsDisplayed()
        val remaining = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.result_remaining, 2, 2)
        composeRule.onNodeWithText(remaining).assertIsDisplayed()
        composeRule.onNodeWithTag(ActivationTestTags.RETRY).performClick()
        assertTrue(retried)
    }

    @Test
    fun agotadoNoOfreceReintentar() {
        composeRule.setContent {
            SolventaTheme {
                ActivationResultScreen(ActivationOutcome.EXHAUSTED, 0, onRetry = {}, onContinue = {}, onBackToLogin = {})
            }
        }

        composeRule.onNodeWithTag(ActivationTestTags.BACK_TO_LOGIN).assertIsDisplayed()
        composeRule.onNodeWithTag(ActivationTestTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun guiaMuestraElGestoPedido() {
        composeRule.setContent {
            SolventaTheme {
                LivenessCaptureScreen(guide = GuideState.ChallengeInProgress(LivenessChallenge.TURN_RIGHT), onCancel = {})
            }
        }

        composeRule.onNodeWithText(text(R.string.guide_turn_right)).assertIsDisplayed()
    }

    @Test
    fun guiaPideUnaSolaPersona() {
        composeRule.setContent {
            SolventaTheme {
                LivenessCaptureScreen(guide = GuideState.AdjustFace(FaceHint.MULTIPLE_FACES), onCancel = {})
            }
        }

        composeRule.onNodeWithText(text(R.string.guide_multiple_faces)).assertIsDisplayed()
    }

    @Test
    fun introSinPermisoPermanenteOfreceAjustes() {
        composeRule.setContent {
            SolventaTheme {
                ActivationIntroScreen(
                    hasFrontCamera = true, permissionDenied = true, permanentlyDenied = true,
                    onStart = {}, onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.activation_open_settings)).assertIsDisplayed()
    }
}
