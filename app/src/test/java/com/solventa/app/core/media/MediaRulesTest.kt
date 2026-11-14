package com.solventa.app.core.media

import androidx.exifinterface.media.ExifInterface
import com.solventa.app.data.claims.evidence.EvidenceProblem
import com.solventa.app.data.claims.evidence.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRulesTest {
    @Test
    fun `escala al lado mayor sin agrandar`() {
        assertEquals(2048 to 1536, ImageScaling.targetSize(4000, 3000, 2048))
        assertEquals(1536 to 2048, ImageScaling.targetSize(3000, 4000, 2048))
        assertEquals(1000 to 800, ImageScaling.targetSize(1000, 800, 2048))
    }

    @Test
    fun `sample size deja el lado mayor por encima del objetivo`() {
        assertEquals(1, ImageScaling.sampleSize(3000, 2000, 2048))
        assertEquals(2, ImageScaling.sampleSize(4096, 3072, 2048))
        assertEquals(4, ImageScaling.sampleSize(8192, 6144, 2048))
        assertEquals(1, ImageScaling.sampleSize(800, 600, 2048))
    }

    @Test
    fun `rotacion segun exif`() {
        assertEquals(90, ImageScaling.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(270, ImageScaling.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0, ImageScaling.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun `reglas del video`() {
        val ok = VideoInfo(durationMs = 30_000, sizeBytes = 10L * 1024 * 1024, mimeType = "video/mp4")
        assertEquals(VideoCheck.OK, VideoRules.check(ok))
        assertEquals(VideoCheck.OK, VideoRules.check(ok.copy(durationMs = 60_900))) // tolerancia de 1 s
        assertEquals(VideoCheck.TOO_LONG, VideoRules.check(ok.copy(durationMs = 61_001)))
        assertEquals(VideoCheck.TOO_LARGE, VideoRules.check(ok.copy(sizeBytes = VideoRules.MAX_BYTES + 1)))
        assertEquals(VideoCheck.UNSUPPORTED, VideoRules.check(ok.copy(mimeType = "video/3gpp")))
        assertEquals(VideoCheck.UNSUPPORTED, VideoRules.check(null))
    }

    @Test
    fun `retroceso exponencial y que se reintenta`() {
        val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 2_000)
        assertEquals(listOf(2_000L, 4_000L, 8_000L), (1..3).map(policy::delayFor))
        assertTrue(policy.isRetryable(EvidenceProblem.NETWORK))
        assertTrue(policy.isRetryable(EvidenceProblem.SERVICE_UNAVAILABLE))
        assertFalse(policy.isRetryable(EvidenceProblem.LIMIT_REACHED))
        assertFalse(policy.isRetryable(EvidenceProblem.REJECTED_BY_SERVER))
    }
}
