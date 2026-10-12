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
├── core/
│   ├── designsystem/    # Colores, tipografía, formas y componentes de marca
│   └── biometric/       # Bóveda Keystore, aviso biométrico, política de bloqueo, AppLockManager
├── data/                # Repositorios y modelos (contratos borrador con el BFF Móvil)
│   ├── auth/            # AuthRepository + MockAuthRepository
│   └── session/         # SessionStore
├── di/                  # AppContainer (inyección manual)
├── feature/             # Una carpeta por funcionalidad (pantalla + ViewModel)
│   ├── login/
│   ├── account/
│   ├── biometric/       # Oferta de activación, bloqueo de la app y Perfil > Seguridad
│   └── home/
└── navigation/          # SolventaNavHost y rutas
```

## Backend simulado

Mientras se confirman los contratos con el BFF Móvil (ver [docs/contratos-pendientes.md](docs/contratos-pendientes.md)), la app usa `MockAuthRepository`, controlado por `BuildConfig.USE_MOCK_BACKEND`.

Regla de demostración: cualquier correo con formato válido y una contraseña de **6 o más caracteres** inicia sesión.

| Correo | Contraseña | Resultado |
| --- | --- | --- |
| `camila.rojas@correo.com` | 6 o más caracteres | Inicio de sesión exitoso, teléfono vinculado: ofrece activar biometría |
| `andres.perez@correo.com` | 6 o más caracteres | Inicio de sesión exitoso, teléfono sin activar: no ofrece biometría |
| cualquier otro correo válido | 6 o más caracteres | Inicio de sesión exitoso, entra como cliente con teléfono vinculado |
| cualquiera de los anteriores | menos de 6 caracteres | Credenciales inválidas |
| `sin.cuenta@correo.com` | cualquiera | Cuenta no encontrada |
| `sin.red@correo.com` | cualquiera | Falla de red |
| correo con formato inválido | cualquiera | Cuenta no encontrada |

## Acceso biométrico (HU3.1.2)

- `BiometricPrompt` con autenticadores `BIOMETRIC_STRONG`. La app no guarda ni envía datos biométricos.
- La sesión se cifra con una llave AES de Android Keystore que exige autenticación biométrica y se invalida si cambian las huellas o rostros registrados (`setInvalidatedByBiometricEnrollment`). Desbloquear = descifrar la sesión.
- 3 intentos fallidos consecutivos bloquean la biometría 5 minutos; contador y bloqueo persisten aunque se cierre la app. Cancelar el aviso no cuenta como intento.
- Se pide biometría al abrir la app y al volver de segundo plano tras más de 60 s (`ProcessLifecycleOwner`).
- Activar o desactivar en cualquier momento desde Perfil y seguridad. Cerrar sesión **no** desactiva la biometría: el login ofrece "Entrar con huella o rostro". Solo se pierde al desactivarla en Perfil, borrar los datos de la app o desinstalarla.
- Código en `core/biometric/` y `feature/biometric/`.

Limitación conocida: al entrar con contraseña teniendo la biometría activa, la sesión cifrada no se renueva (renovarla exige otro aviso biométrico). Se resolverá cuando se defina con backend la estrategia de refresh token (ver pregunta 11 de [docs/contratos-pendientes.md](docs/contratos-pendientes.md)).

## Comandos

```bash
./gradlew testDebugUnitTest        # pruebas unitarias
./gradlew connectedDebugAndroidTest # pruebas Compose (requiere emulador o dispositivo)
./gradlew lintDebug
./gradlew assembleDebug
```
