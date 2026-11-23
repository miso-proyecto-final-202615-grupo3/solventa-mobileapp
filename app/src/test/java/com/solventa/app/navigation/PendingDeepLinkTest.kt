package com.solventa.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingDeepLinkTest {
    @Test
    fun `guarda el siniestro de la notificacion y se consume una sola vez`() {
        val link = PendingDeepLink()

        link.setClaim("c-1")

        assertEquals("c-1", link.claimId.value)
        assertEquals("c-1", link.consume())
        assertNull(link.consume())
    }

    @Test
    fun `un intent sin siniestro no borra el pendiente`() {
        val link = PendingDeepLink()
        link.setClaim("c-1")

        link.setClaim(null)
        link.setClaim(" ")

        assertEquals("c-1", link.consume())
    }
}
