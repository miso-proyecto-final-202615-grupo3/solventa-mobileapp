# HU3.2.1 y HU3.2.2 · Billetera de pólizas sin conexión

> **Estado (2026-11-18): implementado en local, sin commit ni PR.** Backend: rama `feat/HU3.2-polizas-bff-mobile` (worktree `solventa-backend-wt`, desde `origin/dev`). App: rama `feat/HU3.2-billetera-polizas-offline` (desde `dev`). Decisión D6: se cumple la HU; el desbloqueo biométrico sin red entra con la sesión guardada.

Historias: **#90 HU3.2.1** (consultar coberturas y certificado sin conexión, 8 pts) y **#91 HU3.2.2** (sincronizar al reconectar, 8 pts). Feature padre F3.2, Sprint 3. Borrador del 2026-11-18, basado en `origin/dev` de backend y app.

## 1. Estado de partida (verificado en el código)

**Backend**
- MS Pólizas (`insurancepolicies_service`) ya expone `GET /polizas/mis-polizas`, `GET /polizas/{id}`, `GET /polizas/{id}/certificado` (estado) y `GET /polizas/{id}/certificado/descarga` (PDF), con `X-Client-Id`. Los consume `bff_web`.
- **`bff_mobile` no tiene router ni gateway de pólizas.** Hoy solo tiene `auth`, `activacion`, `consentimientos`, `quotingengine`, `siniestros`, `evidencias`. Hay que crearlo (el patrón es `siniestros.py` + `get_cliente_id`).
- Respuesta de póliza: `poliza_id, cliente_id, cotizacion_id, producto_id, numero, estado, vigencia_desde, vigencia_hasta, emitida_en, ramo, prima_total, coberturas[{poliza_cobertura_id, cobertura_id, limite_asegurado, deducible, prima}], historial[]`.
- **Las coberturas de la póliza no traen nombre**, solo `cobertura_id`. Verificado: `GET /productos` del Motor de Cotización (ya consumido por `bff_mobile`) devuelve `producto_id`, `nombre` y `coberturas[{cobertura_id, codigo, nombre, limite_asegurado, deducible}]`, así que el nombre se resuelve en el BFF cruzando `producto_id` y `cobertura_id`, sin cambiar ese endpoint. Si una cobertura no aparece en el catálogo (producto inactivo), el BFF devuelve `nombre: null` y la app muestra el código.
- El certificado es un PDF generado por el servicio (`estado_certificado` PENDIENTE|GENERADO).

**App (`solventa-mobileapp`)**
- No existe feature de pólizas. `HomeScreen` es provisional y su comentario ya reserva la barra "Pólizas · Siniestros · Perfil" para HU3.2.x.
- Sin Room/DataStore/WorkManager. Ya hay: OkHttp + kotlinx.serialization, `AppContainer` manual, patrón `Repository` con `Mock*` y `Remote*` (`USE_MOCK_BACKEND`), `RetryPolicy` y cola con reintentos (`EvidenceUploadQueue`), llaves Keystore AES-GCM (`BiometricKeyManager`) y `PendingEvidenceStore` (archivo local).
- `SessionStore` es **solo memoria**. La sesión se recupera con el desbloqueo biométrico (vault cifrado local).
- Falta el permiso `ACCESS_NETWORK_STATE`.

## 1.1 Regla de compatibilidad (no cambiar lo ya construido)

- **Ningún endpoint existente cambia** (MS Pólizas, Motor de Cotización, `bff_web`, ni los de `bff_mobile`). Solo se agregan `GET /api/v1/bff-mobile/polizas` y `GET /api/v1/bff-mobile/polizas/{id}/certificado/descarga`.
- **Código existente: solo líneas de registro:** `include_router` en `bff_mobile/src/main.py`, proveedor del gateway en `dependencies.py`, URL del MS Pólizas en `core/config.py` y helm/compose; en la app, `AppContainer` (registro de dependencias), `SolventaNavHost` (rutas), `AndroidManifest` (`ACCESS_NETWORK_STATE`) y `strings.xml`.
- **No se modifica** `AppContainer.logout()` ni el comportamiento de `HomeScreen`: se agrega un botón "Mis pólizas" y el de siniestros queda igual. La barra "Pólizas · Siniestros · Perfil" queda fuera de esta entrega.

