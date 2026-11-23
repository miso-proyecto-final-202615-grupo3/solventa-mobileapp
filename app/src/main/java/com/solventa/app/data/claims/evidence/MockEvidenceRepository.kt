package com.solventa.app.data.claims.evidence

import com.solventa.app.core.location.LocationFix
import com.solventa.app.data.claims.EvidenceRules
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Backend de evidencias simulado: aplica las reglas del tipo de siniestro ([rulesFor]) y emite cargas falsas.
 * Regla de demostración: las primeras 2 confirmaciones de cada evidencia número 3 fallan por red, para
 * ver el reintento automático en la pantalla.
 */
class MockEvidenceRepository(
    private val rulesFor: (claimId: String) -> EvidenceRules = { EvidenceRules(5, 1) },
    private val clock: Clock = Clock.systemUTC(),
    private val latencyMs: Long = 400,
) : EvidenceRepository {
    private val issued = mutableMapOf<String, MutableList<Pair<String, EvidenceKind>>>()
    private val failures = mutableMapOf<String, Int>()
    private val byKey = mutableMapOf<String, String>()
    private val uploaded = mutableSetOf<String>()
    val locations = mutableMapOf<String, LocationFix>()

    override suspend fun requestUpload(
        accessToken: String,
        claimId: String,
        kind: EvidenceKind,
        sizeBytes: Long,
        origin: EvidenceOrigin,
        capturedAt: OffsetDateTime,
        idempotencyKey: String,
        correlationId: String,
    ): TicketResult {
        delay(latencyMs)
        val rules = rulesFor(claimId)
        val existing = issued.getOrPut(claimId) { mutableListOf() }
        // Misma clave: la misma evidencia con una carga nueva (como el backend real).
        byKey[idempotencyKey]?.let { previous ->
            if (previous in uploaded) return TicketResult.AlreadyUploaded
            return TicketResult.Issued(ticketFor(previous))
        }
        val max = if (kind == EvidenceKind.PHOTO) rules.maxPhotos else rules.maxVideos
        if (max == 0) return TicketResult.Refused(EvidenceProblem.NOT_ALLOWED)
        if (existing.count { it.second == kind } >= max) return TicketResult.Refused(EvidenceProblem.LIMIT_REACHED)
        val id = UUID.randomUUID().toString()
        existing += id to kind
        byKey[idempotencyKey] = id
        if (existing.size == 3) failures[id] = 2
        return TicketResult.Issued(ticketFor(id))
    }

    override suspend fun confirm(accessToken: String, claimId: String, evidenceId: String, correlationId: String): ConfirmResult {
        delay(latencyMs)
        val pendingFailures = failures[evidenceId] ?: 0
        if (pendingFailures > 0) {
            failures[evidenceId] = pendingFailures - 1
            return ConfirmResult.NetworkError
        }
        uploaded += evidenceId
        return ConfirmResult.Confirmed
    }

    private fun ticketFor(id: String) =
        UploadTicket(id, "https://simulado.local/evidencias", mapOf("key" to id), Instant.now(clock).plusSeconds(600), 50L * 1024 * 1024)

    /** Sin almacenamiento real: devuelve una imagen pública de ejemplo para la evidencia ya enviada. */
    override suspend fun download(accessToken: String, claimId: String, evidenceId: String, correlationId: String): DownloadResult {
        delay(latencyMs)
        if (evidenceId !in uploaded) return DownloadResult.NotAvailable
        val kind = issued[claimId]?.firstOrNull { it.first == evidenceId }?.second ?: EvidenceKind.PHOTO
        return DownloadResult.Ready(SAMPLE_URL, kind.mime, Instant.now(clock).plusSeconds(300))
    }

    override suspend fun saveLocation(accessToken: String, claimId: String, fix: LocationFix, correlationId: String): LocationSaveResult {
        delay(latencyMs)
        locations[claimId] = fix
        return LocationSaveResult.Saved
    }

    private companion object {
        const val SAMPLE_URL = "https://example.com/evidencia-simulada.jpg"
    }
}
