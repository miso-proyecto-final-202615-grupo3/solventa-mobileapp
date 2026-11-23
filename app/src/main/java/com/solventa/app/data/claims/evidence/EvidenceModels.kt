package com.solventa.app.data.claims.evidence

import kotlinx.serialization.Serializable
import java.time.Instant

enum class EvidenceKind(val api: String, val mime: String, val extension: String) {
    PHOTO("FOTO", "image/jpeg", "jpg"),
    VIDEO("VIDEO", "video/mp4", "mp4"),
}

enum class EvidenceOrigin(val api: String) { CAMERA("CAMARA"), GALLERY("GALERIA") }

/** Estados de una evidencia en la app (máquina de estados de la cola de envío). */
enum class EvidenceUploadState { QUEUED, REQUESTING, UPLOADING, CONFIRMING, UPLOADED, FAILED, REJECTED }

enum class EvidenceProblem {
    NETWORK,
    SERVICE_UNAVAILABLE,
    SESSION_EXPIRED,
    LIMIT_REACHED,
    TOO_LARGE,
    NOT_ALLOWED,
    REJECTED_BY_SERVER,
    CLAIM_CLOSED,
    FILE_MISSING,
}

/**
 * Evidencia local pendiente de envío. Se guarda en `pendientes.json` sin la URL firmada ni sus campos
 * (si hace falta, se pide otra carga): solo [remoteId].
 */
@Serializable
data class LocalEvidence(
    val localId: String,
    val claimId: String,
    val claimNumber: String,
    val kind: EvidenceKind,
    val origin: EvidenceOrigin,
    val filePath: String?,
    val sizeBytes: Long,
    val capturedAt: String,
    val remoteId: String? = null,
    val state: EvidenceUploadState = EvidenceUploadState.QUEUED,
    val attempts: Int = 0,
    val problem: EvidenceProblem? = null,
    @kotlinx.serialization.Transient val progress: Float = 0f,
) {
    val pending: Boolean get() = state != EvidenceUploadState.UPLOADED && state != EvidenceUploadState.REJECTED
}

/** Carga prefirmada del backend: el archivo va directo al almacenamiento (Valet Key). */
data class UploadTicket(
    val evidenceId: String,
    val url: String,
    val fields: Map<String, String>,
    val expiresAt: Instant,
    val maxBytes: Long,
)

/** Evidencia ya registrada en el backend (detalle del siniestro). */
data class RemoteEvidence(
    val id: String,
    val kind: EvidenceKind?,
    val state: String,
    val sizeBytes: Long?,
    val capturedAt: Instant,
    val uploadedAt: Instant?,
)
