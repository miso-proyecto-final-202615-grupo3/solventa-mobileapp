package com.solventa.app.core.push

import com.solventa.app.data.claims.ClaimStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParametricMessageTest {
    private fun sample(path: String): Map<String, String> = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader!!.getResource(path)).readText(),
    ).jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `el mensaje del siniestro automatico se reconoce como parametrico`() {
        val message = ClaimStatusMessage.from(sample("contracts/parametricos/mensaje-siniestro-parametrico.json"))

        assertEquals(ClaimStatusMessage("5e8a1c2d-3f4b-4a6c-9d7e-8f9a0b1c2d3e", "SIN-2026-5E8A1C2D", ClaimStatus.APPROVED, parametric = true), message)
    }

    @Test
    fun `un mensaje sin canal es de un siniestro reportado`() {
        assertFalse(ClaimStatusMessage.from(sample("contracts/notificaciones/mensaje-siniestro-estado.json"))!!.parametric)
    }
}
