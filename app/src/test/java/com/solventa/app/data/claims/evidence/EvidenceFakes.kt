package com.solventa.app.data.claims.evidence

import com.solventa.app.core.location.LocationFix
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime

/** Repositorio de evidencias programable para las pruebas de la cola y del ViewModel. */
class FakeEvidenceRepository : EvidenceRepository {
    val tickets = ArrayDeque<TicketResult>()
    val confirms = ArrayDeque<ConfirmResult>()
    var locationResult: LocationSaveResult = LocationSaveResult.Saved
    val requested = mutableListOf<Pair<String, EvidenceKind>>()
    val keys = mutableListOf<String>()
    val confirmed = mutableListOf<String>()
    val savedLocations = mutableListOf<LocationFix>()
    private var counter = 0

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
        requested += claimId to kind
        keys += idempotencyKey
        return tickets.removeFirstOrNull() ?: TicketResult.Issued(ticket("ev-${++counter}"))
    }

    override suspend fun confirm(accessToken: String, claimId: String, evidenceId: String, correlationId: String): ConfirmResult {
        confirmed += evidenceId
        return confirms.removeFirstOrNull() ?: ConfirmResult.Confirmed
    }

    override suspend fun saveLocation(accessToken: String, claimId: String, fix: LocationFix, correlationId: String): LocationSaveResult {
        savedLocations += fix
        return locationResult
    }

    /** HU4.2.1: siguiente respuesta de la descarga (por defecto, una URL lista). */
    val downloads = ArrayDeque<DownloadResult>()
    val downloaded = mutableListOf<String>()

    override suspend fun download(accessToken: String, claimId: String, evidenceId: String, correlationId: String): DownloadResult {
        downloaded += evidenceId
        return downloads.removeFirstOrNull()
            ?: DownloadResult.Ready("https://s3.test/$evidenceId?firma", "image/jpeg", Instant.parse("2030-01-01T00:05:00Z"))
    }

    companion object {
        fun ticket(id: String) = UploadTicket(id, "https://s3.test/bucket", mapOf("key" to id), Instant.parse("2030-01-01T00:00:00Z"), 5L * 1024 * 1024)
    }
}

class FakeUploader : StorageUploader {
    val results = ArrayDeque<StorageUploadResult>()
    val uploaded = mutableListOf<String>()

    override suspend fun upload(ticket: UploadTicket, file: File, mime: String, onProgress: (Float) -> Unit): StorageUploadResult {
        onProgress(1f)
        uploaded += ticket.evidenceId
        return results.removeFirstOrNull() ?: StorageUploadResult.Uploaded
    }
}
