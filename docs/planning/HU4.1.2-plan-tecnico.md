# HU4.1.2 · Adjuntar evidencia multimedia y geolocalización · Plan técnico (app Android)

- Historia: [#82](https://github.com/dzamoranouniandes/misw4501-grupo3-solventa-backend/issues/82) · Sprint 3 (02/11/2026 – 22/11/2026) · Responsable: Sergio Gelvez
- Rama: `feat/HU4.1.2-evidencia-geolocalizacion` (sale de `dev` con HU4.1.1 integrada)
- Backend: `solventa-backend/docs/planning/HU4.1.2-plan-tecnico.md`. **Su sección 3 es el contrato canónico; la sección 4 de este plan es una copia de consumo.**
- Prototipo: [app móvil › Siniestro asistido › Adjuntar evidencia](https://asr88.github.io/githubPagesProjectFinal/movil/) ("Foto del daño · La ubicación se adjunta automáticamente · Adjuntar evidencia y continuar").
- Depende de: HU4.1.1 (`ClaimsRepository`, `ReportedClaim`, `EvidenceRules`, ruta `claims/{claimId}/evidence`).

> Insumo para el agente que implementa. Describe **qué** construir y **cómo** encaja con el código existente.

---

## 0. Alcance y decisiones de base

| # | Criterio | En la app |
|---|---|---|
| CA1 | Fotos desde la cámara o el almacenamiento | **Cámara del sistema** (`ActivityResultContracts.TakePicture`) a un archivo privado vía `FileProvider`, y **selector de fotos del sistema** (`PickMultipleVisualMedia`, sin permiso de almacenamiento). Toda foto se recomprime a JPEG antes de subir. |
| CA2 | Videos cuando el tipo lo permita | Botón de video solo si `EvidenceRules.maxVideos > 0`. Grabación con la cámara del sistema (`ACTION_VIDEO_CAPTURE`, límite de 60 s y 50 MB) o desde el selector. Se valida duración, tamaño y tipo (`video/mp4`). |
| CA3 | Pedir permisos de cámara y ubicación | `CAMERA` antes de abrir la cámara (obligatorio: la app ya declara `CAMERA`, y sin otorgarlo el intent de cámara lanza `SecurityException`); `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` para la ubicación. Explicación previa, manejo de "no volver a preguntar" con enlace a Ajustes. |
| CA4 | Asociar la geolocalización | `FusedLocationProviderClient.getCurrentLocation` con timeout; se envía con `PUT /siniestros/{id}/ubicacion`. Con permiso ya otorgado se adjunta sola al entrar (como el prototipo). |
| CA5 | Evidencias asociadas al siniestro | Cada evidencia se registra en el siniestro (`POST …/evidencias`), se sube y se confirma. |
| CA6 | Si una evidencia falla, informar sin perder el resto | Cada evidencia tiene **su propio estado** en la pantalla (enviando, enviada, falló, rechazada) con "Reintentar" o "Quitar". El siniestro ya existe (HU4.1.1), así que el reporte nunca se pierde. Las evidencias pendientes se guardan y se ofrecen de nuevo al volver a la app. |

Decisiones (aprobadas el 07/11/2026; detalle en la sección 8):
- **A1 · Subida directa a S3** con la carga prefirmada que entrega el backend (`url` + `campos`): el archivo no pasa por el BFF.
- **A2 · Cola de envío en un ámbito de aplicación**, una evidencia a la vez, con reintentos y retroceso exponencial. **No se usa WorkManager** en esta historia: la sesión (token) vive solo en memoria (`InMemorySessionStore`), así que un trabajo en segundo plano con la app cerrada no tendría con qué autenticarse. Se retoma cuando exista refresh de token (pendiente 4b de `contratos-pendientes.md`).
- **A3 · Archivos solo en almacenamiento privado** (`filesDir/evidencias/`), nunca en la galería; se borran al confirmarse la evidencia o al quitarla.
- **A4 · Cámara y selector del sistema**, no CameraX propia: menos código, mejor calidad y sin mantener la UI de captura. CameraX sigue reservada para la prueba de vida.
- **A5 · Ubicación opcional:** si el cliente niega el permiso o no hay señal, el reporte sigue y se muestra "Sin ubicación" con opción de intentarlo de nuevo.

**Fuera de alcance:** asistencia en sitio (HU4.1.3), estado del siniestro (HU4.2.1), mapas, edición o recorte de fotos, transcodificación de video, subida con la app cerrada.

---

## 1. Arquitectura y patrones

| Patrón / táctica | Dónde | Por qué |
|---|---|---|
| Repository + mock | `EvidenceRepository` (simulado y remoto) | Avanzar sin backend; cambiar con `solventa.useMockClaims`. |
| Strategy | `StorageUploader` (`PresignedPostUploader` y `FakeStorageUploader`) | Subir a S3/MinIO o simular sin red; fácil de probar. |
| Adapter | `FusedLocationProvider` → `LocationFix` / `LocationResult` | La lógica no depende de Google Play Services; se prueba con fakes. |
| Máquina de estados por evidencia | `EvidenceUploadState` | Transiciones explícitas: `QUEUED → REQUESTING → UPLOADING → CONFIRMING → UPLOADED`, o `FAILED` / `REJECTED`. |
| Cola + coordinador (ámbito de aplicación) | `EvidenceUploadQueue` | La subida sigue aunque el usuario cambie de pantalla; una a la vez para no saturar la red ni la memoria. |
| Reintento con retroceso exponencial | `RetryPolicy` | Disponibilidad ante redes móviles inestables. |
| Aislamiento de fallas por elemento | Estado por evidencia | Criterio CA6. |
| Persistencia de pendientes | `PendingEvidenceStore` | Si Android mata la app, las evidencias no enviadas siguen ahí. |
| Reducir la demanda en el origen | `PhotoCompressor` | Fotos de 3–8 MB pasan a ~0,5–1,5 MB: menos datos y menos tiempo en redes móviles. |
| Mínimo privilegio | Permisos solo cuando se usan; selector de fotos sin permiso de almacenamiento; `FileProvider` limitado a `evidencias/` | Seguridad y privacidad. |

### 1.1 Flujo

```
ClaimReportedScreen ─ "Adjuntar evidencia" ─► claims/{claimId}/evidence
  al entrar: GET /siniestros/{id} (reglas, evidencias ya cargadas, ubicación)
             permiso de ubicación otorgado → LocationProvider.current() → PUT /ubicacion
  "Tomar foto"   → permiso CAMERA → TakePicture(uri privada) → PhotoCompressor → cola
  "Galería"      → PickMultipleVisualMedia(máx = cupo restante) → copiar a privado → PhotoCompressor → cola
  "Grabar video" → permiso CAMERA → CaptureVideoWithLimit(uri privada) → VideoInspector → cola
  "Elegir video" → PickVisualMedia(VideoOnly) → copiar → VideoInspector → cola

EvidenceUploadQueue (una a la vez):
  QUEUED → REQUESTING  POST /siniestros/{id}/evidencias        → ticket {evidencia_id, url, campos, expira_en}
         → UPLOADING   POST url (multipart: campos + file)     → 204
         → CONFIRMING  POST …/evidencias/{eid}/confirmacion    → CARGADA → UPLOADED (borra el archivo local)
  error reintentable → espera 2 s / 4 s / 8 s → reintenta (máx. 3) → FAILED ("Reintentar")
  error definitivo   → REJECTED (motivo, "Quitar")

"Terminar" → Inicio. Si quedan evidencias sin enviar: aviso "Se seguirán enviando mientras la app esté abierta".
Inicio: si hay pendientes guardadas → banner "Tienes evidencias sin enviar del siniestro SIN-…" → pantalla de evidencia.
```

### 1.2 Paquetes nuevos

```
app/src/main/java/com/solventa/app/
├── core/media/
│   ├── EvidenceFileStore.kt
│   ├── PhotoCompressor.kt         (+ ImageScaling, cálculo puro)
│   ├── VideoInspector.kt          (+ VideoRules, reglas puras)
│   ├── CaptureVideoWithLimit.kt   (ActivityResultContract)
│   └── Thumbnails.kt              (miniaturas en IO, sin librerías nuevas)
├── core/location/
│   ├── LocationProvider.kt        (LocationFix, LocationResult, interfaz)
│   ├── FusedLocationProvider.kt
│   └── AddressResolver.kt         (Geocoder; opcional)
├── core/permissions/
│   └── PermissionRequester.kt
├── data/claims/evidence/
│   ├── EvidenceModels.kt
│   ├── EvidenceRepository.kt      (+ Mock, Remote)
│   ├── StorageUploader.kt         (+ PresignedPostUploader, FakeStorageUploader)
│   ├── RetryPolicy.kt
│   ├── PendingEvidenceStore.kt
│   └── EvidenceUploadQueue.kt
└── feature/claims/evidence/
    ├── ClaimEvidenceViewModel.kt
    └── ClaimEvidenceScreen.kt
```

---

## 2. Diseño detallado

### 2.1 `core/media`

**`EvidenceFileStore(context)`**
- `dir = File(context.filesDir, "evidencias")`.
- `newFile(kind: EvidenceKind): File` → `"{uuid}.jpg"` o `.mp4`.
- `uriFor(file): Uri` → `FileProvider.getUriForFile(context, "${packageName}.evidencias", file)`.
- `suspend fun copyFrom(uri: Uri, kind): File` (IO, `contentResolver.openInputStream`).
- `delete(path)`, `purgeExcept(paths: Set<String>)` (al iniciar, borra archivos huérfanos).

**`PhotoCompressor`**
```kotlin
data class CompressedPhoto(val file: File, val sizeBytes: Long, val width: Int, val height: Int)
sealed interface CompressionResult { data class Ok(val photo: CompressedPhoto) : CompressionResult; data object Unreadable : CompressionResult; data object TooLarge : CompressionResult }
class PhotoCompressor(private val maxSide: Int = 2048, private val maxBytes: Long = 5L * 1024 * 1024) {
    suspend fun compress(source: File, target: File): CompressionResult   // Dispatchers.Default
}
object ImageScaling {
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int           // potencia de 2
    fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int>
}
```
Pasos: leer la orientación con `ExifInterface` → decodificar con `inJustDecodeBounds` + `inSampleSize` → escalar al lado mayor ≤ 2048 → rotar → JPEG calidad 85; si pesa más de 5 MB, bajar a 75 y 65; si aún pesa más → `TooLarge`. El JPEG nuevo **no lleva EXIF** (no filtra la ubicación ni el modelo del teléfono; la ubicación se envía aparte y con permiso). Reciclar los `Bitmap`.

**`VideoInspector`**
```kotlin
data class VideoInfo(val durationMs: Long, val sizeBytes: Long, val mimeType: String?)
class VideoInspector { fun inspect(file: File): VideoInfo? }          // MediaMetadataRetriever
object VideoRules {
    const val MAX_DURATION_MS = 60_000L
    const val MAX_BYTES = 50L * 1024 * 1024
    fun check(info: VideoInfo): VideoCheck                              // OK, TOO_LONG, TOO_LARGE, UNSUPPORTED
}
```
Solo `video/mp4`.

**`CaptureVideoWithLimit : ActivityResultContract<Uri, Boolean>`** — `Intent(MediaStore.ACTION_VIDEO_CAPTURE)` con `EXTRA_OUTPUT`, `EXTRA_DURATION_LIMIT = 60`, `EXTRA_SIZE_LIMIT = 50 MB` y `FLAG_GRANT_WRITE_URI_PERMISSION`. Algunas cámaras ignoran los límites: por eso `VideoRules` vuelve a validar.

**`Thumbnails`** — `@Composable rememberThumbnail(path, kind): ImageBitmap?` con `produceState` en `Dispatchers.IO` (foto: `BitmapFactory` con `inSampleSize`; video: `MediaMetadataRetriever.getFrameAtTime`). Sin Coil.

### 2.2 `core/location`

```kotlin
enum class LocationSource { GPS, NETWORK, LAST_KNOWN }          // → "GPS", "RED", "ULTIMA_CONOCIDA"
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val capturedAt: Instant, val source: LocationSource)
sealed interface LocationResult {
    data class Found(val fix: LocationFix) : LocationResult
    data object PermissionDenied : LocationResult
    data object Disabled : LocationResult          // ubicación del sistema apagada
    data object Unavailable : LocationResult       // sin Play Services o sin señal
    data object Timeout : LocationResult
}
fun interface LocationProvider { suspend fun current(timeoutMs: Long = 10_000): LocationResult }
```
**`FusedLocationProvider(context, clock)`**: verifica el permiso (fino o aproximado) y `LocationManagerCompat.isLocationEnabled`; `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource)` envuelto en `suspendCancellableCoroutine` + `withTimeoutOrNull`; si no responde, `lastLocation` con antigüedad ≤ 2 min (`LAST_KNOWN`). Con solo permiso aproximado acepta el resultado (la precisión se envía tal cual).

**`AddressResolver`** (opcional, solo para mostrar "Bogotá, Colombia"): `GeocoderAddressResolver` con `Geocoder.getFromLocation` asíncrono en API 33+ y en IO para versiones anteriores; si falla, `null` y la UI muestra las coordenadas con su precisión.

### 2.3 `core/permissions/PermissionRequester.kt`

```kotlin
enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED }
@Composable fun rememberPermissionRequester(permissions: Array<String>, onResult: (PermissionStatus) -> Unit): () -> Unit
fun Context.isGranted(vararg permissions: String): Boolean
fun Context.openAppSettings()
```
`PERMANENTLY_DENIED` = denegado y `shouldShowRequestPermissionRationale == false` después de haber preguntado (se guarda en `SharedPreferences` que ya se preguntó, para distinguirlo de la primera vez).

### 2.4 `data/claims/evidence/EvidenceModels.kt`

```kotlin
enum class EvidenceKind(val api: String, val mime: String) { PHOTO("FOTO", "image/jpeg"), VIDEO("VIDEO", "video/mp4") }
enum class EvidenceOrigin(val api: String) { CAMERA("CAMARA"), GALLERY("GALERIA") }
enum class EvidenceUploadState { QUEUED, REQUESTING, UPLOADING, CONFIRMING, UPLOADED, FAILED, REJECTED }
enum class EvidenceProblem { NETWORK, SERVICE_UNAVAILABLE, SESSION_EXPIRED, LIMIT_REACHED, TOO_LARGE, NOT_ALLOWED, REJECTED_BY_SERVER, CLAIM_CLOSED }

@Serializable
data class LocalEvidence(
    val localId: String, val claimId: String, val claimNumber: String,
    val kind: EvidenceKind, val origin: EvidenceOrigin,
    val filePath: String?, val sizeBytes: Long, val capturedAt: String,
    val remoteId: String? = null,
    val state: EvidenceUploadState = EvidenceUploadState.QUEUED,
    val attempts: Int = 0, val problem: EvidenceProblem? = null,
    val progress: Float = 0f,
)
data class UploadTicket(val evidenceId: String, val url: String, val fields: Map<String, String>, val expiresAt: Instant, val maxBytes: Long)
data class RemoteEvidence(val id: String, val kind: EvidenceKind, val state: String, val sizeBytes: Long?, val capturedAt: Instant, val uploadedAt: Instant?)
```
`ReportedClaim` (HU4.1.1) gana `location: LocationFix?` y `evidences: List<RemoteEvidence>`.

### 2.5 `EvidenceRepository`

```kotlin
sealed interface TicketResult { data class Issued(val ticket: UploadTicket) : TicketResult; data class Refused(val problem: EvidenceProblem) : TicketResult; data object SessionExpired : TicketResult; data object NetworkError : TicketResult; data object ServiceUnavailable : TicketResult }
sealed interface ConfirmResult { data object Confirmed : ConfirmResult; data object NotUploadedYet : ConfirmResult; data object Expired : ConfirmResult; data object Rejected : ConfirmResult; data object SessionExpired : ConfirmResult; data object NetworkError : ConfirmResult; data object ServiceUnavailable : ConfirmResult }
sealed interface LocationSaveResult { data object Saved : LocationSaveResult; data object ClaimClosed : LocationSaveResult; data object SessionExpired : LocationSaveResult; data object NetworkError : LocationSaveResult; data object ServiceUnavailable : LocationSaveResult }

interface EvidenceRepository {
    suspend fun requestUpload(accessToken: String, claimId: String, kind: EvidenceKind, sizeBytes: Long, origin: EvidenceOrigin, capturedAt: OffsetDateTime, correlationId: String): TicketResult
    suspend fun confirm(accessToken: String, claimId: String, evidenceId: String, correlationId: String): ConfirmResult
    suspend fun saveLocation(accessToken: String, claimId: String, fix: LocationFix, correlationId: String): LocationSaveResult
}
```
- **Remoto:** mismo esqueleto que `RemoteClaimsRepository`. Mapeo de códigos: `409 LIMITE_EVIDENCIAS` → `Refused(LIMIT_REACHED)`; `413` → `TOO_LARGE`; `415`/`422 TIPO_EVIDENCIA_NO_PERMITIDO` → `NOT_ALLOWED`; `409 SINIESTRO_NO_ADMITE_EVIDENCIAS` → `CLAIM_CLOSED`; confirmación: `409 CARGA_NO_ENCONTRADA` → `NotUploadedYet`, `409 CARGA_EXPIRADA` → `Expired`, `422 EVIDENCIA_RECHAZADA` → `Rejected`.
- **Simulado:** emite tickets falsos, recuerda cuántas evidencias hay por siniestro y aplica las `EvidenceRules`; un archivo cuyo nombre contenga `fail` falla dos veces con `NetworkError` (para ver el reintento).
- Nunca registra la URL, los `campos` ni las coordenadas.

### 2.6 `StorageUploader`

```kotlin
sealed interface StorageUploadResult { data object Uploaded : StorageUploadResult; data object Expired : StorageUploadResult; data object Refused : StorageUploadResult; data object NetworkError : StorageUploadResult }
fun interface StorageUploader { suspend fun upload(ticket: UploadTicket, file: File, mime: String, onProgress: (Float) -> Unit): StorageUploadResult }
```
**`PresignedPostUploader(client: OkHttpClient)`**: `MultipartBody` con **todos** los `fields` en el orden recibido y la parte `file` **al final** (requisito de S3); el cuerpo del archivo es un `RequestBody` que lee del `File` en bloques y reporta progreso (no carga el video en memoria). Cliente propio con `writeTimeout` de 120 s y **sin** el header `Authorization` (la URL firmada es la credencial). `204`/`200` → `Uploaded`; `403` con `AccessDenied`/`Policy expired` → `Expired`; `400 EntityTooLarge`/otro `4xx` → `Refused`; `IOException`/`5xx` → `NetworkError`.

### 2.7 `RetryPolicy`, `PendingEvidenceStore` y `EvidenceUploadQueue`

```kotlin
class RetryPolicy(val maxAttempts: Int = 3, private val baseDelayMs: Long = 2_000) {
    fun delayFor(attempt: Int): Long            // 2 s, 4 s, 8 s
    fun isRetryable(problem: EvidenceProblem): Boolean   // NETWORK, SERVICE_UNAVAILABLE
}

class PendingEvidenceStore(file: File, json: Json) {   // filesDir/evidencias/pendientes.json
    suspend fun load(): List<LocalEvidence>
    suspend fun save(items: List<LocalEvidence>)        // escritura atómica: archivo temporal + rename
}

class EvidenceUploadQueue(
    private val repository: EvidenceRepository,
    private val uploader: StorageUploader,
    private val sessionStore: SessionStore,
    private val store: PendingEvidenceStore,
    private val fileStore: EvidenceFileStore,
    private val policy: RetryPolicy,
    private val scope: CoroutineScope,                    // SupervisorJob + Dispatchers.IO, vive con la app
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val items: StateFlow<List<LocalEvidence>>
    fun itemsFor(claimId: String): Flow<List<LocalEvidence>>
    fun pendingClaims(): Flow<List<Pair<String, String>>>          // (claimId, claimNumber) con pendientes
    fun enqueue(claimId: String, claimNumber: String, file: File, kind: EvidenceKind, origin: EvidenceOrigin, capturedAt: OffsetDateTime)
    fun retry(localId: String)
    fun remove(localId: String)                                    // borra el archivo; no borra lo ya CARGADO en el servidor
    fun resume()                                                   // tras iniciar sesión: retoma QUEUED, FAILED reintentables y FAILED(SESSION_EXPIRED)
}
```
Reglas del procesador (un `Mutex`, una evidencia a la vez, en orden de llegada):
1. Sin sesión → deja el ítem en `QUEUED` y espera a `resume()`.
2. Sin ticket en memoria (nuevo, vencido o perdido al cerrar la app) → `REQUESTING` → `requestUpload` con `Idempotency-Key = localId`: el backend reutiliza la misma evidencia y no se gasta otro cupo. `AlreadyUploaded` (ya `CARGADA` en el servidor) → `UPLOADED`. `Refused` → `REJECTED` con el problema (no reintenta).
3. `UPLOADING` → `uploader.upload`. `Expired` → pide un ticket nuevo (cuenta como intento). `Refused` → `REJECTED(TOO_LARGE)`.
4. `CONFIRMING` → `confirm`. `NotUploadedYet` → vuelve a subir; `Expired` → ticket nuevo; `Rejected` → `REJECTED(REJECTED_BY_SERVER)`.
5. Éxito → `UPLOADED`, borra el archivo local y lo saca de `pendientes.json`.
6. `NETWORK`/`SERVICE_UNAVAILABLE` → reintenta con `policy.delayFor(attempt)` hasta `maxAttempts`; después `FAILED` (el usuario decide "Reintentar").
7. `SessionExpired` → `FAILED(SESSION_EXPIRED)`; `resume()` (al iniciar sesión o renovar el token) la vuelve a la cola sola.
8. Cada cambio de estado se guarda en `PendingEvidenceStore` (sin `progress`).

### 2.8 `feature/claims/evidence/ClaimEvidenceViewModel.kt`

```kotlin
sealed interface LocationUi { data object Idle : LocationUi; data object Locating : LocationUi; data class Attached(val fix: LocationFix, val address: String?) : LocationUi; data object Denied : LocationUi; data object PermanentlyDenied : LocationUi; data object Disabled : LocationUi; data object Unavailable : LocationUi; data object SaveFailed : LocationUi }
enum class EvidenceMessage { PHOTO_UNREADABLE, PHOTO_TOO_LARGE, VIDEO_TOO_LONG, VIDEO_TOO_LARGE, VIDEO_UNSUPPORTED, LIMIT_REACHED, CAMERA_DENIED, CAMERA_PERMANENTLY_DENIED, LOAD_FAILED }
sealed interface EvidenceDestination { data object Home : EvidenceDestination; data object Login : EvidenceDestination }

data class ClaimEvidenceUiState(
    val loading: Boolean = true,
    val claim: ReportedClaim? = null,
    val rules: EvidenceRules? = null,
    val uploaded: List<RemoteEvidence> = emptyList(),
    val local: List<LocalEvidence> = emptyList(),
    val location: LocationUi = LocationUi.Idle,
    val remainingPhotos: Int = 0,
    val remainingVideos: Int = 0,
    val message: EvidenceMessage? = null,
    val confirmLeaveWithPending: Boolean = false,
    val destination: EvidenceDestination? = null,
)

class ClaimEvidenceViewModel(
    private val claimId: String,
    private val claimsRepository: ClaimsRepository,
    private val evidenceRepository: EvidenceRepository,
    private val queue: EvidenceUploadQueue,
    private val locationProvider: LocationProvider,
    private val addressResolver: AddressResolver?,
    private val fileStore: EvidenceFileStore,
    private val compressor: PhotoCompressor,
    private val videoInspector: VideoInspector,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<ClaimEvidenceUiState>
    fun load()
    fun newPhotoTarget(): Pair<File, Uri>               // archivo privado + uri del FileProvider para la cámara
    fun newVideoTarget(): Pair<File, Uri>
    fun onPhotoTaken(file: File, success: Boolean)
    fun onVideoRecorded(file: File, success: Boolean)
    fun onGalleryPicked(uris: List<Uri>)                 // fotos o videos según el mime
    fun onCameraPermission(status: PermissionStatus)
    fun onLocationPermission(status: PermissionStatus)
    fun attachLocation()
    fun retry(localId: String)
    fun remove(localId: String)
    fun finish()                                          // con pendientes → confirmLeaveWithPending
    fun confirmLeave()
    fun dismissMessage()
    fun consumeDestination()
}
```
Reglas:
- `remainingPhotos = rules.maxPhotos − (fotos CARGADA en el servidor + fotos locales no REJECTED)`; igual para videos. El backend es la autoridad: si responde `LIMIT_REACHED`, se muestra en el ítem.
- El cupo restante es el `maxItems` del selector; con cupo 0 los botones se deshabilitan con el texto "Llegaste al máximo de fotos".
- Captura cancelada (`success = false`) → borra el archivo vacío, sin mensaje.
- Archivos de galería: se copian a privado; los de video se validan con `VideoRules` antes de encolarse.
- Ubicación: en `load()`, con permiso ya otorgado, llama `attachLocation()`; sin permiso queda `Idle` con el botón "Adjuntar ubicación". `attachLocation()` → `Locating` → `current()` → `saveLocation` → `Attached` o el estado de error; un fallo al guardar → `SaveFailed` con "Reintentar".
- `SessionExpired` en cualquier paso → `destination = Login`.
- `capturedAt` de la evidencia = hora de la captura (cámara) o la de selección (galería), con zona.

### 2.9 `ClaimEvidenceScreen` (Compose + Material 3)

| Bloque | Contenido |
|---|---|
| Encabezado | "Evidencia del siniestro" + folio `SIN-…` + indicador de pasos (paso 2). |
| Fotos | Cuadrícula con miniaturas (enviadas y locales). Cada ítem muestra su estado: progreso circular (`UPLOADING`), check (`UPLOADED`), ícono de alerta con "Reintentar" (`FAILED`), "No se pudo adjuntar: <motivo>" con "Quitar" (`REJECTED`). Botones "Tomar foto" y "Elegir de la galería" con "Puedes agregar N más". |
| Video | Solo si `rules.allowsVideo`: "Grabar video (máx. 60 s)" y "Elegir video". |
| Ubicación | Tarjeta: "Ubicación adjunta · Bogotá, Colombia · ±18 m" / "Buscando tu ubicación…" / "Sin ubicación: no diste permiso" + "Dar permiso" o "Abrir ajustes" / "Activa la ubicación del teléfono" / "Reintentar". Texto de por qué se pide. |
| Pie | Primario: "Terminar". Diálogo si hay pendientes: "Hay N evidencias enviándose. Se seguirán enviando mientras la app esté abierta, y si se interrumpe podrás reintentarlo desde Inicio." |
| Permisos | Antes de pedir `CAMERA` o ubicación por primera vez, una hoja con la explicación (para qué se usa y que la foto no queda en la galería). |

- `ClaimEvidenceTestTags`; accesibilidad: cada miniatura con `contentDescription` "Foto 2, enviada" / "Video 1, falló el envío"; botones de reintentar con el nombre del ítem.
- Textos en `values` y `values-en`.

### 2.10 Cambios en código existente

| Archivo | Cambio |
|---|---|
| `AndroidManifest.xml` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`; `<uses-feature android:name="android.hardware.location.gps" android:required="false"/>`; `<provider androidx.core.content.FileProvider authorities="${applicationId}.evidencias" exported="false" grantUriPermissions="true">` con `@xml/evidence_paths`; `<queries>` para `android.media.action.IMAGE_CAPTURE` y `android.media.action.VIDEO_CAPTURE` (visibilidad de paquetes, API 30+). |
| `res/xml/evidence_paths.xml` (nuevo) | `<files-path name="evidencias" path="evidencias/"/>` y nada más. |
| `res/xml/data_extraction_rules.xml` | Excluir `evidencias/` de copias y transferencias (refuerza `allowBackup=false`). |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `com.google.android.gms:play-services-location` y `androidx.exifinterface:exifinterface` (versiones estables vigentes; verificar en Maven). El emulador debe tener imagen con Google APIs. |
| `di/AppContainer.kt` | `evidenceFileStore`, `photoCompressor`, `videoInspector`, `locationProvider = FusedLocationProvider(context, clock)`, `addressResolver`, `evidenceRepository` (simulado/remoto como `claimsRepository`), `storageUploader` (`FakeStorageUploader` con el backend simulado), `evidenceUploadQueue` con `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. |
| `SolventaApplication.kt` | Al iniciar: cargar `PendingEvidenceStore` en la cola y `fileStore.purgeExcept(rutas pendientes)`. |
| `feature/login/LoginViewModel.kt` / `UnlockViewModel` | Tras iniciar sesión o desbloquear: `evidenceUploadQueue.resume()`. |
| `navigation/SolventaNavHost.kt` | `composable(Routes.CLAIM_EVIDENCE, arguments = listOf(navArgument("claimId") { type = NavType.StringType }))`. "Adjuntar evidencia" de HU4.1.1 navega aquí. |
| `feature/home/HomeScreen.kt` | Banner de pendientes con `pendingClaims()`. |
| `docs/contratos-pendientes.md`, `README.md` | Evidencias, MinIO local (`10.0.2.2:9000`, o `adb reverse tcp:9000 tcp:9000` en un teléfono) y permisos. |

---

## 3. Validaciones

| Dónde | Validación |
|---|---|
| Foto | Legible; tras comprimir ≤ 5 MB; JPEG. |
| Video | `video/mp4`; ≤ 60 s; ≤ 50 MB. |
| Cantidad | Cupo restante según `EvidenceRules` (el backend vuelve a validar). |
| Ubicación | Permiso fino o aproximado; ubicación del sistema activa; timeout 10 s; `lastLocation` ≤ 2 min. |
| Ticket | No vencido antes de subir; si vence, se pide otro. |
| Subida | Todos los `campos` firmados y el archivo al final; sin `Authorization`. |

## 4. Contrato con el BFF Móvil y S3 (copia de consumo)

> Fuente: sección 3 del plan del backend. Muestras en `app/src/test/resources/contracts/evidencias/`.

| Paso | Request | Respuesta |
|---|---|---|
| Solicitar carga | `POST /api/v1/bff-mobile/siniestros/{id}/evidencias` `{ tipo: FOTO\|VIDEO, content_type, tamano_bytes, origen: CAMARA\|GALERIA, capturada_en }` | `201 { evidencia_id, estado: PENDIENTE, carga: { url, campos{…}, expira_en, tamano_max_bytes } }` |
| Subir | `POST {carga.url}` multipart: `campos` + `file` al final, sin Bearer | `204`; `403` política vencida o condición incumplida; `400 EntityTooLarge` |
| Confirmar | `POST /api/v1/bff-mobile/siniestros/{id}/evidencias/{evidencia_id}/confirmacion` | `200 { evidencia_id, tipo, estado: CARGADA, tamano_bytes, capturada_en, cargada_en }` (idempotente) |
| Ubicación | `PUT /api/v1/bff-mobile/siniestros/{id}/ubicacion` `{ latitud, longitud, precision_m, fuente: GPS\|RED\|ULTIMA_CONOCIDA, capturada_en }` | `200 { ubicacion, actualizada_en }` |
| Detalle | `GET /api/v1/bff-mobile/siniestros/{id}` | HU4.1.1 + `ubicacion` (o `null`) + `evidencias[]` |

Errores: `404 SINIESTRO_NO_ENCONTRADO` · `404 EVIDENCIA_NO_ENCONTRADA` · `409 SINIESTRO_NO_ADMITE_EVIDENCIAS` · `409 LIMITE_EVIDENCIAS` · `409 CARGA_NO_ENCONTRADA` · `409 CARGA_EXPIRADA` · `413 EVIDENCIA_DEMASIADO_GRANDE` · `415 FORMATO_NO_SOPORTADO` · `422 TIPO_EVIDENCIA_NO_PERMITIDO` · `422 EVIDENCIA_RECHAZADA` · `422 DATOS_INVALIDOS` · `503 SERVICIO_NO_DISPONIBLE`.

## 5. Seguridad y privacidad

- Archivos solo en `filesDir/evidencias/` (privado, excluido de copias), compartidos con la cámara solo por `FileProvider` con permiso temporal; borrados al confirmarse o al quitarlos.
- Las fotos se recomprimen sin EXIF; la ubicación se envía solo con permiso y por su endpoint.
- La URL firmada y sus `campos` no se registran ni se guardan en `pendientes.json` (se guarda solo `remoteId`; el ticket se vuelve a pedir si hace falta).
- Coordenadas y token fuera de los logs.
- Selector de fotos del sistema: no se pide `READ_MEDIA_IMAGES` ni `READ_EXTERNAL_STORAGE`.

## 6. Pruebas

| Tipo | Clase | Casos mínimos |
|---|---|---|
| Unitaria | `ImageScalingTest`, `VideoRulesTest`, `RetryPolicyTest` | escalado y `inSampleSize`; bordes de 60 s y 50 MB; tipos no soportados; retrocesos 2/4/8 s; qué problemas se reintentan. |
| Unitaria | `EvidenceUploadQueueTest` (fakes de repositorio y uploader, `StandardTestDispatcher`) | camino feliz; una falla de red y luego éxito; 3 fallas → `FAILED` y `retry()` lo retoma; ticket vencido → pide otro; `NotUploadedYet` → vuelve a subir; `Refused` → `REJECTED` sin reintento; **una evidencia que falla no detiene a las siguientes**; sin sesión → espera `resume()`; persiste en cada cambio y restaura al iniciar; `remove()` borra el archivo. |
| Unitaria | `PendingEvidenceStoreTest` (carpeta temporal) | guardar y cargar; archivo corrupto → lista vacía; escritura atómica. |
| Unitaria | `ClaimEvidenceViewModelTest` | cupos con evidencias enviadas y locales; video oculto si `maxVideos = 0`; captura cancelada; foto ilegible y video largo → mensaje; ubicación con permiso al entrar, sin permiso, permanentemente denegado, apagada, timeout, fallo al guardar; terminar con pendientes → diálogo; `SessionExpired` → `Login`. |
| Unitaria (MockWebServer) | `RemoteEvidenceRepositoryTest` | headers; cuerpo de la solicitud; mapeo de cada código; cuerpo mal formado; desconexión. |
| Unitaria (MockWebServer como S3) | `PresignedPostUploaderTest` | todos los campos en orden y `file` al final; sin `Authorization`; progreso; `204`; `403` → `Expired`; `400` → `Refused`; desconexión → `NetworkError`. |
| Contrato | `RemoteEvidenceRepositoryTest` con `contracts/evidencias/*.json` | cada muestra se parsea. |
| UI (`androidTest`) | `ClaimEvidenceScreenTest` | estados de cada ítem con sus textos y botones; botón de video según reglas; tarjeta de ubicación en cada estado; diálogo de pendientes. |
| Manual (dispositivo real, OBJ-09 de la estrategia de pruebas) | — | foto con cámara; foto de galería; video de 10 s; video > 60 s rechazado; permisos negados y "no volver a preguntar"; ubicación con GPS y en interiores; **modo avión durante la subida** → `FAILED` y "Reintentar" sin perder el reporte; cerrar la app con pendientes → banner al volver; verificar los objetos en MinIO y los registros en la base. |

## 7. Reglas para el agente que implementa

1. Rama `feat/HU4.1.2-evidencia-geolocalizacion`. **No hacer commit ni push.**
2. Estilo existente (KDoc corto en español, clases en inglés, `StateFlow`, DI manual, OkHttp, sin librerías de imágenes ni WorkManager).
3. Nunca escribir evidencias fuera de `filesDir/evidencias/` ni en la galería; nunca registrar URL firmadas ni coordenadas.
4. Gradle con poca RAM, una tarea a la vez (comando del plan de HU4.1.1).
5. Orden: T-M01 → T-M05. Al terminar cada tarea: pruebas en verde y lista de archivos tocados.

## 8. Decisiones

| ID | Tema | Estado |
|---|---|---|
Aprobadas por Sergio Gelvez el 07/11/2026.

| ID | Tema | Estado |
|---|---|---|
| A1 | Subida directa a S3 con carga prefirmada | **Aprobada** (igual a E1/E2 del backend) |
| A2 | Cola en ámbito de aplicación, sin WorkManager, hasta tener refresh de token | **Aprobada** |
| A3 | Evidencias solo en almacenamiento privado; se borran al confirmarse | **Aprobada** |
| A4 | Cámara y selector del sistema en lugar de CameraX propia | **Aprobada** |
| A5 | Ubicación opcional y automática si ya hay permiso | **Aprobada** |
| A6 | Fotos recomprimidas a JPEG ≤ 2048 px, sin EXIF | **Aprobada** |

## 9. Tareas (tarjetas) y criterio de terminado

| ID | Tarea | Estimación |
|---|---|---|
| T-M01 | Permisos, captura de fotos y videos, compresión y almacenamiento privado (2.1, 2.3, manifest) | 3 h |
| T-M02 | Geolocalización: proveedor, timeout, dirección legible (2.2) | 1,5 h |
| T-M03 | Envío por evidencia: repositorio, subida a S3 y cola con reintentos y pendientes (2.4–2.7) | 3 h |
| T-M04 | Pantalla de evidencia, ViewModel, navegación y banner de pendientes (2.8–2.10) | 2,5 h |
| T-M05 | Pruebas de la app y prueba manual en dispositivo real (6) | 2 h |

**Terminado cuando:** pruebas unitarias y de contrato en verde; prueba manual en un **dispositivo real** con todos los casos de la sección 6 y evidencia (capturas, objetos en MinIO, registros en la base); textos en español e inglés; README y `contratos-pendientes.md` actualizados.
