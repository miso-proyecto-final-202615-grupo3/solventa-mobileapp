package com.solventa.app.data.claims

import com.solventa.app.core.location.LocationFix
import com.solventa.app.core.location.LocationSource
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.RemoteEvidence
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
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
internal data class EvidenceRulesDto(@SerialName("fotos_max") val maxPhotos: Int, @SerialName("videos_max") val maxVideos: Int)

@Serializable
internal data class ClaimTypeDto(val codigo: String, val nombre: String, val evidencia: EvidenceRulesDto)

@Serializable
internal data class ProductDto(val codigo: String, val nombre: String)

@Serializable
internal data class PolicyDto(
    @SerialName("poliza_id") val policyId: String,
    val numero: String,
    val producto: ProductDto,
    @SerialName("vigencia_desde") val validFrom: String,
    @SerialName("vigencia_hasta") val validTo: String,
    @SerialName("tipos_siniestro") val claimTypes: List<ClaimTypeDto>,
)

@Serializable
internal data class EligiblePoliciesResponseDto(val polizas: List<PolicyDto>)

@Serializable
internal data class ReportRequestDto(
    @SerialName("poliza_id") val policyId: String,
    val tipo: String,
    val descripcion: String,
    @SerialName("ocurrido_en") val occurredAt: String,
)

@Serializable
internal data class ClaimResponseDto(
    @SerialName("siniestro_id") val claimId: String,
    val numero: String,
    val estado: String,
    @SerialName("poliza_id") val policyId: String,
    @SerialName("poliza_numero") val policyNumber: String? = null,
    val tipo: String,
    @SerialName("ocurrido_en") val occurredAt: String,
    @SerialName("reportado_en") val reportedAt: String,
    val ubicacion: LocationDto? = null,
    val evidencias: List<EvidenceDto> = emptyList(),
)

@Serializable
internal data class LocationDto(
    val latitud: Double,
    val longitud: Double,
    @SerialName("precision_m") val accuracyMeters: Double,
    val fuente: String,
    @SerialName("capturada_en") val capturedAt: String,
)

@Serializable
internal data class EvidenceDto(
    @SerialName("evidencia_id") val evidenceId: String,
    val tipo: String,
    val estado: String,
    @SerialName("tamano_bytes") val sizeBytes: Long? = null,
    @SerialName("capturada_en") val capturedAt: String,
    @SerialName("cargada_en") val uploadedAt: String? = null,
)

@Serializable
internal data class ClaimsErrorDto(val codigo: String? = null)

/**
 * Siniestros contra el BFF Móvil (contrato canónico, plan del backend de HU4.1.1 §3).
 *
 * Envía `Accept-Language` para recibir los nombres de los tipos en el idioma del teléfono.
 * Nunca registra la descripción ni el token. [demoEmail] solo se usa en debug (modo demo del BFF sin JWT).
 */
class RemoteClaimsRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val demoEmail: String? = null,
    private val languageTag: () -> String = { Locale.getDefault().toLanguageTag() },
) : ClaimsRepository {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun eligiblePolicies(accessToken: String, correlationId: String): PoliciesResult =
        withContext(ioDispatcher) {
            val request = authorized(baseUrl + POLICIES_PATH, accessToken, correlationId).get().build()
            try {
                client.newCall(request).execute().use { response -> parsePolicies(response) }
            } catch (_: IOException) {
                PoliciesResult.NetworkError
            } catch (_: SerializationException) {
                PoliciesResult.ServiceUnavailable
            } catch (_: IllegalArgumentException) {
                PoliciesResult.ServiceUnavailable
            } catch (_: DateTimeParseException) {
                PoliciesResult.ServiceUnavailable
            }
        }

    override suspend fun report(
        accessToken: String,
        draft: ClaimDraft,
        idempotencyKey: String,
        correlationId: String,
    ): ReportResult = withContext(ioDispatcher) {
        val body = json.encodeToString(
            ReportRequestDto.serializer(),
            ReportRequestDto(
                policyId = draft.policyId,
                tipo = draft.typeCode,
                descripcion = draft.description,
                occurredAt = draft.occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            ),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = authorized(baseUrl + CLAIMS_PATH, accessToken, correlationId)
            .header("Idempotency-Key", idempotencyKey)
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response -> parseReport(response) }
        } catch (_: IOException) {
            ReportResult.NetworkError
        } catch (_: SerializationException) {
            ReportResult.ServiceUnavailable
        } catch (_: IllegalArgumentException) {
            ReportResult.ServiceUnavailable
        } catch (_: DateTimeParseException) {
            ReportResult.ServiceUnavailable
        }
    }

    override suspend fun claim(accessToken: String, claimId: String, correlationId: String): ClaimResult =
        withContext(ioDispatcher) {
            val request = authorized("$baseUrl$CLAIMS_PATH/$claimId", accessToken, correlationId).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> toClaim(json.decodeFromString(ClaimResponseDto.serializer(), text))
                            ?.let { ClaimResult.Found(it) } ?: ClaimResult.ServiceUnavailable
                        response.code == 401 -> ClaimResult.SessionExpired
                        response.code == 404 || response.code == 422 -> ClaimResult.NotFound
                        else -> ClaimResult.ServiceUnavailable
                    }
                }
            } catch (_: IOException) {
                ClaimResult.NetworkError
            } catch (_: SerializationException) {
                ClaimResult.ServiceUnavailable
            } catch (_: IllegalArgumentException) {
                ClaimResult.ServiceUnavailable
            } catch (_: DateTimeParseException) {
                ClaimResult.ServiceUnavailable
            }
        }

    private fun authorized(url: String, accessToken: String, correlationId: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Correlation-Id", correlationId)
            .header("Accept-Language", languageTag())
            .apply { demoEmail?.takeIf { it.isNotBlank() }?.let { header("X-Demo-Correo", it) } }

    private fun parsePolicies(response: Response): PoliciesResult {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            val dto = json.decodeFromString(EligiblePoliciesResponseDto.serializer(), text)
            return PoliciesResult.Loaded(dto.polizas.map(::toPolicy))
        }
        return when {
            response.code == 401 -> PoliciesResult.SessionExpired
            response.code == 404 && errorCode(text) == "CLIENTE_NO_REGISTRADO" -> PoliciesResult.ClientNotRegistered
            else -> PoliciesResult.ServiceUnavailable
        }
    }

    private fun parseReport(response: Response): ReportResult {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            val claim = toClaim(json.decodeFromString(ClaimResponseDto.serializer(), text))
                ?: return ReportResult.ServiceUnavailable
            return ReportResult.Reported(claim, replayed = response.code == 200)
        }
        val code = errorCode(text)
        return when (response.code) {
            401 -> ReportResult.SessionExpired
            404 -> if (code == "CLIENTE_NO_REGISTRADO") ReportResult.ClientNotRegistered else ReportResult.PolicyNotFound
            409 -> when (code) {
                "POLIZA_NO_VIGENTE" -> ReportResult.PolicyNotActive
                "FECHA_FUERA_DE_VIGENCIA" -> ReportResult.OutOfCoverage
                "IDEMPOTENCIA_CONFLICTO" -> ReportResult.IdempotencyConflict
                else -> ReportResult.ServiceUnavailable
            }
            400, 422 -> when (code) {
                "FECHA_INVALIDA" -> ReportResult.InvalidDate
                "TIPO_NO_PERMITIDO" -> ReportResult.TypeNotAllowed
                else -> ReportResult.InvalidData
            }
            else -> ReportResult.ServiceUnavailable
        }
    }

    private fun toPolicy(dto: PolicyDto) = EligiblePolicy(
        id = dto.policyId,
        number = dto.numero,
        productCode = dto.producto.codigo,
        productName = dto.producto.nombre,
        validFrom = LocalDate.parse(dto.validFrom),
        validTo = LocalDate.parse(dto.validTo),
        claimTypes = dto.claimTypes.map {
            ClaimType(it.codigo, it.nombre, EvidenceRules(it.evidencia.maxPhotos, it.evidencia.maxVideos))
        },
    )

    /** `null` si el estado es desconocido: la UI lo trata como servicio no disponible. */
    private fun toClaim(dto: ClaimResponseDto): ReportedClaim? {
        val status = ClaimStatus.fromCode(dto.estado) ?: return null
        return ReportedClaim(
            id = dto.claimId,
            number = dto.numero,
            status = status,
            policyId = dto.policyId,
            policyNumber = dto.policyNumber.orEmpty(),
            typeCode = dto.tipo,
            occurredAt = Instant.parse(dto.occurredAt),
            reportedAt = Instant.parse(dto.reportedAt),
            location = dto.ubicacion?.let {
                LocationFix(
                    latitude = it.latitud,
                    longitude = it.longitud,
                    accuracyMeters = it.accuracyMeters.toFloat(),
                    capturedAt = Instant.parse(it.capturedAt),
                    source = LocationSource.entries.firstOrNull { s -> s.api == it.fuente } ?: LocationSource.NETWORK,
                )
            },
            evidences = dto.evidencias.map {
                RemoteEvidence(
                    id = it.evidenceId,
                    kind = EvidenceKind.entries.firstOrNull { k -> k.api == it.tipo },
                    state = it.estado,
                    sizeBytes = it.sizeBytes,
                    capturedAt = Instant.parse(it.capturedAt),
                    uploadedAt = it.uploadedAt?.let(Instant::parse),
                )
            },
        )
    }

    private fun errorCode(text: String): String? = try {
        json.decodeFromString(ClaimsErrorDto.serializer(), text).codigo
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val POLICIES_PATH = "/api/v1/bff-mobile/siniestros/polizas-elegibles"
        const val CLAIMS_PATH = "/api/v1/bff-mobile/siniestros"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
