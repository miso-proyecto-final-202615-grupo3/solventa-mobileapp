# HU4.2.2 · Recibir notificaciones push sobre el siniestro — plan técnico (app)

Historia #85. **Contrato canónico, decisiones N1–N8, modelo y tareas del backend:** `solventa-backend/docs/planning/HU4.2.2-plan-tecnico.md`.
Este archivo describe solo la app. Rama `feat/HU4.2.2-notificaciones-siniestro` desde `dev`. Implementa y revisa Sergio.

## 1. Cómo cumple la app cada criterio

| CA | En la app |
|---|---|
| CA1 | `SolventaMessagingService` recibe el mensaje *data-only* y muestra la notificación del canal "Siniestros". |
| CA2 | Título: "Tu siniestro SIN-2026-… cambió de estado"; texto: "Ahora está: En evaluación" (estado traducido con las cadenas de HU4.2.1, `statusRes`). |
| CA3 | La app solo registra **su** token para **su** instalación vinculada; el backend decide los destinos. Al cerrar sesión borra el token (`DELETE`). Si llega un mensaje cuyo siniestro no es del usuario actual, el detalle responde `404` y se muestra "No encontramos este siniestro" (ya existe). |
| CA4 | Permiso `POST_NOTIFICATIONS` (Android 13+, `targetSdk 34`) pedido **en contexto** (§3). Si se niega: la app funciona igual; aviso en "Mis siniestros" con "Activar notificaciones" → ajustes de la app. Si el usuario desactiva el canal, igual. |
| CA5 | Tocar la notificación abre `claims/{siniestro_id}`; si la app está bloqueada, primero el desbloqueo/login y luego el detalle. |

## 2. Dependencias nuevas

- `com.google.gms.google-services` (plugin) y `com.google.firebase:firebase-bom` + `firebase-messaging` (sin Analytics).
- `app/google-services.json` del proyecto de Firebase (DEP-1). **No se sube con valores reales**: se agrega a `.gitignore`; el CI lo genera desde el secreto `GOOGLE_SERVICES_JSON` (base64). Para compilar sin Firebase, el plugin se aplica solo si existe el archivo y `FirebaseMessaging` se usa detrás de una interfaz (§4).

## 3. Flujo en la app

