# HU3.1.1 · Revisión de código de la app · 31/10/2026

## Cuarta pasada · implementación de lo pendiente (sin commit)

**Verificación:** `testDebugUnitTest --rerun` **129 pruebas en verde**; `lintDebug` sin errores; `compileDebugAndroidTestKotlin` y `assembleDebug` correctos. Las pruebas instrumentadas (Compose) **no se ejecutaron**: no hay emulador ni dispositivo en esta máquina.

| Tarea / hallazgo | Estado | Qué se hizo |
|---|---|---|
| T-M01 / T-M08 / A-05 | 🟢 | `ActivationViewModel` + `MockActivationRepository`; repositorios desde `AppContainer`. |
| T-M02 / A-08 | 🟢 | `DeviceInfoProvider` sobre el `InstallationIdProvider` del contenedor. |
| T-M03 / A-02 | 🟢 | `LoginDestination.ACTIVATION` (sin vincular o sin bloque `cliente`) → consentimiento → activación; enlace permanente al registro web. |
| T-M04 | 🟢 | Consentimiento: pantalla, rechazo, ViewModel, repositorio simulado (por defecto) y remoto (contrato 4.3). |
| T-M05 | 🟢 | `CaptureLauncherFactory` + `OwnCaptureLauncher` + lanzadores de Didit. |
| T-M06 / T-M07 / A-03 / A-14 | 🟢 | `CaptureGuideEngine` (puro), `MlKitFaceAnalyzer`, `FrontCameraPreview` (CameraX 1.6.2, ML Kit 16.1.7), frames del mismo `ImageAnalysis` con su hora real. |
| A-04 / A-09 | 🟢 | `CapturedFrames.wipe()` en `finally` y `onCleared`; `FLAG_SECURE`. |
| A-06 / A-13 / A-12 / A-10 | 🟢 | Mapeo por código, sin `catch (Exception)`, DTO `@Serializable`, `X-Demo-Correo` solo con `solventa.demoActivationEmail` en debug. |
| T-M09 / T-M11 / A-07 | 🟢 | Intro, captura con guía accesible, resultado; textos en `values` y `values-en` (con plurales). |
| T-M13 | 🟢 | `SdkDiditCaptureLauncher` con `me.didit:didit-sdk-core:4.9.1` detrás de `solventa.diditSdkEnabled=false`. **No probado en dispositivo.** |
| T-M12 / A-15 | 🟢 | Pruebas de motor, borrado, contrato (muestras idénticas al backend), mock, factory, ViewModels y repositorios; README. |
| A-11 | 🟡 | Sigue `/docs/evidencias/` en `.gitignore`: decisión pendiente. |
| Calibración | 🟡 | Signo de `headEulerY` para "izquierda" (`GuideConfig.turnLeftSign`) frente a MediaPipe: confirmar en un teléfono real. |

## Tercera pasada (commit `928b545`, 7 commits locales sin publicar)

- **La app vuelve a compilar.** `testDebugUnitTest --rerun`: **72 pruebas, todas en verde.** ✅
- El commit solo corrige la compilación (`when (val result = …)`). **Los hallazgos A-02 a A-15 siguen abiertos**; el orden de trabajo de la sección 4 de abajo no cambia, empezando por el punto 2.

---

# Segunda pasada

Rama `feat/HU3.1.1-activacion-selfie-prueba-vida`, último commit `85961cb`. Hay **6 commits locales sin publicar**. Se compara contra [HU3.1.1-plan-tecnico.md](HU3.1.1-plan-tecnico.md) y contra la primera pasada de hoy.

## ⛔ La app no compila

`./gradlew :app:testDebugUnitTest --rerun` falla en `:app:compileDebugKotlin`:

```
ActivationScreen.kt:73:59  Unresolved reference 'result'.
ActivationScreen.kt:73:114 Unresolved reference 'result'.
```

- **Causa:** el commit `17b6d89` ("informa resultado y reintentos de activación") usa `result.remainingAttempts` y `result.reason` en la rama `is ActivationVerificationResult.Rejected`, pero el `when` no declara la variable.
- **Ajuste:** `when (val result = repository.verify(accessToken, challenge!!, frames.toList())) {`.
- **Por qué pasó desapercibido:** Gradle consideraba la tarea de pruebas al día, así que daba "verde" con resultados anteriores (solo 2 pruebas). Hay que **correr las pruebas con `--rerun`** antes de cada commit.

## 1. Estado de los hallazgos de la primera pasada

