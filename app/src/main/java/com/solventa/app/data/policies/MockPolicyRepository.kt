package com.solventa.app.data.policies

import java.time.Instant

/** Billetera simulada para demos sin backend (`solventa.useMockBackend=true`). */
class MockPolicyRepository(
    private val now: () -> Instant = Instant::now,
) : PolicyRepository {

    override suspend fun fetchWallet(accessToken: String, correlationId: String): WalletFetchResult {
        val today = now()
        return WalletFetchResult.Loaded(
            listOf(
                Policy(
                    id = "8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10",
                    number = "SLV-2026-08341",
                    status = "EMITIDA",
                    branch = "VIDA",
                    productCode = "SEGURO_VIDA_HIPOTECARIO",
                    productName = "Seguro de vida hipotecario",
                    validFrom = today.minusSeconds(DAY * 120),
                    validTo = today.plusSeconds(DAY * 245),
                    issuedAt = today.minusSeconds(DAY * 120),
                    totalPremium = "120000.00",
                    coverages = listOf(
                        Coverage("c1", "Muerte por cualquier causa", "200000000.00", "0.00"),
                        Coverage("c2", "Incapacidad total y permanente", "100000000.00", "0.00"),
                    ),
                ),
                Policy(
                    id = "1b7e4c2a-9d3f-4a68-8c15-5e2f0a9b7d44",
                    number = "SLV-2026-09120",
                    status = "EMITIDA",
                    branch = "SALUD",
                    productCode = "SEGURO_VIAJE",
                    productName = "Seguro de viaje",
                    validFrom = today.minusSeconds(DAY * 30),
                    validTo = today.plusSeconds(DAY * 335),
                    issuedAt = today.minusSeconds(DAY * 30),
                    totalPremium = "85000.00",
                    coverages = listOf(Coverage("c3", "Gasto médico", "30000000.00", "150000.00")),
                ),
            ),
        )
    }

    override suspend fun certificate(accessToken: String, policyId: String, correlationId: String): CertificateResult =
        CertificateResult.Loaded(DEMO_PDF)

    private companion object {
        const val DAY = 86_400L

        /** PDF mínimo válido de una página en blanco, para probar el almacenamiento cifrado y la apertura. */
        val DEMO_PDF: ByteArray = (
            "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 144]>>endobj\n" +
                "trailer<</Root 1 0 R>>\n%%EOF"
            ).toByteArray(Charsets.US_ASCII)
    }
}