## 2. Decisiones propuestas (confirmar antes de codificar)

| # | Decisión | Propuesta | Alternativa |
|---|---|---|---|
| D1 | Almacén local | Archivo JSON cifrado con AES-GCM (Keystore) en `filesDir`, **sin Room** | Room + SQLCipher (más dependencias; innecesario para ≤ decenas de pólizas) |
| D2 | Llave | Llave Keystore **sin** exigir biometría por uso (distinta a la del vault); `allowBackup` excluido | Reusar la del vault: obliga a pedir biometría en cada lectura |
| D3 | "Credencial digital" | Datos de la póliza + **PDF del certificado** guardado cifrado (solo si `GENERADO`) | Solo datos, sin PDF |
| D4 | Detección de red | `ConnectivityManager.NetworkCallback` mientras la app vive; reintento con `RetryPolicy` | WorkManager (sincroniza con la app cerrada; dependencia nueva) |
| D5 | Qué pasa con el caché al cerrar sesión | **No se toca `logout()`**: el snapshot se guarda por `cliente_id` y se descarta al iniciar sesión otro cliente | Borrarlo al cerrar sesión (modifica `AppContainer.logout()`) |
| D6 | Acceso offline tras reinicio | Requiere desbloqueo biométrico (el login necesita red) | Permitir PIN local |

D6 es la restricción más fuerte: sin biometría activada y sin red, el cliente no puede entrar. Hay que decirlo explícitamente en los criterios o ofrecer la biometría antes.

## 3. Backend (BFF Móvil) — pequeño, pero bloqueante

1. Gateway `insurancepolicies_service_gateway.py` en `bff_mobile` (copiar el de `bff_web`).
2. Router `polizas.py` bajo `/api/v1/bff-mobile`, cliente tomado del token (`get_cliente_id`), nunca de un header:
   - `GET /polizas` → lista del cliente con coberturas **con nombre** (nombre desde el catálogo de productos).
   - `GET /polizas/{id}/certificado/descarga` → PDF (reutiliza la validación de `Content-Disposition` del BFF web).
3. Sin `actualizado_en`: la app reemplaza todo el snapshot con la versión del servidor (no se modifica MS Pólizas).
4. Contrato con errores `{codigo, mensaje, correlation_id}` como los demás endpoints móviles; muestras JSON en `docs/contracts/polizas/`.
5. El esquema móvil nuevo omite `historial` (solo afecta al router nuevo).
6. Tests de integración y de contrato como en HU4.1.1.

Coordinar con quien posee `bff_mobile` (el de HU3.1.2 / siniestros) para no pisar rutas.

## 4. App — diseño

Capas (MVVM + UDF, como el resto):

```
data/policies/
  PolicyModels.kt            Policy, Coverage, WalletSnapshot(policies, syncedAt)
  PolicyRepository.kt        interfaz: fetch(token) -> Result
  MockPolicyRepository.kt    demo sin backend
  RemotePolicyRepository.kt  GET /polizas + descarga PDF
  local/
    EncryptedWalletStore.kt  lee/escribe snapshot cifrado (JSON + AES-GCM, escritura atómica tmp+rename)
    CertificateStore.kt      PDFs cifrados por poliza_id
  sync/
    WalletSync.kt            orquesta: fetch -> reemplazar -> guardar; estado SyncState
    ConnectivityMonitor.kt   Flow<Boolean> sobre NetworkCallback
core/crypto/
  LocalCipher.kt             AES-GCM con llave Keystore (D2)
feature/policies/
  PolicyWalletScreen/ViewModel   lista + banner offline + "Última sincronización"
  PolicyDetailScreen/ViewModel   coberturas, vigencia, credencial/certificado
```

