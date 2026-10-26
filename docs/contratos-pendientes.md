# Contratos pendientes con backend (BFF Móvil)

La app ya puede autenticarse contra el BFF Móvil (`RemoteAuthRepository`); el backend simulado sigue disponible.
Estado de cada pregunta al 2026-10-24. El contrato canónico de HU3.1.1 está en la sección 3 de `solventa-backend/docs/planning/HU3.1.1-plan-tecnico.md`; su copia de consumo, en la sección 4 de [planning/HU3.1.1-plan-tecnico.md](planning/HU3.1.1-plan-tecnico.md).

## Inicio de sesión
1. ✅ Resuelta: la app inicia sesión contra el BFF Móvil, `POST /api/v1/bff-mobile/auth/login` con `{correo, password}`. Responde `access_token`, `refresh_token`, `expires_in`, `token_type` y `correo`. El BFF llama al MS Identidad, que autentica en Cognito.
2. 📝 Aprobada en HU3.1.1, por implementar: el login recibe `installation_id` opcional y devuelve el bloque opcional `cliente` (`id`, `nombre`, `estado_kyc`, `dispositivo_vinculado`), que el BFF obtiene de `GET /clientes/resumen-movil` del MS Identidad. Sin bloque `cliente`, la app asume **no vinculado** y va a la activación.
3. ✅ Resuelta: Cognito (`prevent_user_existence_errors`) y el MS Identidad responden 401 en ambos casos, así que con backend real se muestra el mensaje genérico de credenciales inválidas. Por eso el login muestra siempre el enlace al registro web.
4. ⏳ URL del registro web para el enlace de "Ir al registro web" (dev: CloudFront de la web app; confirmar la ruta exacta del registro).
4b. ⏳ Renovar la sesión: no hay endpoint de refresh en el MS Identidad ni en el BFF Móvil.
4c. 📝 Aprobada en HU3.1.1, por implementar: el BFF Móvil valida el access token de Cognito (firma, `iss`, `token_use`, `client_id` del mismo cliente que usa el MS Identidad) y toma el correo del claim `username`.

## Consentimiento biométrico (HU3.1.3)
5. ⏳ Pendiente de HU3.1.3 (responsable: asr88). Contrato definido en la sección 4.3 del plan de HU3.1.1: `GET` y `POST /api/v1/bff-mobile/consentimientos/biometrico`. Hasta que se entregue, la app simula las respuestas (`solventa.useMockConsent=true`) y el backend no exige el consentimiento (`REQUIRE_BIOMETRIC_CONSENT=false`). Es una **excepción transitoria**: al entregarse el endpoint, se conecta la versión remota y se activa la variable en QA y producción.

## Prueba de vida (HU3.1.1)
6. ✅ No aplica a la app: la prueba de vida móvil no usa la cédula. Las fotos del documento son del KYC/AML web para adquirir pólizas (HU1.3.3).
7. ✅ Resuelta (diseño del experimento HA12): el backend elige el proveedor en el microservicio `liveness_adapter` (Strategy + Factory, proveedor activo por `PUT /admin/liveness-provider`). La app no conoce el proveedor; recibe solo el tipo de captura: `FRAMES` (cámara propia, 3 frames y reto del servidor; activo por defecto) o `SDK` (Didit, solo demostración y desactivado por defecto).
8. ✅ Resuelta: `POST /activacion/sesiones` y `POST /activacion/sesiones/{id}/verificacion` responden `resultado`, `folio`, `intento`, `intentos_restantes`, `dispositivo_vinculado`, `vinculado_en` y `motivo`. El backend controla el máximo: 3 intentos en 24 h; cuentan rechazados y en curso, los anulados por fallas técnicas no. Una sesión `FRAMES` nunca se reutiliza.

## Vinculación del dispositivo
9. ✅ Resuelta: la vinculación ocurre en la misma respuesta de la verificación aprobada. Hay un vínculo activo por cliente y por instalación: activar otra cuenta en el mismo teléfono revoca el vínculo anterior, y un teléfono nuevo revoca el anterior del cliente. Tabla aprobada: `dispositivos_vinculados` (más `sesiones_activacion` e `intentos_prueba_vida`); detalle y diagrama en `solventa-backend/docs/planning/HU3.1.1-diagramas.md`.

## Transversal
10. 📝 Aprobada en HU3.1.1 para los endpoints nuevos: `{codigo, mensaje, correlation_id}` y header `X-Correlation-Id` de punta a punta. Falta extenderlo al resto de endpoints y armar el catálogo de códigos.
11. ⏳ Duración de tokens y estrategia de refresh. Propuesta: refresh token cifrado con Android Keystore y desbloqueado con biometría (HU3.1.2).
12. 🟡 Parcial: dev y prod en AWS `us-east-1` detrás de un Load Balancer compartido, cada uno con su pool de Cognito. Las URLs están en la hoja de ambientes del equipo y van en `~/.gradle/gradle.properties`, no en el repo. El ALB es HTTP; para el build release falta un endpoint HTTPS.
