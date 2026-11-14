# HU4.1.1 · Registrar un siniestro · Plan técnico (app Android)

- Historia: [#81](https://github.com/dzamoranouniandes/misw4501-grupo3-solventa-backend/issues/81) · Sprint 3 (02/11/2026 – 22/11/2026) · Responsable: Sergio Gelvez
- Rama: `feat/HU4.1.1-registrar-siniestro` (sale de `dev`)
- Backend: `solventa-backend/docs/planning/HU4.1.1-plan-tecnico.md`. **Su sección 3 es el contrato canónico; la sección 4 de este plan es una copia de consumo.**
- Prototipo: [app móvil › Siniestro asistido › Registrar un siniestro](https://asr88.github.io/githubPagesProjectFinal/movil/) ("¿Qué pasó? · Cuéntanos brevemente · ¿Cuándo ocurrió? · Continuar con la evidencia").
- Siguiente historia: HU4.1.2 (evidencias y ubicación) parte de la pantalla de confirmación de esta.

> Insumo para el agente que implementa. Describe **qué** construir y **cómo** encaja con el código existente.

---

## 0. Alcance y decisiones de base

- El cliente reporta desde la app un siniestro de una póliza **suya y vigente**. El backend genera el identificador y el folio.
- **Dos pasos (S1):** esta historia crea el siniestro; las fotos, videos y ubicación se adjuntan después (HU4.1.2). El botón "Adjuntar evidencia" de la confirmación lleva a HU4.1.2; mientras HU4.1.2 no esté, lleva a Inicio.
- **La app nunca envía `cliente_id` ni el correo**: el BFF los toma del token.
- **Envío idempotente (S3):** la app genera una `Idempotency-Key` (UUID) por reporte y la reutiliza en cada reintento; así una falla de red nunca crea dos siniestros.
- Los tipos de siniestro vienen del backend por póliza (`tipos_siniestro`), leídos de la tabla `tipo_siniestro` de MS Siniestros, que es la **única fuente** (decisión S4, aprobada). La app **no** tiene una lista propia: muestra el `nombre` que llega, en el idioma que pide con `Accept-Language`.
- **Entrada:** botón "Reportar un siniestro" en `HomeScreen`. Cuando la billetera (HU3.2.x, asr88) agregue la barra inferior "Pólizas · Siniestros · Perfil" del prototipo, la pestaña Siniestros abre esta ruta.

**Fuera de alcance:** evidencias y ubicación (HU4.1.2), asistencia en sitio (HU4.1.3), lista y estado de siniestros (HU4.2.1), borrador guardado en disco.

| # | Criterio | En la app |
|---|---|---|
| CA1 | Seleccionar una póliza de su cuenta | Paso **Póliza**: lista de `GET /siniestros/polizas-elegibles`. Con una sola póliza se preselecciona. Sin pólizas: estado vacío con "Volver al inicio". |
| CA2 | Información básica del evento | Paso **Qué pasó**: tipo (chips), descripción (10–500, contador) y fecha y hora (selectores de Material 3 limitados a la vigencia y a hoy). |
| CA3 | Confirmar el envío | Paso **Revisar**: resumen y botón "Enviar reporte". Nada se envía antes. |
| CA4 | Identificador del siniestro | Se muestra el folio (`numero`) devuelto por el backend. |
| CA5 | Confirmación de registro | Pantalla **Reporte enviado** con folio, póliza, fecha y hora del reporte. |

---

## 1. Arquitectura y patrones

Se mantiene el estilo actual: **MVVM + UDF** (`StateFlow<UiState>`, eventos como funciones, `destination` que la UI consume y limpia), **repositorio detrás de interfaz** con versión simulada y remota, **resultados sellados** y **DI manual** en `AppContainer`.

| Patrón / táctica | Dónde | Por qué |
|---|---|---|
| Repository + mock | `ClaimsRepository`, `MockClaimsRepository`, `RemoteClaimsRepository` | Avanzar sin backend y cambiar a real sin tocar código (`solventa.useMockClaims`). |
| Sealed result | `PoliciesResult`, `ReportResult`, `ClaimResult` | `when` exhaustivo: cada error tiene pantalla. |
| Validador puro | `ClaimFormValidator` | Reglas del formulario probadas con JUnit, sin Android. |
| Operación idempotente + reintento | `IdempotencyKeyHolder` en el ViewModel | Reintentar es seguro (disponibilidad). |
| Estado que sobrevive al proceso | `SavedStateHandle` | Si Android mata la app en el selector de fecha, el formulario y la clave no se pierden. |
| Reloj inyectado | `Clock` en validador y ViewModel | Pruebas de "fecha futura" y "fuera de vigencia" deterministas. |

### 1.1 Flujo y navegación

```
Home ─ "Reportar un siniestro" ─► claims/report
   POLICY ──(continuar)──► DETAILS ──(continuar)──► REVIEW ──(Enviar reporte)──► SUBMITTING ──► DONE
     ▲  (atrás)              ▲ (atrás)                │                           │ error → REVIEW + mensaje
     └───────────────────────┴────────────────────────┘                           │ 401  → Login
DONE: "Adjuntar evidencia" ─► claims/{id}/evidence (HU4.1.2)      "Ir al inicio" ─► Home
```

### 1.2 Paquetes nuevos

```
app/src/main/java/com/solventa/app/
├── data/claims/
│   ├── ClaimsModels.kt
│   ├── ClaimsRepository.kt
│   ├── MockClaimsRepository.kt
│   └── RemoteClaimsRepository.kt
└── feature/claims/report/
    ├── ClaimFormValidator.kt
    ├── ClaimReportViewModel.kt
    ├── ClaimReportScreen.kt        (Route + pasos)
    └── ClaimReportedScreen.kt
```

---

## 2. Diseño detallado

### 2.1 `data/claims/ClaimsModels.kt`

```kotlin
data class EvidenceRules(val maxPhotos: Int, val maxVideos: Int) { val allowsVideo get() = maxVideos > 0 }
data class ClaimType(val code: String, val name: String, val evidenceRules: EvidenceRules)
data class EligiblePolicy(
    val id: String, val number: String, val productCode: String, val productName: String,
    val validFrom: LocalDate, val validTo: LocalDate, val claimTypes: List<ClaimType>,
)
data class ClaimDraft(val policyId: String, val typeCode: String, val description: String, val occurredAt: OffsetDateTime)
enum class ClaimStatus { REPORTED, UNKNOWN }          // "REPORTADO"; el resto llega con HU4.2.1
data class ReportedClaim(
    val id: String, val number: String, val status: ClaimStatus, val policyId: String, val policyNumber: String,
    val typeCode: String, val occurredAt: Instant, val reportedAt: Instant,
)

sealed interface PoliciesResult {
    data class Loaded(val policies: List<EligiblePolicy>) : PoliciesResult
    data object ClientNotRegistered : PoliciesResult
    data object SessionExpired : PoliciesResult
    data object NetworkError : PoliciesResult
    data object ServiceUnavailable : PoliciesResult
}
sealed interface ReportResult {
    data class Reported(val claim: ReportedClaim, val replayed: Boolean) : ReportResult
    data object PolicyNotFound : ReportResult
    data object PolicyNotActive : ReportResult
    data object OutOfCoverage : ReportResult
    data object InvalidDate : ReportResult
    data object TypeNotAllowed : ReportResult
    data object InvalidData : ReportResult
    data object IdempotencyConflict : ReportResult
    data object SessionExpired : ReportResult
    data object NetworkError : ReportResult
    data object ServiceUnavailable : ReportResult
}
sealed interface ClaimResult { data class Found(val claim: ReportedClaim) : ClaimResult; data object NotFound : ClaimResult; data object SessionExpired : ClaimResult; data object NetworkError : ClaimResult; data object ServiceUnavailable : ClaimResult }
```

### 2.2 `data/claims/ClaimsRepository.kt`

```kotlin
interface ClaimsRepository {
    /** `GET /api/v1/bff-mobile/siniestros/polizas-elegibles`. */
    suspend fun eligiblePolicies(accessToken: String, correlationId: String): PoliciesResult
    /** `POST /api/v1/bff-mobile/siniestros` con `Idempotency-Key`. */
    suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String): ReportResult
    /** `GET /api/v1/bff-mobile/siniestros/{id}`. HU4.1.2 amplía el modelo con evidencias y ubicación. */
    suspend fun claim(accessToken: String, claimId: String, correlationId: String): ClaimResult
}
```

### 2.3 `MockClaimsRepository`

- Datos semilla iguales a los del prototipo (titular ficticio, póliza de viaje `SLV-2026-08341`, una de protección de dispositivos y ninguna vencida en la lista).
- Guarda los reportes en un `Map<idempotencyKey, ReportedClaim>`: la misma clave devuelve `Reported(replayed = true)`.
- Reglas de demostración, por descripción (como los correos especiales de `MockAuthRepository`): contiene `#red` → `NetworkError`; `#503` → `ServiceUnavailable`; `#vencida` → `PolicyNotActive`.
- `delay(600)` para ver los estados de carga.

### 2.4 `RemoteClaimsRepository`

- Mismo esqueleto que `RemoteActivationRepository`: `OkHttpClient` inyectable, `ioDispatcher`, `authorized(url, token, correlationId)` y `Json { ignoreUnknownKeys = true }`.
- DTO `internal` con `@SerialName` en snake_case: `EligiblePoliciesResponseDto`, `PolicyDto`, `ProductDto`, `ClaimTypeDto`, `EvidenceRulesDto`, `ReportRequestDto`, `ClaimResponseDto`, `ApiErrorDto(codigo)`.
- `report`: header `Idempotency-Key`; `ocurrido_en` con `OffsetDateTime` en ISO-8601 **con zona**.
- Todas las llamadas envían `Accept-Language` con el idioma actual (`Locale.getDefault().toLanguageTag()`), para recibir el `nombre` de los tipos en ese idioma.
- Mapeo: `201` → `Reported(replayed = false)`; `200` → `Reported(replayed = true)`; `401` → `SessionExpired`; `404 POLIZA_NO_ENCONTRADA` → `PolicyNotFound`; `404 CLIENTE_NO_REGISTRADO` → `ClientNotRegistered`; `409 POLIZA_NO_VIGENTE` → `PolicyNotActive`; `409 FECHA_FUERA_DE_VIGENCIA` → `OutOfCoverage`; `409 IDEMPOTENCIA_CONFLICTO` → `IdempotencyConflict`; `422 FECHA_INVALIDA` → `InvalidDate`; `422 TIPO_NO_PERMITIDO` → `TypeNotAllowed`; otros `400`/`422` → `InvalidData`; `5xx` → `ServiceUnavailable`; `IOException` → `NetworkError`; cuerpo mal formado o `estado` desconocido → `ServiceUnavailable`.
- Nunca registra la descripción ni el token.

### 2.5 `feature/claims/report/ClaimFormValidator.kt`

```kotlin
enum class ClaimFieldError { POLICY_REQUIRED, TYPE_REQUIRED, DESCRIPTION_TOO_SHORT, DESCRIPTION_TOO_LONG, DATE_REQUIRED, TIME_REQUIRED, DATE_IN_FUTURE, DATE_OUT_OF_COVERAGE }

class ClaimFormValidator(private val clock: Clock, private val zone: ZoneId) {
    fun validatePolicy(policyId: String?): Set<ClaimFieldError>
    fun validateDetails(policy: EligiblePolicy, typeCode: String?, description: String, date: LocalDate?, time: LocalTime?): Set<ClaimFieldError>
    /** Rango para el selector de fecha: [vigencia desde, min(vigencia hasta, hoy)]. */
    fun selectableDates(policy: EligiblePolicy): ClosedRange<LocalDate>
}
```
Reglas: descripción con `trim()` entre 10 y 500; fecha y hora obligatorias; `occurredAt ≤ ahora`; fecha dentro de la vigencia (bordes inclusivos). El backend vuelve a validar todo.

### 2.6 `feature/claims/report/ClaimReportViewModel.kt`

```kotlin
enum class ReportStep { POLICY, DETAILS, REVIEW, SUBMITTING, DONE }
enum class ReportError { NO_POLICIES, POLICY_NOT_ACTIVE, OUT_OF_COVERAGE, INVALID_DATE, TYPE_NOT_ALLOWED, INVALID_DATA, NETWORK, SERVICE_UNAVAILABLE, CLIENT_NOT_REGISTERED }
sealed interface ReportDestination { data class Evidence(val claimId: String) : ReportDestination; data object Home : ReportDestination; data object Login : ReportDestination }

data class ClaimReportUiState(
    val step: ReportStep = ReportStep.POLICY,
    val loadingPolicies: Boolean = true,
    val policies: List<EligiblePolicy> = emptyList(),
    val selectedPolicyId: String? = null,
    val selectedTypeCode: String? = null,
    val description: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val fieldErrors: Set<ClaimFieldError> = emptySet(),
    val error: ReportError? = null,
    val reported: ReportedClaim? = null,
    val destination: ReportDestination? = null,
)

class ClaimReportViewModel(
    private val repository: ClaimsRepository,
    private val sessionStore: SessionStore,
    private val validator: ClaimFormValidator,
    private val savedState: SavedStateHandle,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val newCorrelationId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val uiState: StateFlow<ClaimReportUiState>
    fun loadPolicies()
    fun selectPolicy(policyId: String)          // limpia el tipo si no existe en la nueva póliza
    fun selectType(code: String)
    fun onDescriptionChange(text: String)       // corta en 500
    fun onDateSelected(date: LocalDate)
    fun onTimeSelected(time: LocalTime)
    fun continueFromPolicy()
    fun continueFromDetails()
    fun back()
    fun submit()
    fun retry()                                  // reintenta la última acción fallida con la misma clave
    fun goToEvidence()
    fun goHome()
    fun consumeDestination()
}
```
Reglas:
- **Clave de idempotencia:** se genera al entrar a `REVIEW` y se guarda en `SavedStateHandle` junto con una huella del borrador (`policyId|typeCode|description|occurredAt`). Si el usuario vuelve atrás y cambia algo, la huella cambia y se genera una nueva. Un reintento tras `NetworkError` o `ServiceUnavailable` usa la **misma** clave.
- **Un solo envío a la vez:** `submit()` se ignora si `step == SUBMITTING`.
- Sin sesión o `SessionExpired` → `destination = Login`.
- `IdempotencyConflict` (no debería ocurrir) → se genera una clave nueva y se muestra `SERVICE_UNAVAILABLE` con "Intentar de nuevo".
- `selectedPolicyId`, `selectedTypeCode`, `description`, `date`, `time` y `step` se guardan en `SavedStateHandle`.
- La hora se combina con `ZoneId.systemDefault()` para construir el `OffsetDateTime`.
- `factory(container)` con `viewModelFactory { initializer { … createSavedStateHandle() … } }`, como los demás.

### 2.7 Pantallas (Compose + Material 3, `SolventaButtons`)

| Composable | Contenido |
|---|---|
| `ClaimReportRoute` | Recoge `uiState`, consume `destination` y muestra el paso. Indicador de pasos "Póliza · Qué pasó · Revisar". `BackHandler` llama a `back()`. |
| `ClaimPolicyStep` | Tarjetas seleccionables: número, producto y "Vigente hasta …". Cargando: indicador. Vacío: "No tienes pólizas vigentes para reportar" + "Volver al inicio". Error: mensaje + "Intentar de nuevo". Primario: "Continuar con lo que pasó". |
| `ClaimDetailsStep` | "¿Qué pasó?" con `FilterChip` por tipo; "Cuéntanos brevemente" (`OutlinedTextField`, 3–6 líneas, contador `n/500`, error debajo); "¿Cuándo ocurrió?" con `DatePickerDialog` (`selectableDates` del validador) y `TimePicker`. Primario: "Revisar el reporte". |
| `ClaimReviewStep` | Resumen (póliza, tipo, descripción, fecha y hora) con "Editar" por bloque. Primario: "Enviar reporte"; cargando mientras `SUBMITTING`. Error: `Snackbar`/texto con "Intentar de nuevo". |
| `ClaimReportedScreen` | Ícono de éxito, "Reportamos tu siniestro", folio grande (seleccionable para copiar), póliza, "Reportado el …". Primario: "Adjuntar evidencia"; secundario: "Ir al inicio". |

- `ClaimReportTestTags` con etiquetas para las pruebas de UI.
- Accesibilidad: `contentDescription` en íconos, etiquetas de los campos, el folio leído carácter por carácter (`semantics { contentDescription = … }`), objetivos táctiles ≥ 48 dp.
- Todos los textos de la app en `values/strings.xml` y `values-en/strings.xml`. Los nombres de los tipos de siniestro **no** van en recursos: vienen del backend (tabla `tipo_siniestro`).

### 2.8 Cambios en código existente

| Archivo | Cambio |
|---|---|
| `di/AppContainer.kt` | `claimsRepository`: `MockClaimsRepository` si `USE_MOCK_BACKEND` o `USE_MOCK_CLAIMS`; si no, `RemoteClaimsRepository(BFF_MOBILE_URL)`. `claimFormValidator` con `Clock.systemDefaultZone()`. |
| `app/build.gradle.kts` | `buildConfigField("boolean", "USE_MOCK_CLAIMS", …)` desde `solventa.useMockClaims` (por defecto `false`), igual que `useMockConsent`. |
| `navigation/SolventaNavHost.kt` | `Routes.CLAIM_REPORT = "claims/report"` y `Routes.CLAIM_EVIDENCE = "claims/{claimId}/evidence"` (este último lo implementa HU4.1.2; mientras tanto "Adjuntar evidencia" va a Inicio). Al terminar se hace `popUpTo(CLAIM_REPORT) { inclusive = true }` para que "atrás" no vuelva al formulario enviado. |
| `feature/home/HomeScreen.kt` | Botón "Reportar un siniestro" (`onReportClaim`). Solo visible con el dispositivo vinculado. |
| `README.md` | Sección de siniestros y la propiedad `solventa.useMockClaims`. |
| `docs/contratos-pendientes.md` | Nueva sección "Siniestros (HU4.1.1)" con el estado de DEP-1 y DEP-2 del plan del backend. |

---

## 3. Validaciones

| Dónde | Validación |
|---|---|
| Póliza | Seleccionada y presente en la lista recibida. |
| Tipo | Seleccionado y perteneciente a la póliza elegida. |
| Descripción | 10–500 caracteres tras `trim()`. |
| Fecha y hora | Obligatorias; no futuras; dentro de la vigencia. |
| Envío | Sesión con `accessToken`; un solo envío a la vez; misma clave en los reintentos. |
| Respuesta | `estado` desconocido o campos faltantes → `ServiceUnavailable`. |

## 4. Contrato con el BFF Móvil (copia de consumo)

> Fuente: sección 3 del plan del backend. Muestras en `app/src/test/resources/contracts/siniestros/` (copia exacta de `solventa-backend/docs/contracts/siniestros/`).

| Endpoint | Request | Respuesta |
|---|---|---|
| `GET /api/v1/bff-mobile/siniestros/polizas-elegibles` | Bearer | `200 { polizas: [ { poliza_id, numero, producto{codigo,nombre}, vigencia_desde, vigencia_hasta, tipos_siniestro: [ { codigo, nombre, evidencia{fotos_max, videos_max} } ] } ] }` |
| `POST /api/v1/bff-mobile/siniestros` | Bearer, `Idempotency-Key: UUID`, `{ poliza_id, tipo, descripcion, ocurrido_en }` | `201` (nuevo) o `200` (reintento): `{ siniestro_id, numero, estado, poliza_id, poliza_numero, tipo, ocurrido_en, reportado_en }` |
| `GET /api/v1/bff-mobile/siniestros/{id}` | Bearer | `200` con el mismo cuerpo |

Errores (`{codigo, mensaje, correlation_id}`): `401` · `400/422 DATOS_INVALIDOS` · `422 FECHA_INVALIDA` · `422 TIPO_NO_PERMITIDO` · `404 CLIENTE_NO_REGISTRADO` · `404 POLIZA_NO_ENCONTRADA` · `404 SINIESTRO_NO_ENCONTRADO` · `409 POLIZA_NO_VIGENTE` · `409 FECHA_FUERA_DE_VIGENCIA` · `409 IDEMPOTENCIA_CONFLICTO` · `503 SERVICIO_NO_DISPONIBLE`.

## 5. Seguridad y privacidad

- Ni la descripción ni el token se registran en logs ni en mensajes de error.
- La app no envía `cliente_id` ni correo.
- Solo HTTPS en release (ya lo exige el build); HTTP solo en debug hacia `10.0.2.2`/`localhost`.

## 6. Pruebas

| Tipo | Clase | Casos mínimos |
|---|---|---|
| Unitaria | `ClaimFormValidatorTest` | límites 9/10/500/501 caracteres con espacios; fecha futura con reloj falso; bordes de vigencia; `selectableDates` cuando la vigencia termina antes de hoy. |
| Unitaria | `ClaimReportViewModelTest` | carga con 0, 1 (preselecciona) y varias pólizas; cambiar de póliza limpia el tipo; avanzar con errores no avanza; envío exitoso → `DONE`; doble envío ignorado; reintento tras `NetworkError` **con la misma clave**; editar tras un error genera **otra** clave; `SessionExpired` → `Login`; cada `ReportResult` → su `ReportError`; restauración desde `SavedStateHandle`. |
| Unitaria (MockWebServer) | `RemoteClaimsRepositoryTest` | headers (`Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Accept-Language`); cuerpo con `ocurrido_en` con zona y sin correo; `201`/`200`; cada código de error; cuerpo mal formado; desconexión. |
| Contrato | `RemoteClaimsRepositoryTest` con `contracts/siniestros/*.json` | cada muestra se parsea y se mapea. |
| Unitaria | `MockClaimsRepositoryTest` | reglas de demostración; misma clave → `replayed`. |
| UI (`androidTest`) | `ClaimReportScreenTest` | textos por paso; botón deshabilitado/errores visibles; estado vacío; pantalla de confirmación con folio y los dos botones. |
| Manual | Dispositivo o emulador con el backend de `docker compose` | reporte completo; modo avión en "Enviar" → reintento sin duplicado (verificar en la base). |

Gradle con poca RAM, una tarea a la vez:
```
./gradlew :app:testDebugUnitTest --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx1024m" -Pkotlin.compiler.execution.strategy=in-process
```

## 7. Reglas para el agente que implementa

1. Rama `feat/HU4.1.1-registrar-siniestro`. **No hacer commit ni push.**
2. Estilo existente: KDoc corto en español, clases en inglés, DTO con `@SerialName` snake_case, `StateFlow` + `update {}`, factories con `viewModelFactory { initializer { … } }`.
3. Sin Hilt/Koin/Retrofit: DI manual y OkHttp.
4. Toda cadena visible en `values/strings.xml` y `values-en/strings.xml`.
5. Orden: T-M01 → T-M03. Al terminar cada tarea: pruebas en verde y lista de archivos tocados.

## 8. Tareas (tarjetas) y criterio de terminado

| ID | Tarea | Estimación |
|---|---|---|
| T-M01 | Modelos y repositorio de siniestros: interfaz, simulado y remoto (2.1–2.4, 2.8) | 1,5 h |
| T-M02 | Flujo de reporte: validador, ViewModel, pantallas, navegación y textos (2.5–2.8) | 3 h |
| T-M03 | Pruebas de la app (JUnit, MockWebServer, contrato, Compose) y prueba manual (6) | 1,5 h |

**Terminado cuando:** pruebas unitarias en verde; pruebas de contrato en verde con las mismas muestras que el backend; reporte completo contra el backend de `docker compose` con evidencia (capturas y el registro en la base); reintento sin duplicado; textos en español e inglés; README y `contratos-pendientes.md` actualizados.
