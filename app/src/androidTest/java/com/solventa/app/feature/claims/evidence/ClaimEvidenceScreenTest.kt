package com.solventa.app.feature.claims.evidence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.core.location.LocationFix
import com.solventa.app.core.location.LocationSource
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.EvidenceRules
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.EvidenceOrigin
import com.solventa.app.data.claims.evidence.EvidenceProblem
import com.solventa.app.data.claims.evidence.EvidenceUploadState
import com.solventa.app.data.claims.evidence.LocalEvidence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ClaimEvidenceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val claim = ReportedClaim(
        id = "c-1", number = "SIN-2026-ABCDEF01", status = ClaimStatus.REPORTED, policyId = "p", policyNumber = "SLV",
        typeCode = "EQUIPAJE", occurredAt = Instant.EPOCH, reportedAt = Instant.EPOCH,
    )

    private fun item(id: String, state: EvidenceUploadState, problem: EvidenceProblem? = null) = LocalEvidence(
        localId = id, claimId = "c-1", claimNumber = claim.number, kind = EvidenceKind.PHOTO, origin = EvidenceOrigin.CAMERA,
        filePath = null, sizeBytes = 10, capturedAt = "2026-11-10T14:00-05:00", state = state, problem = problem,
    )

    private fun setScreen(state: ClaimEvidenceUiState, onRetry: (String) -> Unit = {}, onRemove: (String) -> Unit = {}) {
        composeRule.setContent {
            SolventaTheme {
                ClaimEvidenceScreen(
                    state = state,
                    onTakePhoto = {}, onPickPhotos = {}, onRecordVideo = {}, onPickVideo = {},
                    onAttachLocation = {}, onOpenSettings = {}, onRetry = onRetry, onRemove = onRemove, onReload = {},
                    onDismissMessage = {}, onFinish = {}, onConfirmLeave = {}, onDismissLeave = {},
                )
            }
        }
    }

    @Test
    fun cadaEvidenciaMuestraSuEstadoYSusAcciones() {
        var retried: String? = null
        var removed: String? = null
        setScreen(
            ClaimEvidenceUiState(
                loading = false, claim = claim, rules = EvidenceRules(5, 1),
                local = listOf(
                    item("ok", EvidenceUploadState.UPLOADED),
                    item("falla", EvidenceUploadState.FAILED, EvidenceProblem.NETWORK),
                    item("rechazo", EvidenceUploadState.REJECTED, EvidenceProblem.TOO_LARGE),
                ),
                remainingPhotos = 2,
            ),
            onRetry = { retried = it },
            onRemove = { removed = it },
        )

        composeRule.onNodeWithTag(ClaimEvidenceTestTags.RETRY_PREFIX + "falla").performClick()
        composeRule.onNodeWithTag(ClaimEvidenceTestTags.REMOVE_PREFIX + "rechazo").performClick()
        composeRule.onNodeWithText(context.getString(R.string.evidence_problem_too_large)).assertIsDisplayed()

        assertEquals("falla", retried)
        assertEquals("rechazo", removed)
    }

    @Test
    fun sinCupoLosBotonesDeFotoSeDeshabilitan() {
        setScreen(ClaimEvidenceUiState(loading = false, claim = claim, rules = EvidenceRules(5, 0), remainingPhotos = 0, remainingVideos = 0))

        composeRule.onNodeWithTag(ClaimEvidenceTestTags.TAKE_PHOTO).assertIsNotEnabled()
        composeRule.onNodeWithTag(ClaimEvidenceTestTags.PICK_PHOTOS).assertIsNotEnabled()
    }

    @Test
    fun ubicacionAdjuntaMuestraLaDireccionYLaPrecision() {
        val fix = LocationFix(4.65, -74.08, 18f, Instant.EPOCH, LocationSource.GPS)
        setScreen(ClaimEvidenceUiState(loading = false, claim = claim, location = LocationUi.Attached(fix, "Bogotá, Colombia")))

        composeRule.onNodeWithText("Bogotá, Colombia").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.evidence_location_accuracy, 18)).assertIsDisplayed()
    }

    @Test
    fun terminarConPendientesMuestraElDialogo() {
        setScreen(
            ClaimEvidenceUiState(
                loading = false, claim = claim, local = listOf(item("x", EvidenceUploadState.UPLOADING)), confirmLeaveWithPending = true,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.evidence_leave_title)).assertIsDisplayed()
    }
}
