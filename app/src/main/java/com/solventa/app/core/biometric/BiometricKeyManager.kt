package com.solventa.app.core.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** La biometría del teléfono cambió (se agregó o borró una huella o rostro) y la llave quedó inválida. */
class BiometricKeyInvalidatedException : Exception()

/**
 * Llave AES en Android Keystore que solo se puede usar tras una autenticación biométrica fuerte.
 * Se invalida automáticamente si cambia la biometría registrada en el sistema operativo.
 */
class BiometricKeyManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Cipher para cifrar la sesión al activar la biometría. Regenera la llave si quedó inválida. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        return cipher
    }

    /** Cipher para descifrar la sesión al desbloquear. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: throw BiometricKeyInvalidatedException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            throw BiometricKeyInvalidatedException()
        }
        return cipher
    }

    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "solventa_biometric_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
