# Contratos pendientes con backend (BFF Móvil)

La app ya puede autenticarse contra el BFF Móvil (`RemoteAuthRepository`); el backend simulado sigue disponible.
Estado de cada pregunta al 2026-10-31. El contrato canónico de HU3.1.1 está en la sección 3 de `solventa-backend/docs/planning/HU3.1.1-plan-tecnico.md`; su copia de consumo, en la sección 4 de [planning/HU3.1.1-plan-tecnico.md](planning/HU3.1.1-plan-tecnico.md).

## Inicio de sesión
1. ✅ Resuelta: la app inicia sesión contra el BFF Móvil, `POST /api/v1/bff-mobile/auth/login` con `{correo, password}`. Responde `access_token`, `refresh_token`, `expires_in`, `token_type` y `correo`. El BFF llama al MS Identidad, que autentica en Cognito.
2. ✅ Implementada en HU3.1.1: el login recibe `installation_id` opcional y devuelve el bloque opcional `cliente` (`id`, `nombre`, `estado_kyc`, `dispositivo_vinculado`), que el BFF obtiene de `GET /clientes/resumen-movil` del MS Identidad. Sin bloque `cliente`, la app asume **no vinculado** y va a la activación.
3. ✅ Resuelta: Cognito (`prevent_user_existence_errors`) y el MS Identidad responden 401 en ambos casos, así que con backend real se muestra el mensaje genérico de credenciales inválidas. Por eso el login muestra siempre el enlace al registro web.
4. ⏳ URL del registro web para el enlace de "Ir al registro web" (dev: CloudFront de la web app; confirmar la ruta exacta del registro).
4b. ✅ Renovar la sesión: `POST /api/v1/bff-mobile/auth/refresh` (Identidad `POST /clientes/renovar-sesion`); la app lo usa al desbloquear con biometría.
4c. ✅ Implementada en HU3.1.1: el BFF Móvil valida el access token de Cognito (firma, `iss`, `token_use`, `client_id` del mismo cliente que usa el MS Identidad) y obtiene el correo con `GetUser` de Cognito (el token solo trae el `sub`).

## Consentimiento biométrico (HU3.1.3)
5. ✅ Implementada en HU3.1.3: `GET` y `POST /api/v1/bff-mobile/consentimientos/biometrico` (contrato de la sección 4.3 del plan de HU3.1.1; muestras en [app/src/test/resources/contracts/consentimiento-biometrico](../app/src/test/resources/contracts/consentimiento-biometrico)). El MS Identidad guarda cada decisión, también el rechazo, con fecha, hora, versión del texto legal e instalación, y la activación exige el consentimiento (`REQUIRE_BIOMETRIC_CONSENT=true`). La app usa la versión remota por defecto; la simulada queda solo para demos sin backend.

## Prueba de vida (HU3.1.1)
6. ✅ No aplica a la app: la prueba de vida móvil no usa la cédula. Las fotos del documento son del KYC/AML web para adquirir pólizas (HU1.3.3).
7. ✅ Resuelta (diseño del experimento HA12): el backend elige el proveedor en el microservicio `liveness_adapter` (Strategy + Factory, proveedor activo por `PUT /admin/liveness-provider`). La app no conoce el proveedor; recibe solo el tipo de captura: `FRAMES` (cámara propia, 3 frames y reto del servidor; activo por defecto) o `SDK` (Didit, solo demostración y desactivado por defecto).
8. ✅ Resuelta: `POST /activacion/sesiones` y `POST /activacion/sesiones/{id}/verificacion` responden `resultado`, `folio`, `intento`, `intentos_restantes`, `dispositivo_vinculado`, `vinculado_en` y `motivo`. El backend controla el máximo: 3 intentos en 24 h; cuentan rechazados y en curso, los anulados por fallas técnicas no. Una sesión `FRAMES` nunca se reutiliza.

## Vinculación del dispositivo
9. ✅ Resuelta: la vinculación ocurre en la misma respuesta de la verificación aprobada. Hay un vínculo activo por cliente y por instalación: activar otra cuenta en el mismo teléfono revoca el vínculo anterior, y un teléfono nuevo revoca el anterior del cliente. Tabla aprobada: `dispositivos_vinculados` (más `sesiones_activacion` e `intentos_prueba_vida`); detalle y diagrama en `solventa-backend/docs/planning/HU3.1.1-diagramas.md`.

## Siniestros (HU4.1.1)
13. ✅ Implementada en la rama `feat/HU4.1.1-registrar-siniestro`: `GET /siniestros/polizas-elegibles`, `POST /siniestros` (`Idempotency-Key` obligatorio, `201` nuevo / `200` reintento) y `GET /siniestros/{id}`. Contrato canónico en la sección 3 de `solventa-backend/docs/planning/HU4.1.1-plan-tecnico.md`; muestras en [app/src/test/resources/contracts/siniestros](../app/src/test/resources/contracts/siniestros).
14. ✅ DEP-1 resuelta (13/11/2026): MS Pólizas expone `GET /polizas/mis-polizas` y `GET /polizas/{id}` con `X-Client-Id` (#265, HU1.3.8). MS Siniestros los consume (`POLIZAS_ADAPTER=http`) y toma el código y nombre del producto del catálogo del Motor de Cotización (`GET /productos`). Pendiente: probarlo de punta a punta con una póliza emitida.
15. ✅ DEP-2 resuelta: MS Pólizas deja la póliza en `EMITIDA`; son elegibles `EMITIDA` y `VIGENTE` (`ESTADOS_POLIZA_ELEGIBLES`) dentro de su vigencia.

## Evidencias y ubicación (HU4.1.2)
16. ✅ Implementada en la rama `feat/HU4.1.1-registrar-siniestro` (junto con HU4.1.1): `POST /siniestros/{id}/evidencias` (carga prefirmada), subida directa a S3/MinIO, `POST …/evidencias/{evidencia_id}/confirmacion`, `PUT /siniestros/{id}/ubicacion` y el detalle con `ubicacion` y `evidencias`. Contrato canónico en la sección 3 de `solventa-backend/docs/planning/HU4.1.2-plan-tecnico.md`; muestras en [app/src/test/resources/contracts/evidencias](../app/src/test/resources/contracts/evidencias).
17. ⏳ Permisos S3/KMS del rol de MS Siniestros en AWS (sección 7.1 del plan del backend); mientras tanto, solo funciona en local con MinIO.

## Transversal
10. ✅ Implementada en HU3.1.1 para los endpoints nuevos: `{codigo, mensaje, correlation_id}` y header `X-Correlation-Id` de punta a punta. Falta extenderlo al resto de endpoints y armar el catálogo de códigos.
11. ⏳ Duración de tokens y estrategia de refresh. Propuesta: refresh token cifrado con Android Keystore y desbloqueado con biometría (HU3.1.2).
12. 🟡 Parcial: dev y prod en AWS `us-east-1` detrás de un Load Balancer compartido, cada uno con su pool de Cognito. Las URLs están en la hoja de ambientes del equipo y van en `~/.gradle/gradle.properties`, no en el repo. El ALB es HTTP; para el build release falta un endpoint HTTPS.
