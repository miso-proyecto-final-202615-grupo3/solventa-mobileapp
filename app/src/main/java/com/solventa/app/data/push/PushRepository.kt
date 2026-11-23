package com.solventa.app.data.push

/** Registro del destino push; el servidor determina el cliente a partir del JWT. */
interface PushRepository {
    suspend fun register(accessToken: String, installationId: String, token: String): PushResult
    suspend fun unregister(accessToken: String, installationId: String): PushResult
}

sealed interface PushResult {
    data object Registered : PushResult
    data object DeviceNotLinked : PushResult
    data object SessionExpired : PushResult
    data object NetworkError : PushResult
    data object ServiceUnavailable : PushResult
}

class MockPushRepository : PushRepository {
    val registrations = mutableListOf<Pair<String, String>>()
    override suspend fun register(accessToken: String, installationId: String, token: String): PushResult {
        registrations += installationId to token.takeLast(6)
        return PushResult.Registered
    }
    override suspend fun unregister(accessToken: String, installationId: String): PushResult = PushResult.Registered
}
