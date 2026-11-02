package com.solventa.app.data.consent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BiometricConsentStatus(val granted: Boolean, val termsVersion: String, val acceptedVersion: String?)

enum class ConsentDecision(val code: String) { ACCEPTED("ACEPTADO"), REJECTED("RECHAZADO") }

sealed interface ConsentStatusResult {
    data class Loaded(val status: BiometricConsentStatus) : ConsentStatusResult
    data object SessionExpired : ConsentStatusResult
    data object NetworkError : ConsentStatusResult
    data object ServiceUnavailable : ConsentStatusResult
}

sealed interface ConsentResult {
    data class Recorded(val decision: ConsentDecision, val recordedAt: String, val termsVersion: String) : ConsentResult
    data object OutdatedTerms : ConsentResult
    data object SessionExpired : ConsentResult
    data object NetworkError : ConsentResult
    data object ServiceUnavailable : ConsentResult
}

/**
 * Consentimiento biométrico (parte móvil de HU3.1.3). El endpoint lo entrega HU3.1.3 (asr88);
 * mientras tanto se usa [MockBiometricConsentRepository] (excepción transitoria D5).
 */
interface BiometricConsentRepository {
    suspend fun getStatus(accessToken: String): ConsentStatusResult

    /** Registra la decisión, también el rechazo, con fecha, hora y versión del texto. */
    suspend fun record(
        accessToken: String,
        decision: ConsentDecision,
        termsVersion: String,
        installationId: String,
    ): ConsentResult
}

/** Simulación con la misma forma que el contrato 4.3: decisión en memoria por sesión. */
class MockBiometricConsentRepository(
    private val currentEmail: () -> String?,
    private val termsVersion: String = "1.0",
    private val latencyMillis: Long = 400,
) : BiometricConsentRepository {
    private val accepted = mutableMapOf<String, String>()

    override suspend fun getStatus(accessToken: String): ConsentStatusResult {
        delay(latencyMillis)
        if (currentEmail()?.lowercase() == SERVICE_UNAVAILABLE_EMAIL) return ConsentStatusResult.ServiceUnavailable
        val version = accepted[key()]
        return ConsentStatusResult.Loaded(BiometricConsentStatus(version == termsVersion, termsVersion, version))
    }

    override suspend fun record(
        accessToken: String,
        decision: ConsentDecision,
        termsVersion: String,
        installationId: String,
    ): ConsentResult {
        delay(latencyMillis)
        if (currentEmail()?.lowercase() == SERVICE_UNAVAILABLE_EMAIL) return ConsentResult.ServiceUnavailable
        if (termsVersion != this.termsVersion) return ConsentResult.OutdatedTerms
        if (decision == ConsentDecision.ACCEPTED) accepted[key()] = termsVersion else accepted.remove(key())
        return ConsentResult.Recorded(decision, Instant.now().toString(), termsVersion)
    }

    private fun key() = currentEmail().orEmpty().lowercase()

    companion object {
        const val SERVICE_UNAVAILABLE_EMAIL = "sin.servicio@correo.com"
    }
}

@Serializable
internal data class ConsentStatusDto(
    val otorgado: Boolean,
    @SerialName("version_vigente") val currentVersion: String,
    @SerialName("version_aceptada") val acceptedVersion: String? = null,
)

@Serializable
internal data class ConsentRequestDto(
    val decision: String,
    @SerialName("version_terminos") val termsVersion: String,
    @SerialName("installation_id") val installationId: String,
    @SerialName("decidido_en") val decidedAt: String,
)

@Serializable
internal data class ConsentResponseDto(
    val decision: String,
    @SerialName("version_terminos") val termsVersion: String,
    @SerialName("registrado_en") val recordedAt: String,
)

/** Contrato 4.3 del plan de la app. No se conecta hasta que HU3.1.3 entregue el endpoint. */
class RemoteBiometricConsentRepository(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = Instant::now,
) : BiometricConsentRepository {
    private val url = baseUrl.trimEnd('/') + PATH

    override suspend fun getStatus(accessToken: String): ConsentStatusResult = withContext(ioDispatcher) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val dto = json.decodeFromString(ConsentStatusDto.serializer(), response.body?.string().orEmpty())
                        ConsentStatusResult.Loaded(BiometricConsentStatus(dto.otorgado, dto.currentVersion, dto.acceptedVersion))
                    }
                    response.code == 401 -> ConsentStatusResult.SessionExpired
                    else -> ConsentStatusResult.ServiceUnavailable
                }
            }
        } catch (_: IOException) {
            ConsentStatusResult.NetworkError
        } catch (_: SerializationException) {
            ConsentStatusResult.ServiceUnavailable
        } catch (_: IllegalArgumentException) {
            ConsentStatusResult.ServiceUnavailable
        }
    }

    override suspend fun record(
        accessToken: String,
        decision: ConsentDecision,
        termsVersion: String,
        installationId: String,
    ): ConsentResult = withContext(ioDispatcher) {
        val body = json.encodeToString(
            ConsentRequestDto.serializer(),
            ConsentRequestDto(decision.code, termsVersion, installationId, clock().toString()),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val dto = json.decodeFromString(ConsentResponseDto.serializer(), response.body?.string().orEmpty())
                        ConsentResult.Recorded(decision, dto.recordedAt, dto.termsVersion)
                    }
                    response.code == 401 -> ConsentResult.SessionExpired
                    response.code == 409 -> ConsentResult.OutdatedTerms
                    else -> ConsentResult.ServiceUnavailable
                }
            }
        } catch (_: IOException) {
            ConsentResult.NetworkError
        } catch (_: SerializationException) {
            ConsentResult.ServiceUnavailable
        } catch (_: IllegalArgumentException) {
            ConsentResult.ServiceUnavailable
        }
    }

    companion object {
        const val PATH = "/api/v1/bff-mobile/consentimientos/biometrico"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
