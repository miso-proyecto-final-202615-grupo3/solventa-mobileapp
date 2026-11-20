package com.solventa.app.feature.claims.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.RemoteEvidence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ClaimDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val uploaded = RemoteEvidence("e1", EvidenceKind.PHOTO, "CARGADA", 2048, Instant.EPOCH, null)
    private val pending = RemoteEvidence("e2", EvidenceKind.VIDEO, "PENDIENTE", null, Instant.EPOCH, null)

    private fun claim(status: ClaimStatus) = ReportedClaim(
        id = "c-1", number = "SIN-2026-ABCDEF01", status = status, policyId = "p", policyNumber = "SLV",
        typeCode = "ROBO", occurredAt = Instant.EPOCH, reportedAt = Instant.EPOCH, evidences = listOf(uploaded, pending),
    )

    @Test
    fun soloSePuedeVerLaEvidenciaCargadaYAgregarSiEstaReportado() {
        var opened: RemoteEvidence? = null
        composeRule.setContent {
            SolventaTheme {
                ClaimDetailScreen(
                    state = ClaimDetailUiState(loading = false, claim = claim(ClaimStatus.REPORTED)),
                    onOpenEvidence = { opened = it }, onAddEvidence = {}, onRetry = {}, onDismissMessage = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ClaimDetailTestTags.NUMBER).assertIsDisplayed()
        composeRule.onNodeWithTag(ClaimDetailTestTags.EVIDENCE_PREFIX + "e2").assertIsDisplayed()
        composeRule.onNodeWithTag(ClaimDetailTestTags.EVIDENCE_PREFIX + "e2").performClick() // pendiente: no abre
        assertEquals(null, opened)
        composeRule.onNodeWithTag(ClaimDetailTestTags.EVIDENCE_PREFIX + "e1").performClick()
        assertEquals("e1", opened?.id)
        composeRule.onNodeWithTag(ClaimDetailTestTags.ADD_EVIDENCE).assertIsDisplayed()
    }

    @Test
    fun enEvaluacionNoPermiteAgregarEvidencias() {
        composeRule.setContent {
            SolventaTheme {
                ClaimDetailScreen(
                    state = ClaimDetailUiState(loading = false, claim = claim(ClaimStatus.IN_REVIEW)),
                    onOpenEvidence = {}, onAddEvidence = {}, onRetry = {}, onDismissMessage = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ClaimDetailTestTags.ADD_EVIDENCE).assertDoesNotExist()
    }
}
