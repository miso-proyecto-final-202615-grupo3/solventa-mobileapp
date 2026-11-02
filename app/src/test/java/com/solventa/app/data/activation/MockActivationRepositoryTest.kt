package com.solventa.app.data.activation

import com.solventa.app.core.camera.CapturedFrame
import com.solventa.app.core.camera.CapturedFrames
import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.FrameSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MockActivationRepositoryTest {
    private val device = DeviceInfo("instalacion-1", model = "Pixel", osVersion = "15")

    private fun completeFrames() = CapturedFrames().apply {
        FrameSlot.entries.forEach { this[it] = CapturedFrame(byteArrayOf(1), Instant.EPOCH) }
    }

    @Test
    fun `aprueba y despues reconoce la instalacion vinculada`() = runTest {
        val repository = MockActivationRepository({ "andres.perez@correo.com" }, latencyMillis = 0)
        val session = (repository.startSession("t", device, "c") as SessionResult.Started).session

        val result = repository.verify("t", session, CaptureOutcome.OwnFrames(completeFrames()), "c")

        assertTrue(result is VerificationResult.Approved)
        assertEquals(SessionResult.AlreadyLinked, repository.startSession("t", device, "c"))
    }

    @Test
    fun `cuenta de rechazo agota los tres intentos`() = runTest {
        val repository = MockActivationRepository({ MockActivationRepository.REJECT_EMAIL }, latencyMillis = 0)
        val results = (1..3).map {
            val session = (repository.startSession("t", device, "c") as SessionResult.Started).session
            repository.verify("t", session, CaptureOutcome.OwnFrames(completeFrames()), "c")
        }

        assertEquals(VerificationResult.Rejected(1, 2, "GESTO_NO_DETECTADO"), results[0])
        assertEquals(VerificationResult.AttemptsExhausted, results[2])
        assertEquals(SessionResult.AttemptsExhausted, repository.startSession("t", device, "c"))
    }

    @Test
    fun `cuenta sin consentimiento`() = runTest {
        val repository = MockActivationRepository({ MockActivationRepository.NO_CONSENT_EMAIL }, latencyMillis = 0)

        assertEquals(SessionResult.ConsentRequired, repository.startSession("t", device, "c"))
    }

    @Test
    fun `simula didit con captura sdk y cancelacion`() = runTest {
        val repository = MockActivationRepository({ "ana@correo.com" }, captureType = "SDK", latencyMillis = 0)
        val session = (repository.startSession("t", device, "c") as SessionResult.Started).session

        assertTrue(session.capture is CaptureType.Sdk)
        assertEquals(
            VerificationResult.Cancelled,
            repository.verify("t", session, CaptureOutcome.SdkResult(SdkStatus.CANCELLED), "c"),
        )
    }

    @Test
    fun `una sesion no se puede verificar dos veces`() = runTest {
        val repository = MockActivationRepository({ "ana@correo.com" }, latencyMillis = 0)
        val session = (repository.startSession("t", device, "c") as SessionResult.Started).session
        repository.verify("t", session, CaptureOutcome.OwnFrames(completeFrames()), "c")

        assertEquals(
            VerificationResult.InvalidSession,
            repository.verify("t", session, CaptureOutcome.OwnFrames(completeFrames()), "c"),
        )
    }
}
