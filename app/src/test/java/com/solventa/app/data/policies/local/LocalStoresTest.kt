package com.solventa.app.data.policies.local

import com.solventa.app.data.policies.WalletSnapshot
import com.solventa.app.data.policies.testCipher
import com.solventa.app.data.policies.testPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class LocalStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cipher = testCipher()
    private val syncedAt = Instant.parse("2026-11-18T14:05:00Z")

    private fun walletFile() = File(tmp.root, "billetera/polizas.bin")
    private fun walletStore() = EncryptedFileWalletStore(walletFile(), cipher)
    private fun certificates() = EncryptedFileCertificateStore(File(tmp.root, "billetera/certificados"), cipher)

    // --- Billetera ---------------------------------------------------------------------------

    @Test
    fun `sin archivo no hay copia`() {
        assertNull(walletStore().load())
    }

    @Test
    fun `guarda y lee la copia completa`() {
        val snapshot = WalletSnapshot("c-1", listOf(testPolicy(), testPolicy("2", "CANCELADA", "SLV-2")), syncedAt)

        walletStore().save(snapshot)

        assertEquals(snapshot, walletStore().load())
    }

    @Test
    fun `el archivo en disco esta cifrado`() {
        walletStore().save(WalletSnapshot("c-1", listOf(testPolicy()), syncedAt))

        val raw = walletFile().readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(raw.contains("SLV-2026-08341"))
        assertFalse(raw.contains("Seguro de vida hipotecario"))
    }

    @Test
    fun `guardar reemplaza la copia anterior`() {
        val store = walletStore()
        store.save(WalletSnapshot("c-1", listOf(testPolicy()), syncedAt))
        store.save(WalletSnapshot("c-1", listOf(testPolicy("2", "CANCELADA")), syncedAt.plusSeconds(60)))

        val loaded = store.load()!!

        assertEquals(listOf("2"), loaded.policies.map { it.id })
        assertEquals("CANCELADA", loaded.policies.single().status)
        assertEquals(syncedAt.plusSeconds(60), loaded.syncedAt)
    }

    @Test
    fun `una copia danada se trata como inexistente`() {
        walletStore().save(WalletSnapshot("c-1", listOf(testPolicy()), syncedAt))
        walletFile().writeBytes(walletFile().readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })

        assertNull(walletStore().load())
    }

    @Test
    fun `una copia cifrada con otra llave se trata como inexistente`() {
        walletStore().save(WalletSnapshot("c-1", listOf(testPolicy()), syncedAt))

        assertNull(EncryptedFileWalletStore(walletFile(), testCipher()).load())
    }

    @Test
    fun `no queda archivo temporal tras guardar`() {
        walletStore().save(WalletSnapshot("c-1", listOf(testPolicy()), syncedAt))

        assertEquals(listOf("polizas.bin"), walletFile().parentFile!!.list()!!.toList())
    }

    // --- Certificados ------------------------------------------------------------------------

    @Test
    fun `guarda y lee un certificado cifrado`() {
        val pdf = "%PDF-1.4 contenido".toByteArray()
        val store = certificates()

        store.save("8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10", pdf)

        assertTrue(store.has("8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10"))
        assertArrayEquals(pdf, store.load("8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10"))
        val onDisk = File(tmp.root, "billetera/certificados").listFiles()!!.single().readBytes()
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("%PDF"))
    }

    @Test
    fun `un certificado danado devuelve null`() {
        val store = certificates()
        store.save("8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10", "pdf".toByteArray())
        File(tmp.root, "billetera/certificados").listFiles()!!.single().writeBytes(ByteArray(40))

        assertNull(store.load("8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10"))
    }

    @Test
    fun `solo conserva los certificados de las polizas vigentes en la billetera`() {
        val store = certificates()
        store.save("aaaaaaaa-0000-4000-8000-000000000001", "a".toByteArray())
        store.save("aaaaaaaa-0000-4000-8000-000000000002", "b".toByteArray())

        store.retainOnly(setOf("aaaaaaaa-0000-4000-8000-000000000002"))

        assertFalse(store.has("aaaaaaaa-0000-4000-8000-000000000001"))
        assertTrue(store.has("aaaaaaaa-0000-4000-8000-000000000002"))
    }

    @Test
    fun `un identificador con ruta no escribe fuera de la carpeta`() {
        val store = certificates()

        store.save("../../escape", "x".toByteArray())

        assertFalse(store.has("../../escape"))
        assertNull(store.load("../../escape"))
        assertFalse(File(tmp.root, "escape.cert").exists())
    }

    @Test
    fun `clear borra todos los certificados`() {
        val store = certificates()
        store.save("aaaaaaaa-0000-4000-8000-000000000001", "a".toByteArray())

        store.clear()

        assertFalse(store.has("aaaaaaaa-0000-4000-8000-000000000001"))
    }
}
