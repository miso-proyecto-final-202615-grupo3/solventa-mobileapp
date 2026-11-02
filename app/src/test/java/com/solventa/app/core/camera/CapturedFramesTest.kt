package com.solventa.app.core.camera

import com.solventa.app.core.liveness.FrameSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CapturedFramesTest {

    @Test
    fun `wipe sobrescribe los bytes con ceros y suelta las capturas`() {
        val bytes = byteArrayOf(1, 2, 3)
        val frames = CapturedFrames()
        frames[FrameSlot.START] = CapturedFrame(bytes, Instant.EPOCH)
        val frame = frames[FrameSlot.START]!!

        frames.wipe()

        assertTrue(bytes.all { it == 0.toByte() })
        assertTrue(frame.isWiped)
        assertFalse(frames.isComplete())
    }

    @Test(expected = IllegalStateException::class)
    fun `una captura borrada ya no entrega bytes`() {
        val frames = CapturedFrames()
        frames[FrameSlot.START] = CapturedFrame(byteArrayOf(9), Instant.EPOCH)
        val frame = frames[FrameSlot.START]!!
        frames.wipe()

        frame.bytes()
    }

    @Test
    fun `reemplazar una captura borra la anterior`() {
        val first = byteArrayOf(7, 7)
        val frames = CapturedFrames()
        frames[FrameSlot.GESTURE] = CapturedFrame(first, Instant.EPOCH)

        frames[FrameSlot.GESTURE] = CapturedFrame(byteArrayOf(8), Instant.EPOCH)

        assertTrue(first.all { it == 0.toByte() })
    }

    @Test
    fun `esta completa solo con las tres capturas`() {
        val frames = CapturedFrames()
        frames[FrameSlot.START] = CapturedFrame(byteArrayOf(1), Instant.EPOCH)
        frames[FrameSlot.GESTURE] = CapturedFrame(byteArrayOf(1), Instant.EPOCH)
        assertFalse(frames.isComplete())

        frames[FrameSlot.END] = CapturedFrame(byteArrayOf(1), Instant.EPOCH)

        assertTrue(frames.isComplete())
    }
}
