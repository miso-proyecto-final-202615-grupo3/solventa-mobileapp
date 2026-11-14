package com.solventa.app.data.claims.evidence

import com.solventa.app.core.location.LocationFix
import com.solventa.app.data.claims.RemoteClaimsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Serializable
internal data class UploadRequestDto(
    val tipo: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("tamano_bytes") val sizeBytes: Long,
    val origen: String,
    @SerialName("capturada_en") val capturedAt: String,
)

@Serializable
internal data class UploadTicketDto(
    val url: String,
    val campos: Map<String, String>,
    @SerialName("expira_en") val expiresAt: String,
    @SerialName("tamano_max_bytes") val maxBytes: Long,
)

@Serializable
internal data class UploadResponseDto(
    @SerialName("evidencia_id") val evidenceId: String,
    val estado: String,
    /** `null` cuando la evidencia de esa `Idempotency-Key` ya está `CARGADA`. */
    val carga: UploadTicketDto? = null,
)

@Serializable
internal data class LocationRequestDto(
    val latitud: Double,
    val longitud: Double,
    @SerialName("precision_m") val accuracyMeters: Double,
    val fuente: String,
    @SerialName("capturada_en") val capturedAt: String,
)

@Serializable
internal data class EvidenceErrorDto(val codigo: String? = null)

/**
 * Evidencias contra el BFF Móvil. Nunca registra la URL firmada, los campos de la política ni las coordenadas.
 */
class RemoteEvidenceRepository(
    baseUrl: String,
    private val client: OkHttpClient = RemoteClaimsRepository.defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val demoEmail: String? = null,
) : EvidenceRepository {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun requestUpload(
        accessToken: String,
        claimId: String,
        kind: EvidenceKind,
        sizeBytes: Long,
        origin: EvidenceOrigin,
        capturedAt: OffsetDateTime,
        idempotencyKey: String,
        correlationId: String,
    ): TicketResult = withContext(ioDispatcher) {
        val body = json.encodeToString(
            UploadRequestDto.serializer(),
            UploadRequestDto(kind.api, kind.mime, sizeBytes, origin.api, capturedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        ).toRequestBody(JSON)
        call({ TicketResult.NetworkError }, { TicketResult.ServiceUnavailable }) {
            val request = authorized("$baseUrl$CLAIMS/$claimId/evidencias", accessToken, correlationId)
                .header("Idempotency-Key", idempotencyKey)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val dto = json.decodeFromString(UploadResponseDto.serializer(), text)
                        val carga = dto.carga
                            ?: return@use if (dto.estado == "CARGADA") TicketResult.AlreadyUploaded else TicketResult.ServiceUnavailable
                        return@use TicketResult.Issued(
                            UploadTicket(dto.evidenceId, carga.url, carga.campos, Instant.parse(carga.expiresAt), carga.maxBytes),
                        )
                    }
                    val code = errorCode(text)
                    when {
                        response.code == 401 -> TicketResult.SessionExpired
                        response.code == 404 -> TicketResult.Refused(EvidenceProblem.CLAIM_CLOSED)
                        response.code == 409 -> TicketResult.Refused(
                            when (code) {
                                "LIMITE_EVIDENCIAS" -> EvidenceProblem.LIMIT_REACHED
                                "IDEMPOTENCIA_CONFLICTO" -> EvidenceProblem.REJECTED_BY_SERVER
                                else -> EvidenceProblem.CLAIM_CLOSED
                            },
                        )
                        response.code == 413 -> TicketResult.Refused(EvidenceProblem.TOO_LARGE)
                        // Reintento de una evidencia que el servidor ya había rechazado al verificarla.
                        response.code == 422 && code == "EVIDENCIA_RECHAZADA" -> TicketResult.Refused(EvidenceProblem.REJECTED_BY_SERVER)
                        response.code == 415 || response.code == 422 -> TicketResult.Refused(EvidenceProblem.NOT_ALLOWED)
                        else -> TicketResult.ServiceUnavailable
                    }
                }
        }
    }

    override suspend fun confirm(accessToken: String, claimId: String, evidenceId: String, correlationId: String): ConfirmResult =
        withContext(ioDispatcher) {
            val url = "$baseUrl$CLAIMS/$claimId/evidencias/$evidenceId/confirmacion"
            call({ ConfirmResult.NetworkError }, { ConfirmResult.ServiceUnavailable }) {
                client.newCall(authorized(url, accessToken, correlationId).post(ByteArray(0).toRequestBody(null)).build())
                    .execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        when {
                            response.isSuccessful -> ConfirmResult.Confirmed
                            response.code == 401 -> ConfirmResult.SessionExpired
                            response.code == 409 && errorCode(text) == "CARGA_NO_ENCONTRADA" -> ConfirmResult.NotUploadedYet
                            response.code == 409 && errorCode(text) == "CARGA_EXPIRADA" -> ConfirmResult.Expired
                            response.code == 422 || response.code == 404 -> ConfirmResult.Rejected
                            else -> ConfirmResult.ServiceUnavailable
                        }
                    }
            }
        }

    override suspend fun saveLocation(accessToken: String, claimId: String, fix: LocationFix, correlationId: String): LocationSaveResult =
        withContext(ioDispatcher) {
            val body = json.encodeToString(
                LocationRequestDto.serializer(),
                LocationRequestDto(
                    latitud = fix.latitude,
                    longitud = fix.longitude,
                    accuracyMeters = fix.accuracyMeters.toDouble(),
                    fuente = fix.source.api,
                    capturedAt = fix.capturedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
            ).toRequestBody(JSON)
            call({ LocationSaveResult.NetworkError }, { LocationSaveResult.ServiceUnavailable }) {
                client.newCall(authorized("$baseUrl$CLAIMS/$claimId/ubicacion", accessToken, correlationId).put(body).build())
                    .execute().use { response ->
                        when {
                            response.isSuccessful -> LocationSaveResult.Saved
                            response.code == 401 -> LocationSaveResult.SessionExpired
                            response.code == 409 || response.code == 404 -> LocationSaveResult.ClaimClosed
                            else -> LocationSaveResult.ServiceUnavailable
                        }
                    }
            }
        }

    private inline fun <T> call(network: () -> T, unavailable: () -> T, block: () -> T): T = try {
        block()
    } catch (_: IOException) {
        network()
    } catch (_: SerializationException) {
        unavailable()
    } catch (_: IllegalArgumentException) {
        unavailable()
    } catch (_: DateTimeParseException) {
        unavailable()
    }

    private fun authorized(url: String, accessToken: String, correlationId: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Correlation-Id", correlationId)
            .apply { demoEmail?.takeIf { it.isNotBlank() }?.let { header("X-Demo-Correo", it) } }

    private fun errorCode(text: String): String? = try {
        json.decodeFromString(EvidenceErrorDto.serializer(), text).codigo
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val CLAIMS = "/api/v1/bff-mobile/siniestros"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
