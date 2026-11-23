package com.solventa.app.feature.claims.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/** HU4.2.3: formato de la compensación y nombres de los tipos paramétricos. */
class MoneyFormatTest {
    @Test
    fun `pesos enteros sin decimales`() {
        val text = formatMoney(BigDecimal("300000.00"), "COP", Locale.US)

        assertTrue(text, text.endsWith("300,000"))
        assertFalse(text, text.contains(".00"))
    }

    @Test
    fun `con centavos muestra dos decimales`() {
        assertTrue(formatMoney(BigDecimal("1500.5"), "USD", Locale.US).endsWith("1,500.50"))
    }

    @Test
    fun `moneda desconocida no rompe el formato`() {
        assertTrue(formatMoney(BigDecimal("10"), "XYZ1", Locale.US).endsWith("10"))
    }

    @Test
    fun `pesos con convenciones de Colombia aunque el telefono este en otra region`() {
        assertEquals(Locale("es", "CO"), moneyLocale("COP", Locale("es", "US")))
        assertEquals(Locale.US, moneyLocale("USD", Locale.US))
        val text = formatMoney(BigDecimal("300000.00"), "COP", moneyLocale("COP", Locale("es", "US")))
        assertTrue(text, text.contains("300.000"))
    }

    @Test
    fun `tipos parametricos tienen nombre propio`() {
        assertTrue(parametricTypeRes("LLUVIA_EXTREMA") != null && parametricTypeRes("RETRASO_VUELO") != null)
        assertNull(parametricTypeRes("ROBO"))
        assertEquals(parametricTypeRes("LLUVIA_EXTREMA"), parametricTypeRes("LLUVIA_EXTREMA"))
    }
}