1. **Registrar el token** cuando hay sesión y el teléfono está vinculado (`session.cliente.dispositivoVinculado == true`): al iniciar sesión, al desbloquear con biometría y en `onNewToken`. Si falla por red → reintento con la política de 2/4/8 s en un `CoroutineScope` de aplicación; `404 DISPOSITIVO_NO_VINCULADO` → no reintentar.
2. **Pedir el permiso** (solo si `Build.VERSION.SDK_INT >= 33` y no está concedido):
   - después de ver "Reporte enviado" (HU4.1.1), con una tarjeta que explica para qué sirve antes del diálogo del sistema;
   - y desde el aviso de "Mis siniestros" si quedó negado.
   - Si el sistema ya no muestra el diálogo (`shouldShowRequestPermissionRationale == false` tras negar), el botón abre los ajustes de notificaciones de la app (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
3. **Recibir:** `onMessageReceived` valida `tipo == "SINIESTRO_ESTADO"` y que `siniestro_id`, `numero` y `estado` existan (si no, ignora). Arma la notificación con `NotificationCompat` (canal `siniestros`, importancia alta, `setAutoCancel(true)`, ícono `ic_check_circle` o uno nuevo monocromático), id = `siniestro_id.hashCode()` (un cambio nuevo del mismo siniestro reemplaza al anterior).
4. **Tocar:** `PendingIntent` a `MainActivity` con extra `claim_id` (`FLAG_IMMUTABLE`). `MainActivity` lo guarda en un `PendingDeepLink` (StateFlow en `AppContainer`) que `SolventaNavHost` consume cuando la app está desbloqueada → `navigate(Routes.claimDetail(id))`. También en `onNewIntent` (app abierta).
5. **Cerrar sesión:** `AppContainer.logout()` llama primero al `DELETE /dispositivos/push` (sin bloquear el cierre si falla) y luego limpia la sesión.

## 4. Clases (nombres sugeridos)

| Capa | Archivo | Responsabilidad |
|---|---|---|
| core | `core/push/PushTokenSource.kt` | Interfaz `suspend fun currentToken(): String?`; implementación `FirebasePushTokenSource` (`FirebaseMessaging.getInstance().token.await()`), y `NoPushTokenSource` cuando no hay Firebase. |
| core | `core/push/NotificationPermission.kt` | Consultar y pedir `POST_NOTIFICATIONS` (reusar `PermissionRequester` de HU4.1.2); `areNotificationsEnabled()`. |
| core | `core/push/ClaimNotifications.kt` | Canal `siniestros` (crear en `SolventaApplication.onCreate`), construir y mostrar la notificación desde los datos. |
| core | `core/push/SolventaMessagingService.kt` | `FirebaseMessagingService`: `onNewToken` → `PushRegistrar.register(token)`; `onMessageReceived` → `ClaimNotifications`. Declarado en el `AndroidManifest` (`exported=false`, intent-filter `com.google.firebase.MESSAGING_EVENT`). |
| data | `data/push/PushRepository.kt` (+ `RemotePushRepository`, `MockPushRepository`) | `PUT`/`DELETE /api/v1/bff-mobile/dispositivos/push` → `PushResult` (`Registered`, `DeviceNotLinked`, `SessionExpired`, `NetworkError`, `ServiceUnavailable`). |
| data | `data/push/PushRegistrar.kt` | Orquesta: token + sesión + `installationId` (`InstallationIdProvider` de HU3.1.1) → repositorio, con reintentos; evita registrar dos veces el mismo token en la misma sesión. |
| navigation | `navigation/PendingDeepLink.kt` | Guarda el `claim_id` pendiente hasta que haya sesión. |
| feature | `feature/claims/list/MyClaimsScreen.kt` | Aviso "Las notificaciones están desactivadas" + "Activar notificaciones" (CA4). |
| feature | `feature/claims/report/ClaimReportedScreen.kt` | Tarjeta que explica y pide el permiso tras reportar (solo la primera vez). |

Cadenas nuevas en `values/strings.xml` y `values-en/strings.xml` (sin ellas el lint falla con `MissingTranslation`).

## 5. Pruebas

| Tipo | Archivo | Casos |
|---|---|---|
| JUnit | `data/push/RemotePushRepositoryTest.kt` (MockWebServer + muestras de `contracts/notificaciones`) | `PUT` con `installation_id`, token y `Authorization`; `204`; `404` → `DeviceNotLinked`; `401`; red; `DELETE`. |
| JUnit | `data/push/PushRegistrarTest.kt` | sin sesión o sin vincular no registra; registra al iniciar sesión y con `onNewToken`; no repite el mismo token; reintenta en red; no reintenta `DeviceNotLinked`; `logout` borra. |
| JUnit | `core/push/ClaimNotificationsTest.kt` (Robolectric **no** está en el proyecto: probar solo el armado de textos y la validación de datos con funciones puras) | datos incompletos o de otro `tipo` se ignoran; texto con número y estado traducido. |
| JUnit | `navigation/PendingDeepLinkTest.kt` | se guarda, se consume una sola vez, espera a que haya sesión. |
| Compose | `MyClaimsScreenTest` (ampliar) | aviso de notificaciones desactivadas visible y botón. |
| Manual | Teléfono real | permiso concedido y negado; notificación con la app cerrada, en segundo plano y abierta; tocarla abre el detalle; cerrar sesión y verificar que ya no llegan. |

## 6. Reglas de implementación (además de las del plan del backend §9)

- No commitear ni hacer push hasta que Sergio lo indique; mapa de commits en `docs/planning/HU4.2.2-mapa-commits.md`.
- No subir `google-services.json` real ni ninguna clave. `network_security_config.xml` de debug: **no** agregar IP de la red local.
- Compilar y probar con `./gradlew testDebugUnitTest lintDebug --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx1536m" -Pkotlin.compiler.execution.strategy=in-process` (la máquina tiene 8 GB). Lint debe quedar en **0 errores**.
- No cambiar el comportamiento de HU4.1.x ni HU4.2.1; sus pruebas deben seguir en verde.
- Kotlin y nombres de la app en inglés (como el resto de la app); textos visibles en español con traducción al inglés.
