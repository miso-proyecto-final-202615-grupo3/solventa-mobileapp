package com.solventa.app.core.camera

import com.solventa.app.core.liveness.FrameSlot
import java.time.Instant

/** Un fotograma JPEG en memoria con la marca de tiempo del sensor. */
class CapturedFrame(bytes: ByteArray, val capturedAt: Instant) {
    private var data: ByteArray? = bytes

    fun bytes(): ByteArray = checkNotNull(data) { "La captura ya fue descartada" }

    val isWiped: Boolean get() = data == null

    internal fun wipe() {
        data?.fill(0)
        data = null
    }
}

/**
 * Las tres capturas de la prueba de vida (inicio, gesto, fin). Nunca se escriben en disco:
 * `wipe()` sobrescribe los bytes con ceros y suelta las referencias (CA7).
 */
class CapturedFrames {
    private val frames = linkedMapOf<FrameSlot, CapturedFrame>()

    operator fun set(slot: FrameSlot, frame: CapturedFrame) {
        frames.remove(slot)?.wipe()
        frames[slot] = frame
    }

    operator fun get(slot: FrameSlot): CapturedFrame? = frames[slot]

    fun isComplete(): Boolean = FrameSlot.entries.all { frames[it] != null }

    fun wipe() {
        frames.values.forEach(CapturedFrame::wipe)
        frames.clear()
    }
}
