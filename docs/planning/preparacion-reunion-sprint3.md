# Preparación reunión con el tutor · Sprint 3

- Fecha de corte: 2026-11-07. Sprint 3: 02/11/2026 – 22/11/2026 (3 semanas, milestone 3).
- Responsable de la app móvil y de F3.1 / F4.x: Sergio Gelvez.
- Fuentes: backlog (issues de `dzamoranouniandes/misw4501-grupo3-solventa-backend`), `main` de `solventa-mobileapp` y `origin/dev` de `solventa-backend` (último commit 06/11, Danny).

---

## 1. Lo que preguntó el tutor: HU que no están en el Sprint 3

| HU | Épica / canal | Estado en el backlog | Comentario para la reunión |
|---|---|---|---|
| HU1.3.5 Firmar digitalmente la póliza (#65) | E1 Web | Abierta, sin sprint ni responsable | Va entre el pago (HU1.3.4) y la emisión (HU1.3.6), ambas en Sprint 3. Sin ella el flujo de adquisición queda con un hueco. |
| HU1.3.7 Registrar trazabilidad de la emisión (#67) | E1 Web | Abierta, sin sprint ni responsable | Mismo flujo de emisión; es la trazabilidad regulatoria. |
| HU4.2.1 Consultar estado de un siniestro (#84) | E4 Mobile | Abierta, sin sprint | Continuación natural de HU4.1.1 (mismo MS Siniestros y misma pantalla de lista/detalle). Candidata a entrar en Sprint 3 con Sergio. |
| HU4.2.2 Notificaciones push del siniestro (#85) | E4 Mobile | Abierta, sin sprint | Depende del servicio de notificaciones (tarea #260 Email / SNS Push, de HU1.3.6) y de FCM en la app. |
| HU4.2.3 Siniestro paramétrico automático (#86) | E4 Mobile | Abierta, sin sprint | Es la HU del ASR de escalabilidad HA8 (1.000.000 de eventos en 10 min, `mock_opendata`). El modelo `EVENTO_EXTERNO` / `ACTIVACION_PARAMETRICA` ya está en `insuranceclaims_service`. |
| Épica E2 (F2.1 socios: HU2.1.1–2.1.7; F2.2 venta asistida: HU2.2.1–2.2.12) | E2 Web | Todas abiertas, sin sprint | Ninguna HU de E2 está planeada. Hay que decidir en equipo cuáles son las "sensibles" (autenticación del asesor HU2.2.1, credenciales del socio HU2.1.3/2.1.5/2.1.6 y KYC asistido HU2.2.7 parecen las de más riesgo). |

**HU del Sprint 3 por persona** (de los issues):

| Responsable | HU |
|---|---|
| dzamoranouniandes | HU1.3.1, HU1.3.2, HU1.3.3 (KYC/AML) |
| JuanTapia911 | HU1.3.4 (pago), HU1.3.6 (emisión), HU1.3.8 (consultar póliza) |
| asr88 | HU3.2.1, HU3.2.2 (billetera offline) |
| seangego | HU4.1.1 (registrar siniestro), HU4.1.2 (evidencia y geolocalización) |

Las tareas #227–#273 (backend, Angular, pytest, Jest, Cypress, Terraform) son de las HU web. **HU4.1.1 y HU4.1.2 todavía no tienen tareas.**

---

## 2. Estructura de carpetas

### 2.1 App móvil (`solventa-mobileapp`)

```
solventa-mobileapp/
├── app/
│   ├── build.gradle.kts            # BuildConfig por propiedades: useMockBackend, bffMobileUrl, diditSdkEnabled…
│   └── src/
│       ├── main/java/com/solventa/app/
│       │   ├── MainActivity.kt, SolventaApplication.kt
│       │   ├── core/                # Piezas técnicas reutilizables, sin pantallas de negocio
│       │   │   ├── biometric/       # HU3.1.2: Keystore, BiometricPrompt, bloqueo, AppLockManager
│       │   │   ├── camera/          # HU3.1.1: CameraX, frames en memoria, FLAG_SECURE
│       │   │   ├── liveness/        # HU3.1.1: guía con ML Kit (máquina de estados) y lanzadores de captura
│       │   │   ├── device/          # installation_id y datos del teléfono (sin IDs de hardware)
│       │   │   └── designsystem/    # Tema de marca y botones
│       │   ├── data/                # Repositorios (interfaz + Mock + Remote) y DTO
│       │   │   ├── auth/  activation/  consent/  session/
│       │   ├── di/AppContainer.kt   # DI manual: elige mock o remoto
│       │   ├── feature/             # Pantalla Compose + ViewModel por funcionalidad
│       │   │   ├── login/  account/  consent/  activation/  biometric/  home/
│       │   └── navigation/SolventaNavHost.kt
│       ├── main/res/values, values-en   # i18n español / inglés
│       ├── debug/                   # network_security_config (HTTP solo a 10.0.2.2/localhost)
│       ├── test/                    # JUnit + coroutines-test + MockWebServer; resources/contracts/*.json
│       └── androidTest/             # Compose Testing por pantalla
└── docs/
    ├── contratos-pendientes.md      # Estado de cada contrato con el BFF Móvil
    ├── planning/                    # Planes técnicos, diagramas y revisiones por HU
    ├── diagramas/  evidencias/
```

### 2.2 Backend (`solventa-backend`, rama `dev`)

```
solventa-backend/
├── bff_mobile/                 # BFF Móvil (FastAPI). Prefijo /api/v1/bff-mobile
├── bff_web/                    # BFF Web
├── identity_service/           # MS Identidad: clientes, Cognito, consentimientos, activación móvil, KYC, gRPC perfil
├── liveness_adapter/           # Adaptador de prueba de vida (experimento HA12): Strategy + Factory
├── quotingengine_service/      # Cotización (HU1.2.x)
├── insurancepolicies_service/  # MS Pólizas (esqueleto, Sprint 3)
├── insuranceclaims_service/    # MS Siniestros (esqueleto, Sprint 3) ← HU4.1.1 / HU4.1.2
├── payments_service/           # MS Pagos (esqueleto, Sprint 3)
├── mock_kyc/ mock_opendata/ mock_openfinance/   # Simuladores de terceros
├── deployment/helm/ + kuztomize/   # Chart común backend-service, values por servicio y ambiente (dev/qa/prod)
├── .github/workflows/          # CI, publicar en ECR, desplegar en EKS
├── docs/contracts/             # Muestras JSON compartidas con la app (pruebas de contrato)
└── docker-compose.yml          # Entorno local (Cognito real con credenciales propias en .env)
```

Cada microservicio sigue la misma arquitectura hexagonal: `src/domain` (entidades y puertos de repositorio), `src/application` (casos de uso y puertos), `src/infrastructure` (persistencia SQLAlchemy, gateways HTTP, caché), `src/presentation` (routers FastAPI, schemas, dependencias) y `src/core` (config, correlación, telemetría).

---

## 3. Componentes de la app móvil

### 3.1 Base, navegación y DI

| Componente | Ruta (`app/src/main/java/com/solventa/app/`) | Función |
|---|---|---|
| `MainActivity` | `MainActivity.kt` | `FragmentActivity` (la exige `BiometricPrompt`); monta el tema y la navegación. |
| `SolventaApplication` | `SolventaApplication.kt` | Crea el `AppContainer` y el `AppLockManager`. |
| `AppContainer` | `di/AppContainer.kt` | DI manual. Elige mock o BFF real según `solventa.useMockBackend`, `useMockConsent` y `diditSdkEnabled`. |
| `SolventaNavHost`, `Routes` | `navigation/SolventaNavHost.kt` | Grafo: login → consentimiento → activación → oferta biométrica → inicio; bloqueo superpuesto. |
| `SolventaTheme`, `Color`, `Type` | `core/designsystem/theme/` | Tema Material 3 con el manual de marca. |
| `SolventaPrimaryButton` / `SecondaryButton` | `core/designsystem/component/SolventaButtons.kt` | Botones de marca; el texto describe la acción. |

### 3.2 Login y sesión (base de HU3.1.2)

| Componente | Ruta | Función |
|---|---|---|
| `AuthRepository` | `data/auth/AuthRepository.kt` | Interfaz del login (`POST /api/v1/bff-mobile/auth/login`). |
| `MockAuthRepository` | `data/auth/MockAuthRepository.kt` | Backend simulado con reglas de demo (correos que simulan red caída o 503). |
| `RemoteAuthRepository` | `data/auth/RemoteAuthRepository.kt` | Cliente OkHttp del BFF; envía `installation_id` y lee el bloque `cliente`. |
| `AuthModels` (`Session`, `ClienteResumen`, `EstadoKyc`, `LoginResult`) | `data/auth/AuthModels.kt` | Modelos de dominio y resultado sellado. |
| `SessionStore` / `InMemorySessionStore` | `data/session/SessionStore.kt` | Sesión solo en memoria. |
| `SessionCodec` | `data/session/SessionCodec.kt` | Serializa la sesión para cifrarla con la llave biométrica. |
| `LoginScreen` / `LoginViewModel` | `feature/login/` | Formulario, validaciones, destino según vinculación y consentimiento. |
| `AccountNotFoundScreen` | `feature/account/` | La app no crea cuentas: enlace al registro web. |
| `HomeScreen` | `feature/home/` | Inicio provisional hasta la billetera (HU3.2.x). |

### 3.3 HU3.1.2 · Autenticación biométrica (Sprint 1)

| Componente | Ruta | Función |
|---|---|---|
| `BiometricKeyManager` | `core/biometric/BiometricKeyManager.kt` | Llave AES en Android Keystore que exige biometría fuerte; se invalida si cambian las huellas/rostros. |
| `BiometricVault` | `core/biometric/BiometricVault.kt` | Guarda la sesión cifrada; desbloquear = descifrar con el cipher autenticado. |
| `BiometricSettings` | `core/biometric/BiometricSettings.kt` | Estado local (activado, "ahora no", sesión sellada). Sin datos biométricos. |
| `BiometricPromptLauncher` | `core/biometric/BiometricPromptLauncher.kt` | Envuelve `BiometricPrompt` y emite eventos sellados (éxito, fallo, cancelado, bloqueo del sistema). |
| `BiometricAvailabilityChecker` | `core/biometric/BiometricAvailability.kt` | Hay sensor / no hay / no está configurado. |
| `BiometricLockoutPolicy` | `core/biometric/BiometricLockoutPolicy.kt` | 3 fallos seguidos → bloqueo 5 min, persistido. |
| `AppLockManager` | `core/biometric/AppLockManager.kt` | Pide biometría al abrir y al volver de segundo plano tras más de 60 s. |
| `BiometricOfferScreen` / `ViewModel` | `feature/biometric/` | Oferta de activar huella/rostro tras vincular el teléfono. |
| `UnlockScreen` / `ViewModel` | `feature/biometric/` | Pantalla de bloqueo superpuesta; reintentos, bloqueo, biometría cambiada, "usar contraseña". |
| `SecuritySettingsScreen` / `ViewModel` | `feature/biometric/` | Perfil > Seguridad: activar o desactivar. |

### 3.4 HU3.1.1 · Activación con selfie y prueba de vida (Sprint 2)

| Componente | Ruta | Función |
|---|---|---|
| `InstallationIdProvider` | `core/device/InstallationIdProvider.kt` | UUID aleatorio por instalación (no IMEI ni Android ID). |
| `DeviceInfoProvider` | `core/device/DeviceInfoProvider.kt` | Marca, modelo y versión para la trazabilidad de la vinculación. |
| `FrontCameraPreview`, `SecureWindowEffect` | `core/camera/FrontCameraPreview.kt` | Cámara frontal CameraX + `FLAG_SECURE` mientras se ve el rostro. |
| `FrameEncoder` | `core/camera/FrameEncoder.kt` | Fotograma → JPEG en memoria, rotado, ≤ 1280 px y ≤ 512 KB. |
| `CapturedFrames` | `core/camera/CapturedFrames.kt` | Las 3 capturas (inicio, gesto, fin); `wipe()` las sobrescribe con ceros. |
| `CaptureGuideEngine` | `core/liveness/CaptureGuideEngine.kt` | Máquina de estados pura: rostro centrado → gesto del servidor → capturas; timeout, rostro perdido, varios rostros. |
| `MlKitFaceAnalyzer` | `core/liveness/MlKitFaceAnalyzer.kt` | Adapter de ML Kit a `FaceSignals`; captura el frame cuando la guía lo pide. |
| `LivenessCaptureLauncher`, `OwnCaptureLauncher`, `CaptureLauncherFactory` | `core/liveness/launcher/CaptureLaunchers.kt` | Strategy + Factory por tipo de captura (`FRAMES` o `SDK`). |
| `SdkDiditCaptureLauncher` | `core/liveness/launcher/SdkDiditCaptureLauncher.kt` | SDK real de Didit, solo demo (`diditSdkEnabled=true`). |
| `ActivationRepository` (+ `CaptureType`, resultados) | `data/activation/ActivationRepository.kt` | Contrato: crear sesión, verificar. |
| `MockActivationRepository` | `data/activation/MockActivationRepository.kt` | Backend simulado con las mismas formas del contrato. |
| `RemoteActivationRepository` | `data/activation/RemoteActivationRepository.kt` | Cliente del BFF: JSON para la sesión, multipart para los 3 frames, `X-Correlation-Id`, mapeo de errores. |
| `ActivationScreen` (intro, captura, resultado) | `feature/activation/ActivationScreen.kt` | Pantallas del flujo. |
| `ActivationViewModel` | `feature/activation/ActivationViewModel.kt` | Orquesta sesión → captura → verificación → resultado; borra los frames siempre. |

### 3.5 HU3.1.3 · Consentimiento biométrico (parte móvil, HU de asr88)

| Componente | Ruta | Función |
|---|---|---|
| `BiometricConsentRepository` (Mock + Remote) | `data/consent/BiometricConsentRepository.kt` | `GET` / `POST /consentimientos/biometrico`; versión del texto legal. |
| `BiometricConsentScreen`, `ConsentDeclinedScreen` | `feature/consent/` | Texto legal, casilla obligatoria, aceptar o rechazar. |
| `BiometricConsentViewModel` | `feature/consent/` | Salta si ya aceptó la versión vigente; el rechazo también se registra. |

### 3.6 Pruebas

| Tipo | Dónde | Qué cubre |
|---|---|---|
| Unitarias (17 clases) | `app/src/test/` | ViewModels, repositorios mock y remotos (MockWebServer), `CaptureGuideEngine`, política de bloqueo, `wipe()`, Factory de lanzadores. |
| Contrato | `app/src/test/resources/contracts/` | Copia exacta de `solventa-backend/docs/contracts/` (activación y consentimiento). |
| UI Compose (4 clases) | `app/src/androidTest/` | Login, consentimiento, activación, bloqueo. |

---

## 4. Componentes del backend en los que trabajó Sergio

### 4.1 BFF Móvil (`bff_mobile/src`)

| Componente | Ruta | Función |
|---|---|---|
| Router de autenticación | `presentation/routers/auth.py` | `POST /auth/login`: delega en Identidad y agrega el bloque `cliente` (resumen móvil). |
| Router de activación | `presentation/routers/activacion.py` | `POST /activacion/sesiones` y `POST /activacion/sesiones/{id}/verificacion` (multipart). |
| Router de consentimientos | `presentation/routers/consentimientos.py` | `GET` / `POST /consentimientos/biometrico`. |
| Router de cotización | `presentation/routers/quotingengine.py` | Productos y cotizaciones (equipo). |
| `jwt_validator`, `id_token` | `presentation/` | Valida el access token de Cognito (firma, `iss`, `token_use`, `client_id`) y obtiene el correo. |
| `errors` | `presentation/errors.py` | Formato común `{codigo, mensaje, correlation_id}`. |
| `IdentityServiceGateway` | `infrastructure/gateways/identity_service_gateway.py` | Cliente HTTP hacia el MS Identidad. |
| `correlation`, `telemetry` | `core/` | `X-Correlation-Id` de punta a punta y OpenTelemetry. |

### 4.2 MS Identidad (`identity_service/src`), partes de F3.1

| Componente | Ruta | Función |
|---|---|---|
| `IniciarActivacionMovilUseCase` | `application/use_cases/iniciar_activacion_movil_use_case.py` | Crea la sesión de activación, aplica intentos y consentimiento, pide reto al adaptador. |
| `VerificarActivacionMovilUseCase` | `application/use_cases/verificar_activacion_movil_use_case.py` | Envía frames al adaptador, registra el intento y vincula el dispositivo si aprueba. |
| `ConsultarResumenMovilUseCase` | `application/use_cases/consultar_resumen_movil_use_case.py` | `GET /clientes/resumen-movil`: id, nombre, `estado_kyc`, `dispositivo_vinculado`. |
| `ConsentimientoBiometricoUseCase` | `application/use_cases/consentimiento_biometrico_use_case.py` | Estado y registro del consentimiento biométrico (HU3.1.3). |
| `politica_intentos` | `domain/activacion_movil/politica_intentos.py` | 3 intentos en 24 h; `ANULADO` no cuenta. |
| Entidades de activación | `domain/activacion_movil/entities.py` | Sesión de activación, intento de prueba de vida, dispositivo vinculado. |
| `LivenessVerificationPort` | `application/ports/liveness.py` | Puerto hacia la prueba de vida; Identidad no conoce al proveedor. |
| `HttpLivenessAdapterClient` | `infrastructure/gateways/http_liveness_adapter_client.py` | Implementación HTTP del puerto hacia `liveness_adapter`. |
| Repositorios de activación y consentimiento | `infrastructure/persistence/*_repository_impl.py` | PostgreSQL (SQLAlchemy); tablas `sesiones_activacion`, `intentos_prueba_vida`, `dispositivos_vinculados`. |
| Router de clientes | `presentation/routers/clientes.py` | Endpoints internos de activación, consentimiento biométrico y resumen móvil (además de registro, autenticar, consentimiento de datos, perfil y KYC del equipo). |

### 4.3 Adaptador de prueba de vida (`liveness_adapter/src`, experimento HA12)

| Componente | Ruta | Función |
|---|---|---|
| `LivenessProvider` (Protocol) | `application/ports.py` | Strategy: `start_session` y `verify`. |
| `LivenessProviderFactory` | `infrastructure/provider_factory.py` | Factory: devuelve el proveedor registrado por nombre. |
| `PropioProvider` | `infrastructure/providers/propio.py` | Proveedor propio (por defecto): reto + nonce, verifica 3 frames. |
| `face_analyzer`, `gesture_rules` | `infrastructure/providers/` | Análisis de rostro con MediaPipe y reglas de parpadeo/giro. |
| `DiditProvider` | `infrastructure/providers/didit.py` | Didit, solo demo y desactivado por defecto. |
| `ProviderBudget` | `infrastructure/budget.py` | Topes de Didit en Redis (por día y por mes) → vuelve a `propio`. |
| `configuration`, `session_store` | `infrastructure/` | Proveedor activo (Redis → variable de entorno) y sesiones del adaptador. |
| API | `main.py` | `POST /liveness/sessions`, `POST /liveness/verifications`, `GET`/`PUT /admin/liveness-provider`, `/health`, `/ready`. |

---

## 5. Decisiones tomadas en las HU anteriores

### 5.1 Transversales (app)

| Decisión | Por qué |
|---|---|
| Android nativo, Kotlin + Jetpack Compose, `targetSdk` 34, i18n nativa (es/en) | Stack definido por el equipo (DT3.12). |
| MVVM + UDF (`StateFlow<UiState>`, destino de navegación consumido por la UI) | Estado predecible y ViewModels testeables con JUnit. |
| Repositorio detrás de interfaz con versión **Mock** y **Remote**, elegida por propiedad de Gradle | Avanzar sin depender del backend; cambiar a real sin tocar código. |
| DI manual (`AppContainer`), OkHttp sin Retrofit, kotlinx.serialization | App pequeña; menos dependencias y menos RAM en la máquina de build. |
| Resultados sellados (`LoginResult`, `VerificationResult`, `ConsentResult`) | `when` exhaustivo: ningún error queda sin pantalla. |
| La URL del BFF nunca va en el repo (`~/.gradle/gradle.properties`); HTTP solo en debug hacia el emulador | Seguridad; release exige HTTPS. |
| Pruebas de contrato con JSON compartidos con el backend | El contrato canónico vive en el backend; la app lo copia (D10). |
| La app no crea cuentas | El registro es web (F1.1); la app enlaza al registro. |

### 5.2 HU3.1.2 · Biometría

| Decisión | Por qué |
|---|---|
| La biometría la verifica el sistema operativo (`BiometricPrompt`, clase fuerte); la app no guarda datos biométricos | Criterio de la HU y regla de Android. |
| La sesión se cifra con una llave de Keystore que solo se libera tras la biometría | Desbloquear con huella = recuperar la sesión; nadie la lee sin autenticar. |
| La llave se invalida si cambian las huellas/rostros del teléfono → pedir contraseña | Evita que una huella agregada por un tercero dé acceso. |
| 3 fallos → bloqueo de 5 min, persistido | Criterio de la HU; sobrevive al cierre de la app. |
| Bloqueo al abrir y al volver tras 60 s en segundo plano; pantalla superpuesta | No se pierde el estado de la navegación. |
| Se ofrece solo tras vincular el teléfono (HU3.1.1); "Ahora no" no vuelve a ofrecerse, queda en Perfil > Seguridad | Flujo acordado F3.1. |
| Cerrar sesión conserva la biometría activada | Ajuste pedido en la revisión de la HU. |

### 5.3 HU3.1.1 · Activación con prueba de vida (D1–D12 del plan del backend)

| ID | Decisión |
|---|---|
| — | La prueba de vida activa el acceso móvil **una vez por instalación**; **no** es el KYC/AML web (HU1.3.3) y **no cambia `estado_kyc`** (D4). |
| — | Orden: login → consentimiento biométrico → selfie → teléfono vinculado → oferta de biometría → inicio. |
| D1 | Patrón del experimento HA12: microservicio `liveness_adapter` + Strategy + Factory; proveedor activo por `PUT /admin/liveness-provider` (Redis). La app no conoce al proveedor: solo recibe `captura.tipo` (`FRAMES` o `SDK`). |
| — | El reto (gesto + nonce) lo emite el servidor; ML Kit en la app solo guía y elige los momentos de captura; el veredicto es del backend (MediaPipe). |
| D2 | Un vínculo activo por cliente y por instalación. |
| D3 / D12 | Máximo 3 intentos en 24 h; cuentan `RECHAZADO` y `EN_CURSO`; `ANULADO` (falla técnica) no; los `EN_CURSO` vencidos pasan a `ANULADO` antes de contar. |
| D5 | Consentimiento biométrico obligatorio antes de la activación (ya activo: `REQUIRE_BIOMETRIC_CONSENT=true` tras HU3.1.3). |
| D6 | Error común `{codigo, mensaje, correlation_id}` y `X-Correlation-Id` de punta a punta. |
| D7 | Extensión del experimento con `/liveness/sessions`. |
| D8 | Riesgo aceptado: la validación propia no es PAD certificado (alcance académico). |
| D9 | Didit integrado solo para demo, desactivado por defecto, costo cero (sandbox / cuota gratuita, topes en Redis). |
| D10 | Contrato canónico en el plan del backend; la app tiene la copia y las pruebas de contrato. |
| D11 | Una sesión `FRAMES` no se reutiliza; el nonce se guarda solo como hash. |
| — | Privacidad (CA7): frames solo en memoria, `wipe()` garantizado, `FLAG_SECURE`, sin logs, `allowBackup=false`, sin identificadores de hardware. |

### 5.4 Pendientes conocidos (para no sorprenderse en la reunión)

- No hay refresh de token en Identidad ni en el BFF Móvil (`contratos-pendientes.md` 4b y 11).
- Falta un endpoint HTTPS para el build release (el ALB de dev responde por HTTP).
- Sin verificar todavía: prueba de concurrencia en PostgreSQL, MediaPipe con fotos reales, pruebas instrumentadas en dispositivo real, SDK de Didit en dispositivo, calibración del signo del giro (`HU3.1.1-revision-2026-10-31.md`).
- El formato de error común solo se aplica a los endpoints nuevos; falta el catálogo de códigos.

---

## 6. Punto de partida para el Sprint 3 (HU4.1.1 y HU4.1.2)

Lo que ya hay:
- `insuranceclaims_service` en `dev`: arquitectura hexagonal y modelo `SINIESTRO` (`poliza_id`, `tipo`, `estado`, `clave_idempotencia` única, `ocurrido_en`, `reportado_en`, `evidencia_referencia` JSON), más `INDEMNIZACION` y las tablas paramétricas. Solo expone `/health`.
- Wiki: el flujo es App → BFF Móvil → MS Siniestros (HTTPS); las fotos van a S3.
- App: el patrón Mock/Remote, el `installation_id`, CameraX y el manejo de permisos de cámara se pueden reutilizar.

Por definir con el equipo antes de crear las tareas:
1. Origen de la lista de pólizas del cliente: HU1.3.8 (Juan) o la billetera de HU3.2.1 (asr88). Mientras tanto, un mock detrás de una interfaz.
2. Catálogo de `tipo` de siniestro por ramo y los campos básicos del evento.
3. Evidencias: ¿una tabla `EVIDENCIA` con estado por archivo, o solo el JSON `evidencia_referencia`? El criterio "si una evidencia falla no se pierde el resto del reporte" sugiere la tabla. Subida directa a S3 con URL prefirmada (como la tarea #266 de pólizas).
4. Límites de foto y video (tamaño, duración, cantidad) y cifrado en S3 (KMS, como la tarea #242 de KYC).
5. ¿Entra HU4.2.1 (consultar estado) en este sprint, ya que reutiliza el mismo servicio?
