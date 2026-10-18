# Contratos pendientes con backend (BFF Móvil)

La app ya puede autenticarse contra el BFF Móvil (`RemoteAuthRepository`); el backend simulado sigue disponible.
Estado de cada pregunta al 2026-10-16.

## Inicio de sesión
1. ✅ Resuelta: la app inicia sesión contra el BFF Móvil, `POST /api/v1/bff-mobile/auth/login` con `{correo, password}`. Responde `access_token`, `refresh_token`, `expires_in`, `token_type` y `correo`. El BFF llama al MS Identidad, que autentica en Cognito.
2. ⏳ Pendiente: el MS Identidad no expone el cliente autenticado (id, nombre, `estado_kyc`), y `dispositivoVinculado` depende de HU3.1.1. Propuesta: `GET /clientes/me` en el MS Identidad a partir del token, consumido por el BFF Móvil en el login.
3. ✅ Resuelta: Cognito (`prevent_user_existence_errors`) y el MS Identidad responden 401 en ambos casos, así que con backend real se muestra el mensaje genérico de credenciales inválidas.
4. ⏳ URL del registro web para el enlace de "Ir al registro web".
4b. ⏳ Renovar la sesión: no hay endpoint de refresh en el MS Identidad ni en el BFF Móvil.
4c. ⏳ Validación del token: hoy ningún servicio valida el JWT de Cognito; el BFF Móvil deberá hacerlo antes de exponer endpoints con datos del cliente.

## Consentimiento (HU3.1.3)
5. ¿Es el mismo endpoint que usa la web en HU1.1.2 (`fuente_datos` / `proposito`)? ¿Qué valores corresponden a biométrico y ubicación, y de dónde sale la versión de términos?

## Cédula y prueba de vida (HU3.1.1)
6. ¿La cédula viaja en `multipart`, en base64 o a S3 con URL prefirmada?
7. ¿El SDK de liveness (Didit) corre en el dispositivo, o la app solo envía la selfie y el backend llama al proveedor?
8. Respuesta esperada: resultado, número de intento, intentos restantes, folio. ¿El máximo de 3 intentos lo controla el backend?

## Vinculación del dispositivo
9. ¿La vinculación ocurre en la misma respuesta de la prueba de vida o con un `POST /dispositivos` aparte? ¿Varios dispositivos por cliente? ¿Uno nuevo desvincula el anterior?

Propuesta de entidad para el esquema de Identidad (pendiente de aprobación):

```
DISPOSITIVO_VINCULADO
  dispositivo_id PK (uuid)
  cliente_id FK
  installation_id UK      -- UUID generado por la app (Android no permite usar IMEI)
  plataforma, modelo, version_so
  folio_verificacion      -- folio de la prueba de vida
  estado                  -- ACTIVO | REVOCADO
  vinculado_en, revocado_en, ultimo_acceso_en
```

## Transversal
10. Formato de error común (`codigo`, `mensaje`, `correlationId`) y catálogo de códigos de negocio.
11. Duración de tokens y estrategia de refresh. Propuesta: refresh token cifrado con Android Keystore y desbloqueado con biometría (HU3.1.2).
12. URL base de DEV/QA y fecha estimada de disponibilidad.
