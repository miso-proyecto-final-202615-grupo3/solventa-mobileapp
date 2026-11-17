# HU4.2.1 · Consultar el estado de un siniestro — app

Historia #84. Contrato canónico, decisiones (C1–C7) y tareas del backend: `solventa-backend/docs/planning/HU4.2.1-plan-tecnico.md`.
Muestras del contrato en `app/src/test/resources/contracts/siniestros/listado-response.json` y `…/evidencias/descarga-response.json`.

## Pantallas

| Pantalla | Ruta | Qué hace |
|---|---|---|
| **Mis siniestros** | `claims` (desde Inicio) | Lista paginada (20 por página, "Ver más"), más reciente primero. Filtro por póliza con chips ("Todas" + una por póliza). Cada fila: número, estado, tipo, producto · póliza y fecha de reporte. Estados vacío, error con "Reintentar" y sesión vencida → login. |
| **Detalle del siniestro** | `claims/{claimId}` | Número y estado, tipo, póliza, fechas, ubicación y evidencias como **miniaturas** (las fotos se ven; los videos con ícono de reproducir; las no cargadas, con su estado). Tocar una `CARGADA` la abre en el **visor de la app** a pantalla completa. "Agregar evidencia" solo si el siniestro está `REPORTADO` (lleva a la pantalla de HU4.1.2). |

## Clases

| Capa | Clase | Responsabilidad |
|---|---|---|
| data | `ClaimsRepository.claims(...)` → `ClaimsPageResult` | `GET /siniestros?poliza_id&pagina&tamano`; omite estados que la app no conoce. Remoto y simulado. |
| data | `EvidenceRepository.download(...)` → `DownloadResult` | `GET …/evidencias/{id}/descarga`; `409` → `NotAvailable`. Nunca registra ni guarda la URL. |
| feature | `MyClaimsViewModel` | Carga pólizas (filtro, nombres de producto y tipo) y la primera página; filtro, paginación sin duplicados, errores. |
| feature | `ClaimDetailViewModel` | Detalle; al cargar pide en paralelo la URL temporal de cada evidencia `CARGADA` (miniaturas); al abrir el visor la reutiliza o, si vence en menos de 30 s, pide otra. |
| feature | `MyClaimsScreen`, `ClaimDetailScreen`, `ClaimUi.kt` | UI, `StatusBadge` y formatos compartidos. |

## Decisiones de la app

- Las evidencias se ven **dentro de la app**: miniaturas y visor de fotos con **Coil 2.7** (carga la URL temporal de S3, con caché en memoria y disco de la propia librería) y videos con `VideoView` + `MediaController` de Android (sin dependencias nuevas). El archivo no se guarda en la galería ni la URL se registra.
- Las miniaturas de video no se generan: extraer un fotograma obligaría a descargar el video completo; se muestra un ícono de reproducir.
- Los nombres del producto y del tipo salen de las pólizas elegibles; si la póliza ya no está vigente, se muestra el código del tipo legible.
- La entrada "Mis siniestros" en Inicio es provisional, igual que "Reportar un siniestro", hasta la barra de la billetera (HU3.2.x).

## Pruebas

`MyClaimsViewModelTest`, `ClaimDetailViewModelTest`, `RemoteClaimsRepositoryTest` (listado) y `RemoteEvidenceRepositoryTest` (descarga) en JUnit; `MyClaimsScreenTest` y `ClaimDetailScreenTest` en Compose (emulador).
