package com.solventa.app.core.liveness.launcher

import androidx.activity.ComponentActivity
import com.solventa.app.core.liveness.LivenessChallenge
import com.solventa.app.data.activation.CaptureType
import com.solventa.app.data.activation.SdkStatus
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CaptureLauncherFactoryTest {
    private val didit = object : DiditCaptureLauncher {
        override suspend fun launch(activity: ComponentActivity, sessionToken: String) = SdkStatus.COMPLETED
    }
    private val factory = CaptureLauncherFactory(listOf(OwnCaptureLauncher, didit))

    @Test
    fun `frames usa la captura propia`() {
        assertSame(OwnCaptureLauncher, factory.forType(CaptureType.Frames(LivenessChallenge.BLINK, "n")))
    }

    @Test
    fun `sdk de didit usa su lanzador`() {
        assertSame(didit, factory.forType(CaptureType.Sdk("DIDIT", "token")))
    }

    @Test
    fun `sdk desconocido no tiene estrategia`() {
        assertNull(factory.forType(CaptureType.Sdk("OTRO", "token")))
    }
}
