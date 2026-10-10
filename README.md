# Solventa — App móvil Android

Cliente móvil del Sistema Solventa (MISW4501/MISW4502, Grupo 3). Canal de autoservicio del cliente asegurado: activación del teléfono, biometría, billetera de pólizas offline, siniestros y notificaciones.

Documentación de arquitectura y prototipos: [wiki del proyecto](https://github.com/dzamoranouniandes/misw4501-grupo3-solventa-backend/wiki).

## Stack

| Elemento | Decisión |
| --- | --- |
| Lenguaje | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3, tema del Manual de Marca) |
| Plataforma | `targetSdk` 34 (Android 14), `minSdk` 26, `compileSdk` 36 |
| Navegación | Navigation Compose |
| Pruebas | JUnit 4 + kotlinx-coroutines-test (unitarias), Compose Testing (instrumentadas) |
| i18n | Recursos nativos de Android (`values` = español, `values-en` = inglés) |
| CI | GitHub Actions (`.github/workflows/android-ci.yml`) |

## Estructura

```
app/src/main/java/com/solventa/app/
├── core/designsystem/   # Colores, tipografía, formas y componentes de marca
├── data/                # Repositorios y modelos (contratos borrador con el BFF Móvil)
│   ├── auth/            # AuthRepository + MockAuthRepository
│   └── session/         # SessionStore
├── di/                  # AppContainer (inyección manual)
├── feature/             # Una carpeta por funcionalidad (pantalla + ViewModel)
│   ├── login/
│   ├── account/
│   └── home/
└── navigation/          # SolventaNavHost y rutas
```

## Backend simulado

Mientras se confirman los contratos con el BFF Móvil (ver [docs/contratos-pendientes.md](docs/contratos-pendientes.md)), la app usa `MockAuthRepository`, controlado por `BuildConfig.USE_MOCK_BACKEND`.

| Correo | Contraseña | Resultado |
| --- | --- | --- |
| `camila.rojas@correo.com` | `Solventa2026` | Inicio de sesión exitoso |
| `camila.rojas@correo.com` | otra | Credenciales inválidas |
| `sin.red@correo.com` | cualquiera | Falla de red |
| cualquier otro | cualquiera | Cuenta no encontrada |

## Comandos

```bash
./gradlew testDebugUnitTest        # pruebas unitarias
./gradlew connectedDebugAndroidTest # pruebas Compose (requiere emulador o dispositivo)
./gradlew lintDebug
./gradlew assembleDebug
```
