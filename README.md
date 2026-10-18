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

## Backend

La app puede usar un backend simulado (por defecto) o el **BFF Móvil** real del monorepo `solventa-backend` (`POST /api/v1/bff-mobile/auth/login`). Se elige con propiedades de Gradle, sin tocar código; por ejemplo en `~/.gradle/gradle.properties`:

```properties
solventa.useMockBackend=false
solventa.bffMobileUrl=http://10.0.2.2:8002
```

- `10.0.2.2` es el PC visto desde el emulador; en un teléfono físico usa la IP del PC en la red local.
- En debug se permite HTTP sin cifrar solo hacia `10.0.2.2` y `localhost` (`src/debug/res/xml/network_security_config.xml`). Release exige HTTPS.

### APK de release (API Gateway)

En release la app consume el **API Gateway** de AWS (app → API Gateway → ALB del ingress → BFF Móvil), nunca el ALB
directamente: su DNS cambia en cada despliegue y no tiene HTTPS. La URL **no está en el código**: se pasa al compilar
con la propiedad `solventa.release.bffMobileUrl`. Si el API Gateway se recrea y cambia de URL, basta con volver a
compilar con la nueva; no hay que tocar código.

**1. Obtener la URL del API Gateway** (después de desplegar `solventa-infrastructure`):

```bash
# Salida de Terraform (output api_gateway_endpoint en src/environments/dev; hoy está comentado junto con el módulo)
terraform -chdir=src/environments/dev output -raw api_gateway_endpoint
# o directamente en AWS
aws apigatewayv2 get-apis --region us-east-1 --query "Items[].[Name,ApiEndpoint]" --output table
# Nombre del stage: si no es $default, la URL lleva el stage al final (https://…amazonaws.com/dev)
aws apigatewayv2 get-stages --api-id <api-id> --region us-east-1 --query "Items[].StageName"
```

**2. Comprobar que el API Gateway llega al BFF Móvil** antes de compilar. Con un usuario inexistente debe responder
`401 {"detail":"Credenciales inválidas."}`; un `404` indica una ruta o un stage mal configurados, y un `5xx`, que el
API Gateway no alcanza el ALB (clúster apagado o integración desactualizada):

```bash
curl -i -X POST "<URL>/api/v1/bff-mobile/auth/login" -H "Content-Type: application/json" \
  -d '{"correo":"no.existe@correo.com","password":"Clave1234"}'
```

**3. Poner la URL y compilar.** Cualquiera de estas opciones:

- En la línea de comandos (recomendado para un release puntual):
  ```bash
  ./gradlew assembleRelease -Psolventa.release.bffMobileUrl=https://<api-id>.execute-api.us-east-1.amazonaws.com
  ```
- O fija en `~/.gradle/gradle.properties` (no se versiona), para no repetirla:
  ```properties
  solventa.release.bffMobileUrl=https://<api-id>.execute-api.us-east-1.amazonaws.com
  ```

La URL va sin `/` final ni ruta: la app agrega `/api/v1/bff-mobile/auth/login`. El APK queda en
`app/build/outputs/apk/release/`. Para el APK de demostración con backend simulado (funciona sin AWS):
`./gradlew assembleRelease -Psolventa.useMockBackend=true`.

**4. Probar el APK** en un teléfono o emulador: con un usuario de Cognito debe entrar al inicio; con uno inexistente,
"El correo o la contraseña no coinciden"; con el clúster apagado, "No pudimos iniciar sesión en este momento".

Notas:

- El empaquetado falla si la URL no es `https://`, para no publicar un APK que no pueda conectarse (release no permite
  HTTP sin cifrar).
- Hoy el release sale sin firmar (`app-release-unsigned.apk`) y Android no lo instala así: falta definir la llave de
  firma antes de publicarlo en GitHub Releases.
- Para que la URL no cambie entre despliegues, el API Gateway debe quedar como recurso permanente en
  `solventa-infrastructure` (como Cognito) y el despliegue solo debe actualizar su integración con el DNS del ALB que
  crea el ingress (`kubectl get ingress solventa-ingress-alb -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'`
  y `aws apigatewayv2 update-integration … --integration-uri http://<dns-del-alb>`).
- Backend local: en `solventa-backend`, crear `.env` desde `.env.example` con tus credenciales de AWS y ejecutar `docker compose up --build`. La cuenta debe existir en Cognito (registro desde la web o el `bff_web`).
- Limitaciones actuales del BFF Móvil: solo entrega tokens y el correo. Nombre, estado KYC y vinculación del dispositivo llegarán cuando el MS Identidad exponga el cliente autenticado y exista HU3.1.1; mientras tanto la app asume el teléfono vinculado para poder ofrecer la biometría. "Cuenta no encontrada" no se distingue de "credenciales inválidas" (Cognito no lo revela).

### Backend simulado

`MockAuthRepository`, activo con `solventa.useMockBackend=true` (valor por defecto).

Regla de demostración: cualquier correo con formato válido y una contraseña de **6 o más caracteres** inicia sesión.

| Correo | Contraseña | Resultado |
| --- | --- | --- |
| `camila.rojas@correo.com` | 6 o más caracteres | Inicio de sesión exitoso, teléfono vinculado: ofrece activar biometría |
| `andres.perez@correo.com` | 6 o más caracteres | Inicio de sesión exitoso, teléfono sin activar: no ofrece biometría |
| cualquier otro correo válido | 6 o más caracteres | Inicio de sesión exitoso, entra como cliente con teléfono vinculado |
| cualquiera de los anteriores | menos de 6 caracteres | Credenciales inválidas |
| `sin.cuenta@correo.com` | cualquiera | Cuenta no encontrada |
| `sin.red@correo.com` | cualquiera | Falla de red |
| `sin.servicio@correo.com` | cualquiera | Servicio no disponible (el BFF responde pero no puede autenticar) |
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
