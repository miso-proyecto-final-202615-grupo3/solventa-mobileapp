package com.solventa.app.core.push

import com.solventa.app.data.claims.ClaimStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaimStatusMessageTest {
    private val sample: Map<String, String> = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader!!.getResource("contracts/notificaciones/mensaje-siniestro-estado.json")).readText(),
    ).jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `el mensaje del contrato identifica el siniestro y su estado`() {
        assertEquals(
            ClaimStatusMessage("c41f0e2a-9b7d-4e55-8a0c-2f6d1b3e9a77", "SIN-2026-C41F0E2A", ClaimStatus.IN_REVIEW),
            ClaimStatusMessage.from(sample),
        )
    }

    @Test
    fun `se ignoran mensajes de otro tipo, incompletos o con estado desconocido`() {
        assertNull(ClaimStatusMessage.from(sample + ("tipo" to "OTRO")))
        assertNull(ClaimStatusMessage.from(sample - "siniestro_id"))
        assertNull(ClaimStatusMessage.from(sample + ("numero" to "")))
        assertNull(ClaimStatusMessage.from(sample + ("estado" to "CERRADO")))
        assertNull(ClaimStatusMessage.from(emptyMap()))
    }
}
