# HU4.2.3 · Mapa de commits (app)

Rama `feat/HU4.2.3-siniestro-parametrico` (desde `dev`). Sin commits hasta que Sergio lo indique.
`app/src/debug/res/xml/network_security_config.xml` tiene una IP local de pruebas: **no se commitea**.

### T-M01 — `feat(HU4.2.3): canal e indemnización del siniestro en la app`
- `app/src/main/java/com/solventa/app/data/claims/ClaimsModels.kt`, `RemoteClaimsRepository.kt`
- `app/src/test/java/com/solventa/app/data/claims/ParametricClaimContractTest.kt`
- `app/src/test/resources/contracts/parametricos/`, `app/src/test/resources/contracts/{siniestros,evidencias}/` (`canal` en las muestras)

### T-M02 — `feat(HU4.2.3): siniestro automático en Mis siniestros, detalle y notificación`
- `app/src/main/java/com/solventa/app/feature/claims/list/ClaimUi.kt`, `MyClaimsScreen.kt`, `MyClaimsViewModel.kt` (recarga al desbloquear con huella)
- `app/src/test/java/com/solventa/app/feature/claims/list/MyClaimsViewModelTest.kt`
- `app/src/main/java/com/solventa/app/feature/claims/detail/ClaimDetailScreen.kt`
- `app/src/main/java/com/solventa/app/core/push/ClaimNotifications.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/solventa/app/feature/claims/list/MoneyFormatTest.kt`, `app/src/test/java/com/solventa/app/core/push/ParametricMessageTest.kt`
- `app/src/androidTest/java/com/solventa/app/feature/claims/{detail/ClaimDetailScreenTest.kt,list/MyClaimsScreenTest.kt}`

### T-M03 — `docs(HU4.2.3): plan técnico y mapa de commits del siniestro paramétrico`
- `docs/planning/HU4.2.3-plan-tecnico.md`, `docs/planning/HU4.2.3-mapa-commits.md`

## Verificación (21/11/2026)
258 pruebas unitarias en verde, lint sin errores, pruebas de interfaz compiladas. Falta verlo en el teléfono.
