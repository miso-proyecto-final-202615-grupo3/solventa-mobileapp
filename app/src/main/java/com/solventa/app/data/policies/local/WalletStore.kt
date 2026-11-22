package com.solventa.app.data.policies.local

import com.solventa.app.core.crypto.LocalCipher
import com.solventa.app.core.crypto.LocalDataCorruptedException
import com.solventa.app.data.policies.Coverage
import com.solventa.app.data.policies.Policy
import com.solventa.app.data.policies.WalletSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant

/** Copia local cifrada de la billetera (HU3.2.1). */
interface WalletStore {
    /** La copia guardada, o `null` si no hay o está dañada (se trata como si no existiera). */
    fun load(): WalletSnapshot?

    /** Reemplaza la copia completa; si falla, la anterior queda intacta. */
    fun save(snapshot: WalletSnapshot)
}

@Serializable
private data class StoredCoverage(val id: String, val name: String?, val limit: String, val deductible: String)

@Serializable
private data class StoredPolicy(
    val id: String,
    val number: String,
    val status: String,
    val branch: String,
    val productCode: String?,
    val productName: String?,
    val validFrom: String,
    val validTo: String,
    val issuedAt: String?,
    val totalPremium: String,
    val coverages: List<StoredCoverage>,
)

@Serializable
private data class StoredWallet(val version: Int, val clientId: String, val syncedAt: String, val policies: List<StoredPolicy>)

/**
 * Archivo JSON cifrado con [LocalCipher]. La escritura es atómica (archivo temporal y renombrado):
 * una sincronización interrumpida nunca deja una copia a medias.
 */
class EncryptedFileWalletStore(
    private val file: File,
    private val cipher: LocalCipher,
) : WalletStore {
    private val lock = Any()

    override fun load(): WalletSnapshot? = synchronized(lock) {
        if (!file.exists()) return null
        try {
            val stored = json.decodeFromString(StoredWallet.serializer(), cipher.decrypt(file.readBytes()).decodeToString())
            if (stored.version != VERSION) return null
            WalletSnapshot(stored.clientId, stored.policies.map(::toPolicy), Instant.parse(stored.syncedAt))
        } catch (_: LocalDataCorruptedException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.time.format.DateTimeParseException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun save(snapshot: WalletSnapshot) = synchronized(lock) {
        val stored = StoredWallet(VERSION, snapshot.clientId, snapshot.syncedAt.toString(), snapshot.policies.map(::toStored))
        val encrypted = cipher.encrypt(json.encodeToString(StoredWallet.serializer(), stored).encodeToByteArray())
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(encrypted)
        if (!temp.renameTo(file)) {
            // Algunos sistemas de archivos no reemplazan al renombrar: se borra el destino y se reintenta.
            file.delete()
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IOException("No se pudo guardar la billetera.")
            }
        }
    }

    private fun toStored(policy: Policy) = StoredPolicy(
        id = policy.id,
        number = policy.number,
        status = policy.status,
        branch = policy.branch,
        productCode = policy.productCode,
        productName = policy.productName,
        validFrom = policy.validFrom.toString(),
        validTo = policy.validTo.toString(),
        issuedAt = policy.issuedAt?.toString(),
        totalPremium = policy.totalPremium,
        coverages = policy.coverages.map { StoredCoverage(it.id, it.name, it.insuredLimit, it.deductible) },
    )

    private fun toPolicy(stored: StoredPolicy) = Policy(
        id = stored.id,
        number = stored.number,
        status = stored.status,
        branch = stored.branch,
        productCode = stored.productCode,
        productName = stored.productName,
        validFrom = Instant.parse(stored.validFrom),
        validTo = Instant.parse(stored.validTo),
        issuedAt = stored.issuedAt?.let(Instant::parse),
        totalPremium = stored.totalPremium,
        coverages = stored.coverages.map { Coverage(it.id, it.name, it.limit, it.deductible) },
    )

    private companion object {
        const val VERSION = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}
