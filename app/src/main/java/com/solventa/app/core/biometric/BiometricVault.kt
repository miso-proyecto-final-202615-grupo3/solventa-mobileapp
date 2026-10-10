package com.solventa.app.core.biometric

import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.SessionCodec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Guarda la sesión cifrada con una llave que solo se libera tras validar la biometría.
 * Así, desbloquear la app con huella o rostro equivale a recuperar la sesión.
 */
interface BiometricVault {
    fun availability(): BiometricAvailability
    fun isEnabled(): Boolean

    /** Cipher que se entrega al aviso biométrico para activar el acceso. */
    fun cipherForSealing(): Cipher

    /** Cifra la sesión con el cipher ya autenticado y deja activo el acceso biométrico. */
    fun seal(cipher: Cipher, session: Session)

    /** @throws BiometricKeyInvalidatedException si la biometría del teléfono cambió. */
    fun cipherForUnsealing(): Cipher

    fun unseal(cipher: Cipher): Session

    /** Desactiva el acceso biométrico y borra la llave y la sesión cifrada. */
    fun disable()
}

class KeystoreBiometricVault(
    private val keyManager: BiometricKeyManager,
    private val settings: BiometricSettings,
    private val availabilityChecker: BiometricAvailabilityChecker,
) : BiometricVault {

    override fun availability(): BiometricAvailability = availabilityChecker.check()

    override fun isEnabled(): Boolean = settings.enabled && settings.sealedSession != null

    override fun cipherForSealing(): Cipher = keyManager.encryptCipher()

    override fun seal(cipher: Cipher, session: Session) {
        val ciphertext = cipher.doFinal(SessionCodec.encode(session))
        settings.sealedSession = SealedPayload(
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
            iv = Base64.getEncoder().encodeToString(cipher.iv),
        )
        settings.enabled = true
        settings.offerDeclined = false
    }

    override fun cipherForUnsealing(): Cipher {
        val sealed = settings.sealedSession ?: throw BiometricKeyInvalidatedException()
        return keyManager.decryptCipher(Base64.getDecoder().decode(sealed.iv))
    }

    override fun unseal(cipher: Cipher): Session {
        val sealed = checkNotNull(settings.sealedSession) { "No hay sesión cifrada" }
        return SessionCodec.decode(cipher.doFinal(Base64.getDecoder().decode(sealed.ciphertext)))
    }

    override fun disable() {
        settings.enabled = false
        settings.sealedSession = null
        keyManager.deleteKey()
    }
}
