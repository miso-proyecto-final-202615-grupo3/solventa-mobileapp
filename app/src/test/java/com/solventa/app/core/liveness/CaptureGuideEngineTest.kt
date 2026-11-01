package com.solventa.app.core.liveness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGuideEngineTest {

    private fun neutral(t: Long, yaw: Float = 0f, eyes: Float = 0.95f) = FaceSignals(
        faceCount = 1,
        leftEyeOpenProbability = eyes,
        rightEyeOpenProbability = eyes,
        headEulerY = yaw,
        faceBoxRatio = 0.2f,
        faceCentered = true,
        timestampMillis = t,
    )

    /** Recorre la guía hasta pedir START y devuelve el último tiempo usado. */
    private fun reachChallenge(engine: CaptureGuideEngine): Long {
        assertNull(engine.onFrame(neutral(0)).captureSlot)
        val start = engine.onFrame(neutral(600))
        assertEquals(FrameSlot.START, start.captureSlot)
        return 600
    }

    @Test
    fun `parpadeo completo pide inicio gesto y fin en orden`() {
        val engine = CaptureGuideEngine(LivenessChallenge.BLINK)
        val t = reachChallenge(engine)

        assertEquals(FrameSlot.GESTURE, engine.onFrame(neutral(t + 200, eyes = 0.05f)).captureSlot)
        assertNull(engine.onFrame(neutral(t + 300)).captureSlot)
        val end = engine.onFrame(neutral(t + 700))

        assertEquals(FrameSlot.END, end.captureSlot)
        assertEquals(GuideState.Completed, end.state)
    }

    @Test
    fun `ojos entrecerrados no cuentan como parpadeo`() {
        val engine = CaptureGuideEngine(LivenessChallenge.BLINK)
        val t = reachChallenge(engine)

        val update = engine.onFrame(neutral(t + 200, eyes = 0.5f))

        assertNull(update.captureSlot)
        assertEquals(GuideState.ChallengeInProgress(LivenessChallenge.BLINK), update.state)
    }

    @Test
    fun `giro a la izquierda y a la derecha usan el signo configurado`() {
        val left = CaptureGuideEngine(LivenessChallenge.TURN_LEFT)
        val right = CaptureGuideEngine(LivenessChallenge.TURN_RIGHT)
        val tl = reachChallenge(left)
        val tr = reachChallenge(right)

        assertEquals(FrameSlot.GESTURE, left.onFrame(neutral(tl + 100, yaw = 30f)).captureSlot)
        assertNull(right.onFrame(neutral(tr + 100, yaw = 30f)).captureSlot)
        assertEquals(FrameSlot.GESTURE, right.onFrame(neutral(tr + 200, yaw = -30f)).captureSlot)
    }

    @Test
    fun `no inicia hasta que el rostro esta estable 500 ms`() {
        val engine = CaptureGuideEngine(LivenessChallenge.BLINK)

        engine.onFrame(neutral(0))
        val update = engine.onFrame(neutral(300))

        assertTrue(update.state is GuideState.HoldStill)
        assertNull(update.captureSlot)
    }

    @Test
    fun `rostro pequeno o descentrado pide ajustar`() {
        val engine = CaptureGuideEngine(LivenessChallenge.BLINK)

        val far = engine.onFrame(neutral(0).copy(faceBoxRatio = 0.01f))
        val offCenter = engine.onFrame(neutral(100).copy(faceCentered = false))

        assertEquals(GuideState.AdjustFace(FaceHint.TOO_FAR), far.state)
        assertEquals(GuideState.AdjustFace(FaceHint.NOT_CENTERED), offCenter.state)
    }

    @Test
    fun `dos rostros antes del gesto pide ajustar y durante el gesto falla`() {
        val before = CaptureGuideEngine(LivenessChallenge.BLINK)
        assertEquals(
            GuideState.AdjustFace(FaceHint.MULTIPLE_FACES),
            before.onFrame(FaceSignals(faceCount = 2, timestampMillis = 0)).state,
        )

        val during = CaptureGuideEngine(LivenessChallenge.BLINK)
        val t = reachChallenge(during)
        assertEquals(
            GuideState.Failed(GuideFailure.MULTIPLE_FACES),
            during.onFrame(FaceSignals(faceCount = 2, timestampMillis = t + 100)).state,
        )
    }

    @Test
    fun `rostro perdido mas de un segundo falla`() {
        val engine = CaptureGuideEngine(LivenessChallenge.TURN_LEFT)
        val t = reachChallenge(engine)

        engine.onFrame(FaceSignals(faceCount = 0, timestampMillis = t + 100))
        val update = engine.onFrame(FaceSignals(faceCount = 0, timestampMillis = t + 1_200))

        assertEquals(GuideState.Failed(GuideFailure.FACE_LOST), update.state)
    }

    @Test
    fun `sin gesto en diez segundos falla por tiempo`() {
        val engine = CaptureGuideEngine(LivenessChallenge.BLINK)
        val t = reachChallenge(engine)

        val update = engine.onFrame(neutral(t + 10_001))

        assertEquals(GuideState.Failed(GuideFailure.TIMEOUT), update.state)
        assertEquals(GuideState.Failed(GuideFailure.TIMEOUT), engine.onFrame(neutral(t + 10_100)).state)
    }
}
