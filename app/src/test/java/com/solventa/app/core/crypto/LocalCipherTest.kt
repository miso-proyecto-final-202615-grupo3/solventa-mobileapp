package com.solventa.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class LocalCipherTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = SecretKeyLocalCipher { key }

    @Test
    fun `descifra lo que cifro`() {
        val plain = "SLV-2026-08341 · Muerte por cualquier causa".toByteArray()

        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun `el contenido cifrado no contiene el texto original`() {
        val plain = "SLV-2026-08341".toByteArray()

        assertFalse(String(cipher.encrypt(plain), Charsets.ISO_8859_1).contains("SLV-2026-08341"))
    }

    @Test
    fun `cada cifrado usa un IV distinto`() {
        val plain = "igual".toByteArray()

        assertNotEquals(cipher.encrypt(plain).toList(), cipher.encrypt(plain).toList())
    }

    @Test
    fun `un byte alterado se detecta`() {
        val data = cipher.encrypt("datos".toByteArray())
        data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()

        assertThrows(LocalDataCorruptedException::class.java) { cipher.decrypt(data) }
    }

    @Test
    fun `contenido demasiado corto es dañado`() {
        assertThrows(LocalDataCorruptedException::class.java) { cipher.decrypt(ByteArray(5)) }
    }

    @Test
    fun `el IV lo genera el cifrador y es de 12 bytes`() {
        val data = cipher.encrypt("datos".toByteArray())

        // IV (12) + texto cifrado de 5 bytes + etiqueta GCM de 16 bytes.
        assertEquals(12 + 5 + 16, data.size)
    }

    @Test
    fun `una llave invalida falla con IOException y no con otra excepcion`() {
        val broken = SecretKeyLocalCipher { SecretKeySpec(ByteArray(5), "AES") }

        assertThrows(IOException::class.java) { broken.encrypt("datos".toByteArray()) }
    }

    @Test
    fun `otra llave no puede descifrar`() {
        val data = cipher.encrypt("datos".toByteArray())

        assertThrows(LocalDataCorruptedException::class.java) { SecretKeyLocalCipher { newKey() }.decrypt(data) }
    }
}
