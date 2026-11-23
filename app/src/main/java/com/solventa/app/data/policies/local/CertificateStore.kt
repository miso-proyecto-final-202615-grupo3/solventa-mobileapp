package com.solventa.app.data.policies.local

import com.solventa.app.core.crypto.LocalCipher
import com.solventa.app.core.crypto.LocalDataCorruptedException
import java.io.File
import java.io.IOException

/** PDF del certificado de cada póliza, cifrado en el teléfono para consultarlo sin conexión (HU3.2.1). */
interface CertificateStore {
    fun has(policyId: String): Boolean

    /** El PDF, o `null` si no hay o está dañado. */
    fun load(policyId: String): ByteArray?

    fun save(policyId: String, pdf: ByteArray)

    /** Borra los certificados de las pólizas que ya no están en la billetera. */
    fun retainOnly(policyIds: Set<String>)

    fun clear()
}

class EncryptedFileCertificateStore(
    private val directory: File,
    private val cipher: LocalCipher,
) : CertificateStore {
    private val lock = Any()

    override fun has(policyId: String): Boolean = synchronized(lock) { fileFor(policyId)?.exists() == true }

    override fun load(policyId: String): ByteArray? = synchronized(lock) {
        val file = fileFor(policyId)?.takeIf { it.exists() } ?: return null
        try {
            cipher.decrypt(file.readBytes())
        } catch (_: LocalDataCorruptedException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun save(policyId: String, pdf: ByteArray) = synchronized(lock) {
        val file = fileFor(policyId) ?: return
        directory.mkdirs()
        val temp = File(directory, file.name + ".tmp")
        temp.writeBytes(cipher.encrypt(pdf))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IOException("No se pudo guardar el certificado.")
            }
        }
    }

    override fun retainOnly(policyIds: Set<String>) = synchronized(lock) {
        val keep = policyIds.filter(::isSafeId).map { "$it$EXTENSION" }.toSet()
        directory.listFiles()?.forEach { if (it.name !in keep) it.delete() }
        Unit
    }

    override fun clear() = synchronized(lock) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** El identificador viene del servidor y se usa como nombre de archivo: solo se aceptan UUID. */
    private fun fileFor(policyId: String): File? =
        if (isSafeId(policyId)) File(directory, "$policyId$EXTENSION") else null

    private fun isSafeId(id: String) = SAFE_ID.matches(id)

    private companion object {
        const val EXTENSION = ".cert"
        val SAFE_ID = Regex("^[A-Fa-f0-9-]{8,64}$")
    }
}