| Id | Estado | Comentario |
|---|---|---|
| A-01 Sin token / correo en el cuerpo | 🟢 Resuelto | Envía `Authorization: Bearer` y quitó `correo` del cuerpo. Ver A-12. |
| A-02 Uso sin activar | 🟡 Parcial | El login ahora va a la activación si el dispositivo no está vinculado ✅. Pero `HomeScreen` sigue mostrando el botón "Activar este teléfono" y el arranque con biometría va directo a Inicio sin revisar la vinculación. |
| A-03 Cámara del sistema, sin guía | 🔴 Pendiente | Sigue `TakePicturePreview`. El texto ya muestra el gesto, pero en código crudo (`TURN_LEFT`). |
| A-04 Fotos sin borrar | 🔴 Pendiente | `frames.clear()` no sobrescribe los bytes. |
| A-05 Lógica en el composable | 🔴 Pendiente | Sin `ActivationViewModel`; el repositorio se sigue creando en la navegación. |
| A-06 `catch (Exception)` y errores genéricos | 🔴 Pendiente | Atrapa la cancelación; todo código no exitoso se vuelve `ServiceUnavailable`; sin manejo de `401` (sesión vencida). |
| A-07 Textos en el código | 🔴 Pendiente | |
| A-08 `InstallationIdProvider` duplicado | 🔴 Pendiente | |
| A-09 Sin `FLAG_SECURE` | 🔴 Pendiente | |
| A-10 Contrato canónico | 🟢 Resuelto | Lee `captura.tipo` / `reto.gesto` y `resultado` / `intentos_restantes`, con muestras de contrato en `src/test/resources`. |
| A-11 `.gitignore` excluye las evidencias | 🔴 Pendiente de decisión | |

## 2. Hallazgos nuevos

**A-12 (alto) · El correo de demo se envía siempre.** `navigation/SolventaNavHost.kt` (ruta `activation`), `RemoteActivationRepository.kt`
- `demoEmail = session?.cliente?.correo` hace que **todas** las llamadas, en cualquier build, lleven `X-Demo-Correo` con el correo del cliente.
- El backend lo ignora fuera de `development`, pero es PII innecesaria en cada request.
- **Ajuste:** pasar `demoEmail` solo con un `BuildConfig` de demo (p. ej. `solventa.demoActivation=true`, solo en debug).

**A-13 (medio) · `metadata` se arma concatenando texto.** `RemoteActivationRepository.kt` (`verify`)
- El JSON se construye con interpolación de strings; un valor con comillas lo rompe.
- **Ajuste:** usar un DTO `@Serializable` (`VerificationMetadataDto` con `nonce` y `capturas`), como en `start`.

**A-14 (medio) · La marca de tiempo es la de llegada, no la de captura.** `ActivationScreen.kt` (`Instant.now()` en el callback de `TakePicturePreview`)
- Con la cámara del sistema, la hora es cuando vuelve el resultado.
- Las reglas del backend (0,3 s a 15 s entre `inicio` y `fin`, dentro de los 2 min de la sesión) van a rechazar con frecuencia una captura manual de 3 fotos.
- Esto se resuelve con T-M07 (CameraX), usando el timestamp del frame.

**A-15 (medio) · La única prueba nueva no cubre los cambios.** `RemoteActivationRepositoryTest`
- No verifica el header `Authorization`, la ausencia de `correo` en el cuerpo, ni el contenido de `metadata` (`nonce`, `capturas`).
- No hay pruebas de la redirección del login hacia la activación.

## 3. Avance por tarea

| Tarea | Estado |
|---|---|
| T-M01 Modelo y servicio simulado | 🟡 Sin `MockActivationRepository` |
| T-M02 Identificador de instalación | 🟢 |
| T-M03 Redirección | 🟡 Ver A-02 |
| T-M04 Consentimiento | 🔴 |
| T-M05 Patrón de lanzadores | 🔴 |
| T-M06 Guía con ML Kit | 🔴 |
| T-M07 Captura con CameraX | 🔴 |
| T-M08 ViewModel | 🔴 |
| T-M09 Pantallas | 🟡 Prototipo |
| T-M10 Integración con el BFF | 🟢 Casi listo (A-06, A-12, A-13) |
| T-M11 Textos y accesibilidad | 🔴 |
| T-M12 Pruebas y documentación | 🟡 |
| T-M13 Didit | 🔴 |

## 4. Siguientes pasos para el desarrollador (en orden)

1. **Arreglar la compilación** (`when (val result = …)`) y correr `testDebugUnitTest --rerun`.
2. A-06, A-12, A-13 y A-15: robustez del cliente HTTP, con pruebas.
3. A-05 + T-M01 + T-M08: `ActivationViewModel` y `MockActivationRepository`.
4. T-M05 → T-M07: lanzadores, CameraX y ML Kit (resuelve A-03, A-04, A-09 y A-14).
5. A-02 (resto), T-M04, A-07, A-08, T-M11 y T-M12.
6. T-M13 (Didit).

Recordatorio: los 6 commits están solo en local. No publicar hasta que Sergio lo indique, y nunca con la compilación rota.