**Reglas de HU3.2.1 (#90)**
- Tras cada sincronización exitosa se escribe el snapshot cifrado (póliza, coberturas, vigencia, número, certificado).
- Sin red: "Mis pólizas" lee del almacén y **no muestra error de red**. Banner "Modo sin conexión · Última sincronización: dd/MM/yyyy HH:mm".
- Sin snapshot y sin red: estado vacío explicativo (no un error).
- Datos cifrados en reposo; sin copias en logs ni en `allowBackup`.

**Reglas de HU3.2.2 (#91)**
- `ConnectivityMonitor` emite `true` → `WalletSync.sync()` automático.
- Reemplazo por versión servidor (modificada, cancelada, renovada, y las que ya no vengan se eliminan del caché). Escritura atómica: si algo falla a mitad, queda el snapshot anterior intacto.
- `SyncState`: `Idle | Syncing | Failed(retryAt)`. "Sincronizando…" no bloquea la lista (se sigue mostrando el snapshot).
- Fallo: reintento con retroceso exponencial (`RetryPolicy`) mientras haya red; no se toca el snapshot; se sigue mostrando la última versión buena.
- Token vencido durante la sincronización: un intento de refresh y, si falla, quedarse con el snapshot y no reintentar en bucle.

**Navegación/Home:** `PolicyWalletScreen` se abre desde un botón nuevo "Mis pólizas" en `HomeScreen`; el botón de siniestros y el resto no cambian.

**DI:** `policyRepository`, `walletStore`, `walletSync` en `AppContainer` con el interruptor `USE_MOCK_BACKEND`. Permiso nuevo `ACCESS_NETWORK_STATE`.

## 5. Tarjetas sugeridas

Backend
1. Gateway y router de pólizas en BFF Móvil (lista con nombres de cobertura, descarga de certificado).
2. Contrato y muestras JSON `docs/contracts/polizas/` + tests de integración.

App
3. `LocalCipher` + `EncryptedWalletStore` + `CertificateStore` (tests JUnit, incluye datos corruptos y escritura atómica).
4. Modelos, `RemotePolicyRepository` y `MockPolicyRepository` (tests con MockWebServer y las muestras del contrato).
5. `ConnectivityMonitor` + `WalletSync` (reemplazo, fallo sin corromper, reintento, token vencido).
6. Pantallas billetera y detalle con banner offline y última sincronización.
7. Ruta de navegación y botón "Mis pólizas" en `HomeScreen` (sin tocar lo existente).
8. Pruebas de UI (Compose), prueba manual con modo avión y reporte.

Orden: 1–2 (backend) en paralelo con 3–4; 5 después; 6–7 al final.

## 6. Riesgos y preguntas abiertas

- **Backend/BFF Móvil sin pólizas:** bloquea el modo remoto; mientras tanto se avanza con `MockPolicyRepository` y las muestras del contrato.
- **Nombres de cobertura:** resuelto con el catálogo (ver sección 1); queda el caso de producto inactivo (`nombre: null`).
- **Pólizas de prueba:** HU4.1.1 anota que falta probar de punta a punta con una póliza emitida real.
- **Snapshot de otro cliente en el teléfono:** al no borrarlo en `logout()`, queda cifrado y atado a `cliente_id`; documentarlo como riesgo aceptado.
- **D6 (acceso offline sin biometría):** confirmar con el equipo / profesor si es aceptable.
- **Datos sensibles en el teléfono:** documentar la decisión (cifrado AES-GCM, llave no exportable, sin backup) para el criterio de "lineamientos de protección de datos".
- **Alcance de "certificado":** confirmar si basta mostrar los datos o se espera el PDF.
- **Fecha de cierre del Sprint 3:** no se obtuvo del tablero.
