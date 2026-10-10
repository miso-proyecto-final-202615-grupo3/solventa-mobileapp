package com.solventa.app.core.biometric

import com.solventa.app.data.auth.ClienteResumen
import com.solventa.app.data.auth.EstadoKyc
import com.solventa.app.data.auth.Session
import javax.crypto.Cipher

class InMemoryBiometricSettings : BiometricSettings {
    override var enabled: Boolean = false
    override var offerDeclined: Boolean = false
    override var failedAttempts: Int = 0
    override var lockoutUntilMillis: Long = 0
    override var sealedSession: SealedPayload? = null
}

/** Bóveda falsa: no usa Keystore, guarda la sesión en memoria. */
class FakeBiometricVault(
    var availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
) : BiometricVault {
    var sealed: Session? = null
    var enabled = false
    var keyInvalidated = false
    var failOnSealingCipher = false
    var disableCalls = 0

    private fun newCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    override fun availability() = availability
    override fun isEnabled() = enabled && sealed != null

    override fun cipherForSealing(): Cipher {
        if (failOnSealingCipher) error("Keystore no disponible")
        return newCipher()
    }

    override fun seal(cipher: Cipher, session: Session) {
        sealed = session
        enabled = true
    }

    override fun cipherForUnsealing(): Cipher {
        if (keyInvalidated || sealed == null) throw BiometricKeyInvalidatedException()
        return newCipher()
    }

    override fun unseal(cipher: Cipher): Session = checkNotNull(sealed)

    override fun disable() {
        disableCalls++
        enabled = false
        sealed = null
    }
}

class FakeClock(var now: Long = 1_000_000L) : () -> Long {
    override fun invoke(): Long = now
}

fun testSession(linked: Boolean = true) = Session(
    accessToken = "a",
    refreshToken = "r",
    expiresInSeconds = 3600,
    cliente = ClienteResumen("c-1", "Camila", EstadoKyc.VERIFICADO, dispositivoVinculado = linked),
)
