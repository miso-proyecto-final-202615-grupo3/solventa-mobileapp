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
solventa.bffMobileUrl=http://10.0.2.2:8001
```

- `10.0.2.2` es el PC visto desde el emulador; en un teléfono físico usa la IP del PC en la red local.
- En debug se permite HTTP sin cifrar solo hacia `10.0.2.2` y `localhost` (`src/debug/res/xml/network_security_config.xml`). Release exige HTTPS.
- En un teléfono por USB, `solventa.bffMobileUrl=http://localhost:8001` junto con `adb reverse tcp:8001 tcp:8001` lleva el tráfico al BFF del PC sin depender de la red Wi-Fi.
- Backend local: en `solventa-backend`, crear `.env` desde `.env.example` con tus credenciales de AWS y ejecutar `docker compose up --build`. La cuenta debe existir en Cognito (registro desde la web o el `bff_web`).
- Limitaciones actuales del BFF Móvil: solo entrega tokens y el correo. Nombre, estado KYC y vinculación del dispositivo llegarán cuando el MS Identidad exponga el cliente autenticado y exista HU3.1.1; mientras tanto la app asume el teléfono vinculado para poder ofrecer la biometría. "Cuenta no encontrada" no se distingue de "credenciales inválidas" (Cognito no lo revela).

## Publicar el APK en GitHub Releases

Se publica el **APK de debug**: lo firma Gradle con la llave de debug, así que Android lo instala sin más
configuración. El build de release sale sin firmar (`app-release-unsigned.apk`) y no se puede instalar hasta definir
una llave de firma del equipo.

Hay dos tipos de APK; ninguno requiere cambiar código, solo propiedades de Gradle al compilar. Las propiedades pasadas
con `-P` tienen prioridad sobre `~/.gradle/gradle.properties`, así que una configuración local (por ejemplo, apuntando
a `localhost`) no se cuela en el APK publicado.

| APK | Backend | Cuándo |
| --- | --- | --- |
| Demostración | Simulado (`MockAuthRepository`), funciona sin AWS | **Release actual** (v0.1.0) y mientras no exista el API Gateway |
| AWS | BFF Móvil a través del API Gateway | Cuando el API Gateway exista, y otra vez cada vez que cambie su URL |

### A. APK de demostración (release actual)

```bash
./gradlew clean assembleDebug -Psolventa.useMockBackend=true
```

Verifica que tomó el backend simulado (`USE_MOCK_BACKEND = true`):

