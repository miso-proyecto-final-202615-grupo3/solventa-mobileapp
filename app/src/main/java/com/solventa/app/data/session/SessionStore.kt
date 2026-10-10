package com.solventa.app.data.session

import com.solventa.app.data.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionStore {
    val session: StateFlow<Session?>
    fun save(session: Session)
    fun clear()
}

/**
 * Sesión solo en memoria: se pierde al cerrar la app.
 * Con HU3.1.2 se reemplaza por almacenamiento cifrado con Android Keystore,
 * donde el refresh token queda protegido por la autenticación biométrica.
 */
class InMemorySessionStore : SessionStore {
    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override fun save(session: Session) {
        _session.value = session
    }

    override fun clear() {
        _session.value = null
    }
}
