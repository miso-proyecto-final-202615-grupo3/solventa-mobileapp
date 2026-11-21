# HU4.2.2 · Mapa de commits (app)

Rama `feat/HU4.2.2-notificaciones-siniestro` (sale de `dev` con HU4.2.1). Sin commits hasta que Sergio lo indique.
`app/src/debug/res/xml/network_security_config.xml` tiene una IP local de pruebas: **no se commitea**.

### #378 · T-M01 — `feat(HU4.2.2): token de Firebase y registro del dispositivo`
- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` (Firebase solo si existe `app/google-services.json`)
- `.gitignore` (`/app/google-services.json`)
- `app/src/main/AndroidManifest.xml` (servicio de mensajería y `POST_NOTIFICATIONS`)
- `app/src/main/java/com/solventa/app/core/push/PushTokenSource.kt`
- `app/src/main/java/com/solventa/app/core/push/SolventaMessagingService.kt`
- `app/src/main/java/com/solventa/app/data/push/` (`PushRepository.kt`, `RemotePushRepository.kt`, `PushRegistrar.kt`)
- `app/src/main/java/com/solventa/app/di/AppContainer.kt`, `app/src/main/java/com/solventa/app/SolventaApplication.kt`
- `app/src/test/java/com/solventa/app/data/push/` (`RemotePushRepositoryTest.kt`, `PushRegistrarTest.kt`)
- `app/src/test/resources/contracts/notificaciones/`

### #379 · T-M02 — `feat(HU4.2.2): notificación del cambio de estado del siniestro`
- `app/src/main/java/com/solventa/app/core/push/ClaimNotifications.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/solventa/app/core/push/ClaimStatusMessageTest.kt`

### #380 · T-M03 — `feat(HU4.2.2): permiso de notificaciones y aviso cuando están apagadas`
- `app/src/main/java/com/solventa/app/feature/claims/report/ClaimReportedScreen.kt`
- `app/src/main/java/com/solventa/app/feature/claims/list/MyClaimsScreen.kt`, `MyClaimsViewModel.kt` (recarga al volver a la pantalla)
- `app/src/androidTest/java/com/solventa/app/feature/claims/list/MyClaimsScreenTest.kt`
- `app/src/test/java/com/solventa/app/feature/claims/list/MyClaimsViewModelTest.kt`

### #381 · T-M04 — `feat(HU4.2.2): abrir el detalle al tocar la notificación`
- `app/src/main/java/com/solventa/app/MainActivity.kt`
- `app/src/main/java/com/solventa/app/navigation/PendingDeepLink.kt`, `app/src/main/java/com/solventa/app/navigation/SolventaNavHost.kt`
- `app/src/test/java/com/solventa/app/navigation/PendingDeepLinkTest.kt`

### #382 · T-M05 — pruebas y documentación
- `docs/planning/HU4.2.2-plan-tecnico.md`, `docs/planning/HU4.2.2-mapa-commits.md`
- Las pruebas van con cada tarea. Prueba en el teléfono con Firebase y SNS reales hecha el 21/11/2026 (app abierta, en segundo plano, cerrada y tras cerrar sesión); evidencias locales en `docs/evidencias/HU4.2.2/` (no se suben).