```bash
grep -E "USE_MOCK_BACKEND|BFF_MOBILE_URL" app/build/generated/source/buildConfig/debug/com/solventa/app/BuildConfig.java
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`; renómbralo, por ejemplo, `solventa-v0.1.0-demo.apk`.
Acceso de prueba: cualquier correo válido con contraseña de 6 o más caracteres (ver [Backend simulado](#backend-simulado)).

### B. APK contra AWS ( API Gateway cuando cambie su URL)

La app consume el **API Gateway** de AWS (app → API Gateway → ALB del ingress → BFF Móvil):
su DNS cambia en cada despliegue y no tiene HTTPS. La URL **no está en el código**: se pasa al compilar. Si el API
Gateway se recrea y cambia de URL, se repiten estos pasos con la nueva y se publica una versión nueva del APK.

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

**3. Compilar con la URL** (sin `/` final ni ruta: la app agrega `/api/v1/bff-mobile/auth/login`):

```bash
./gradlew clean assembleDebug -Psolventa.useMockBackend=false \
  -Psolventa.bffMobileUrl=https://<api-id>.execute-api.us-east-1.amazonaws.com
```

Verifica `USE_MOCK_BACKEND = false` y la URL en el `BuildConfig.java` (mismo `grep` del caso A) y renombra el APK,
por ejemplo, `solventa-v0.2.0-aws.apk`.

**4. Probar el APK** en un teléfono: con un usuario de Cognito debe entrar al inicio; con uno inexistente,
"El correo o la contraseña no coinciden"; con el clúster apagado, "No pudimos iniciar sesión en este momento".

Cuando el equipo defina la llave de firma, el mismo APK se genera como release (optimizado y sin depuración) con
`./gradlew assembleRelease -Psolventa.release.bffMobileUrl=https://…`; el empaquetado falla si la URL no es `https://`.

Para que la URL no cambie entre despliegues, el API Gateway debe quedar como recurso permanente en
`solventa-infrastructure` (como Cognito) y el despliegue solo debe actualizar su integración con el DNS del ALB que crea
el ingress (`kubectl get ingress solventa-ingress-alb -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'` y
`aws apigatewayv2 update-integration … --integration-uri http://<dns-del-alb>`).

### C. Publicar en GitHub

1. Fusionar el código del release en `main` y subir `versionCode` (siempre mayor que el anterior) y `versionName` en
   `app/build.gradle.kts`.
2. Generar el APK (caso A o B) e instalarlo en un teléfono con `adb install -r <apk>` para probarlo antes de publicar.
3. Crear y subir el tag:
   ```bash
   git checkout main && git pull
   git tag -a v0.1.0 -m "Solventa móvil 0.1.0"
   git push origin v0.1.0
   ```
4. En GitHub: **Releases → Draft a new release**, elegir el tag, título (`Solventa móvil v0.1.0`), notas (qué incluye,
   a qué backend apunta, cómo instalar y el acceso de prueba), adjuntar el APK y **Publish release** (marcar
   *pre-release* si no es la versión final).

Cada PC firma con una llave de debug distinta: si un teléfono tiene la app instalada desde otro PC, hay que
desinstalarla antes de instalar el APK nuevo.

### Backend simulado

`MockAuthRepository`, activo con `solventa.useMockBackend=true` (valor por defecto).

Regla de demostración: cualquier correo con formato válido y una contraseña de **6 o más caracteres** inicia sesión.

| Correo | Contraseña | Resultado |
| --- | --- | --- |
| `camila.rojas@correo.com` | 6 o más caracteres | Inicio de sesión exitoso, teléfono vinculado: ofrece activar biometría |
| `andres.perez@correo.com` | 6 o más caracteres | Teléfono sin activar: consentimiento → prueba de vida aprobada → oferta biométrica |
| `rechazo.vida@correo.com` | 6 o más caracteres | Teléfono sin activar: la prueba de vida se rechaza; al tercer intento se agotan |
| `sin.consentimiento@correo.com` | 6 o más caracteres | Teléfono sin activar: el backend simulado exige el consentimiento biométrico |
| `sin.servicio@correo.com` | cualquiera | Servicio no disponible (también en el consentimiento simulado) |
| cualquier otro correo válido | 6 o más caracteres | Inicio de sesión exitoso, entra como cliente con teléfono vinculado |
| cualquiera de los anteriores | menos de 6 caracteres | Credenciales inválidas |
| `sin.cuenta@correo.com` | cualquiera | Cuenta no encontrada |
| `sin.red@correo.com` | cualquiera | Falla de red |
| correo con formato inválido | cualquiera | Cuenta no encontrada |

## Activación con prueba de vida (HU3.1.1)

Un teléfono sin vincular no puede saltarse la activación: login → consentimiento biométrico → prueba de vida →
teléfono vinculado → oferta de huella o rostro (HU3.1.2). Diseño completo en
[docs/planning/HU3.1.1-plan-tecnico.md](docs/planning/HU3.1.1-plan-tecnico.md) y diagramas en
[docs/planning/HU3.1.1-diagramas.md](docs/planning/HU3.1.1-diagramas.md).

- **Proveedor:** lo decide el `liveness_adapter` del backend (patrón del experimento HA12). La app solo recibe
  el tipo de captura: `FRAMES` (propia, por defecto) o `SDK` (Didit, solo demostración).
- **Captura propia:** CameraX + ML Kit (`CaptureGuideEngine`) guían el gesto que pide el servidor y toman 3 fotos
  (inicio, gesto, fin) solo en memoria; se sobrescriben con ceros al terminar y la pantalla usa `FLAG_SECURE`.
- **Strategy + Factory:** `CaptureLauncherFactory` elige la estrategia (`OwnCaptureLauncher`,
  `SimulatedDiditCaptureLauncher` o `SdkDiditCaptureLauncher`) según la sesión.
- **Consentimiento:** simulado hasta que HU3.1.3 entregue su endpoint (excepción transitoria; responsable asr88).

Propiedades de Gradle (en `~/.gradle/gradle.properties` o con `-P`):

| Propiedad | Por defecto | Uso |
| --- | --- | --- |
| `solventa.useMockConsent` | `true` | Consentimiento simulado; `false` usa el endpoint de HU3.1.3 |
| `solventa.mockCaptureType` | `FRAMES` | Con el backend simulado, `SDK` simula Didit |
| `solventa.diditSdkEnabled` | `false` | SDK real de Didit (demo, costo cero); si no, lanzador simulado |
| `solventa.demoActivationEmail` | vacío | Solo debug: correo para el modo demo del BFF sin JWT (`X-Demo-Correo`) |

El SDK de Didit (`me.didit:didit-sdk-core`) se descarga de su repositorio Maven, restringido al grupo `me.didit`
en `settings.gradle.kts`. Aumenta el APK de debug a ~67 MB junto con CameraX y ML Kit.

Pendiente de calibrar en un dispositivo real: el signo de `headEulerY` para "girar a la izquierda"
(`GuideConfig.turnLeftSign`) frente al que usa MediaPipe en el backend.

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
