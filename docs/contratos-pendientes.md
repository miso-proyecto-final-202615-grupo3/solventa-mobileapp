# Contratos pendientes con backend (BFF Móvil)

La app usa hoy un backend simulado (`MockAuthRepository`, `BuildConfig.USE_MOCK_BACKEND = true`).
Estas preguntas deben confirmarse con el equipo antes de implementar la integración real.

## Inicio de sesión
1. ¿El móvil inicia sesión contra el BFF Móvil (`POST /v1/mobile/auth/login`) o directo contra Cognito? Propuesta: BFF devuelve `accessToken`, `refreshToken`, `expiresIn`.
2. ¿La respuesta incluye `estadoKyc` y `dispositivoVinculado` para decidir si se va al onboarding o directo a la app?
3. "No encontramos tu cuenta" revela qué correos existen (enumeración de usuarios). ¿Se mantiene o se usa un mensaje genérico?
4. URL del registro web para el enlace de "Ir al registro web".

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
