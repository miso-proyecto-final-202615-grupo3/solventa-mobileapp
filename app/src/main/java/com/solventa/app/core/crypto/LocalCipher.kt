package com.solventa.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** El contenido cifrado está dañado, fue alterado o la llave ya no existe. */
class LocalDataCorruptedException(cause: Throwable? = null) : Exception(cause)

/**
 * Cifrado en reposo de los datos que la app guarda en el teléfono (billetera de pólizas, HU3.2.1).
 * Formato: `IV (12 bytes) || texto cifrado AES-GCM`. Cualquier alteración se detecta al descifrar.
 */
interface LocalCipher {
    /** @throws IOException si la llave no permite cifrar (por ejemplo, Keystore no disponible). */
    fun encrypt(plain: ByteArray): ByteArray

    /** @throws LocalDataCorruptedException si el contenido no se puede descifrar. */
    fun decrypt(data: ByteArray): ByteArray
}

/** Implementación con una llave AES dada; la usa [KeystoreLocalCipher] y las pruebas locales. */
open class SecretKeyLocalCipher(private val key: () -> SecretKey) : LocalCipher {
    override fun encrypt(plain: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Sin IV propio: Android Keystore exige generarlo él mismo (cifrado aleatorizado) y lo rechaza si se lo damos.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.iv + cipher.doFinal(plain)
    } catch (e: GeneralSecurityException) {
        throw IOException("No se pudo cifrar el contenido.", e)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (data.size <= IV_BYTES) throw LocalDataCorruptedException()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw LocalDataCorruptedException(e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Llave AES-256 no exportable en Android Keystore, **sin** exigir biometría en cada uso: la billetera debe
 * poder leerse sin conexión (la biometría ya protege la entrada a la app, HU3.1.2). Es distinta de la llave
 * del acceso biométrico, así que activar o desactivar la biometría no borra la billetera.
 */
class KeystoreLocalCipher : SecretKeyLocalCipher(::keystoreKey) {
    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "solventa_local_data_key"

        fun keystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        }
    }
}
