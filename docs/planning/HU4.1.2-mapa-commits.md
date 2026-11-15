# HU4.1.2 · Mapa de commits por tarea (y cómo separarla de HU4.1.1)

## Commits creados (14/11/2026)

Las dos historias quedaron en la misma rama, `feat/HU4.1.1-registrar-siniestro` de cada repo, con un commit por tarjeta. Se armaron sobre la versión de HU4.1.1 sola del 08/11 (`_respaldos/HU4.1.1-*.patch`), y luego se agregaron los cambios posteriores, así que cada commit contiene solo lo de su tarjeta. Al terminar, el árbol quedó idéntico al que se probó.

**Backend**, en orden:
1. `docs(HU4.1.1-HU4.1.2)`: planes técnicos.
2. Los 6 commits de HU4.1.1 (T-B01 a T-B06 de [HU4.1.1-mapa-commits.md](HU4.1.1-mapa-commits.md)).
3. `feat(HU4.1.1): consume el contrato real de MS Pólizas…`: cambios posteriores al 13/11 (`dev` 70dc8b3), con póliza por cliente, catálogo de productos y `EMITIDA`.
4. `feat(auth): renovación de sesión con refresh token…`: Identidad y BFF; no tiene tarjeta.
5. Los 5 commits de HU4.1.2 (T-B01 a T-B05, abajo).

**App**, en orden:
1. `docs(HU4.1.1-HU4.1.2)`: planes, tarjetas y preparación del sprint.
2. Los 3 commits de HU4.1.1 (T-M01 a T-M03).
3. `feat(auth): renueva la sesión con refresh token al desbloquear con biometría`, que no tiene tarjeta.
4. Los 5 commits de HU4.1.2 (T-M01 a T-M05, abajo).

Las listas de archivos de abajo son las que se usaron.

---

## Backend (`solventa-backend`) · 51 archivos

### T-B01 · Modelo de evidencias y ubicación del siniestro (tarjeta 1)
`feat(HU4.1.2): modelo de evidencias, ubicación y política de evidencias`
```bash
git add insuranceclaims_service/src/domain/siniestros/evidencias.py \
  insuranceclaims_service/src/domain/siniestros/ubicacion.py \
  insuranceclaims_service/src/domain/siniestros/politica_evidencias.py \
  insuranceclaims_service/src/domain/siniestros/entities.py \
  insuranceclaims_service/src/domain/siniestros/errores.py \
  insuranceclaims_service/src/domain/siniestros/repositories.py \
  insuranceclaims_service/src/infrastructure/persistence/models.py \
  insuranceclaims_service/src/infrastructure/persistence/siniestros_repository_impl.py \
  scripts/sql/insuranceclaims_hu4_1_2.sql
```

### T-B02 · Almacenamiento de evidencias en S3 con URL prefirmada (tarjeta 2)
`feat(HU4.1.2): almacenamiento de evidencias en S3 con POST prefirmado y SSE`
```bash
git add insuranceclaims_service/src/application/ports/almacen_evidencias.py \
  insuranceclaims_service/src/infrastructure/storage \
  insuranceclaims_service/src/core/config.py \
  insuranceclaims_service/requirements.txt
```

### T-B03 · Endpoints de evidencias y ubicación en MS Siniestros y BFF Móvil (tarjeta 3)
`feat(HU4.1.2): API de evidencias y ubicación en MS Siniestros y BFF Móvil`
```bash
git add insuranceclaims_service/src/application/use_cases/evidencias_use_cases.py \
  insuranceclaims_service/src/presentation/dependencies.py \
  insuranceclaims_service/src/presentation/routers/evidencias.py \
  insuranceclaims_service/src/presentation/routers/siniestros.py \
  insuranceclaims_service/src/presentation/schemas/evidencias.py \
  insuranceclaims_service/src/main.py \
  insuranceclaims_service/tests/integration/test_health_api.py \
  insuranceclaims_service/tests/integration/test_siniestros_api.py \
  bff_mobile/src/infrastructure/gateways/insuranceclaims_service_gateway.py \
  bff_mobile/src/presentation/routers/evidencias.py \
  bff_mobile/src/presentation/routers/siniestros.py \
  bff_mobile/src/presentation/schemas.py \
  bff_mobile/src/main.py
```

### T-B04 · Infraestructura de evidencias (tarjeta 4)
`chore(HU4.1.2): MinIO local, helm, contrato y documentación de evidencias`
```bash
git add docker-compose.yml .env.example \
  deployment/helm/config/insuranceclaims_service \
  docs/contracts/evidencias \
  README.md
```

### T-B05 · Tests unitarios y de integración Backend (tarjeta 5)
`test(HU4.1.2): pruebas de evidencias, S3/MinIO y límites concurrentes`
```bash
git add insuranceclaims_service/pytest.ini \
  insuranceclaims_service/tests/unit/test_evidencias_dominio.py \
  insuranceclaims_service/tests/unit/test_evidencias_use_cases.py \
  insuranceclaims_service/tests/unit/test_s3_almacen.py \
  insuranceclaims_service/tests/integration/test_evidencias_api.py \
  insuranceclaims_service/tests/integration/test_evidencias_infra.py \
  bff_mobile/tests/contract/test_evidencias_contract.py \
  bff_mobile/tests/integration/test_evidencias_api.py
```

---

## App (`solventa-mobileapp`) · 45 archivos + este mapa

### T-M01 · Captura de fotos y videos con permisos (tarjeta 6)
`feat(HU4.1.2): permisos, captura y compresión de evidencias en almacenamiento privado`
```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/AndroidManifest.xml \
  app/src/main/res/xml/evidence_paths.xml \
  app/src/main/java/com/solventa/app/core/media \
  app/src/main/java/com/solventa/app/core/permissions
```

### T-M02 · Geolocalización del siniestro (tarjeta 7)
`feat(HU4.1.2): ubicación del siniestro con Fused Location Provider`
```bash
git add app/src/main/java/com/solventa/app/core/location
```

### T-M03 · Envío de evidencias con reintentos (tarjeta 8)
`feat(HU4.1.2): subida directa a S3 y cola de evidencias con reintentos`
```bash
git add app/src/main/java/com/solventa/app/data/claims/evidence \
  app/src/main/java/com/solventa/app/data/claims/ClaimsModels.kt \
  app/src/main/java/com/solventa/app/data/claims/RemoteClaimsRepository.kt \
  app/src/main/java/com/solventa/app/data/claims/MockClaimsRepository.kt \
  app/src/main/java/com/solventa/app/di/AppContainer.kt \
  app/src/main/java/com/solventa/app/SolventaApplication.kt
```

### T-M04 · Pantalla "Adjuntar evidencia" (tarjeta 9)
`feat(HU4.1.2): pantalla de evidencia, navegación y aviso de pendientes`
```bash
git add app/src/main/java/com/solventa/app/feature/claims/evidence \
  app/src/main/java/com/solventa/app/navigation/SolventaNavHost.kt \
  app/src/main/java/com/solventa/app/feature/home/HomeScreen.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
```

### T-M05 · Tests App y documentación (tarjeta 10)
`test(HU4.1.2): pruebas de evidencias, cola, subida y pantalla`
```bash
git add app/src/test/java/com/solventa/app/core/media \
  app/src/test/java/com/solventa/app/data/claims/evidence \
  app/src/test/java/com/solventa/app/feature/claims/evidence \
  app/src/test/resources/contracts/evidencias \
  app/src/androidTest/java/com/solventa/app/feature/claims/evidence \
  README.md docs/contratos-pendientes.md \
  docs/planning/HU4.1.2-mapa-commits.md docs/planning/HU4.1.1-mapa-commits.md
```

(`HU4.1.1-mapa-commits.md` aparece aquí porque se le agregó la nota que remite a este procedimiento.)

---

## Verificación hecha

| Qué | Resultado |
|---|---|
| `insuranceclaims_service` pytest | 144 en verde; 6 de infraestructura se saltan sin variables |
| Las mismas, contra Postgres y MinIO reales (contenedor en la red de compose) | 148 en verde, ninguna saltada: límite concurrente (8 solicitudes → 5 fotos), MinIO acepta la subida firmada y rechaza tamaño y `Content-Type` distintos, confirmación completa |
| `bff_mobile` pytest | 114 en verde |
| Flujo de punta a punta con `docker compose` (Siniestros + Postgres + MinIO) | Foto y video `204` + `200`; segundo video `409 LIMITE_EVIDENCIAS`; foto falsa `422 EVIDENCIA_RECHAZADA` y borrada; ubicación `200`; detalle con los tres estados; 11 líneas con el mismo `X-Correlation-Id`; sin URL firmadas ni coordenadas en los logs |
| Script `insuranceclaims_hu4_1_2.sql` | Aplicado dos veces sin error |
| App `testDebugUnitTest` | 209 en verde (38 nuevas) |
| App `compileDebugAndroidTestKotlin` y `assembleDebug` | Compilan |

Errores encontrados y corregidos en la prueba de punta a punta: el `.env` local trae `AWS_PROFILE` y en el contenedor ese perfil no existe (boto3 fallaba con 500). Ahora compose vacía el perfil para Siniestros, un perfil vacío se trata como "sin perfil" y cualquier error al crear el cliente S3 responde `503` en vez de `500`.

### Defectos del envío corregidos (14/11/2026, tarjeta #310)

Van en los mismos commits de su tarea (T-B03/T-B05 en el backend, T-M03/T-M04/T-M05 en la app); no requieren commit aparte.

| Defecto | Corrección | Pruebas |
|---|---|---|
| La carga firmada solo vive en memoria: tras cerrar la app, perder la respuesta o vencer la sesión, la nueva solicitud creaba otra evidencia `PENDIENTE` que gastaba cupo (terminaba en `LIMITE_EVIDENCIAS`) | `Idempotency-Key = localId` en `POST …/evidencias`; el backend renueva la misma evidencia (`200`), o responde `CARGADA` con `carga: null`. Columna `clave_idempotencia` (script `insuranceclaims_hu4_1_2.sql`) | backend: 4 de caso de uso, 2 de API de Siniestros, 2 del BFF · app: repositorio remoto y 2 de la cola |
| `resume()` no reanudaba lo detenido por `SESSION_EXPIRED` | `resume()` también reanuda `FAILED(SESSION_EXPIRED)` al iniciar sesión o renovar el token | `la sesion vencida se reanuda sola…` |
| Si Android cerraba la app con la cámara abierta se perdía la foto | `captureFile` en `SavedStateHandle`; la foto espera a que termine de cargar el siniestro | `la foto sobrevive si Android cierra la app…` |

Verificación: Siniestros y BFF pytest sin fallos (en contenedor); app `testDebugUnitTest` 214 en verde.

## Pendiente de verificar

- En una base ya creada (local o dev), aplicar de nuevo `insuranceclaims_hu4_1_2.sql`: `create_all` no agrega la columna `clave_idempotencia` a una tabla existente.

- La app en un **dispositivo real**: cámara, galería, video de 10 s, video de más de 60 s, permisos negados y "no volver a preguntar", ubicación en exteriores e interiores, **modo avión durante la subida** y cerrar la app con pendientes.
- `ClaimEvidenceScreenTest` (Compose) en un emulador: aquí solo se compiló.
- Flujo app → BFF con un token real de Cognito.
- AWS: permisos S3/KMS del rol de Siniestros (plan del backend §7.1) y que el pod tome las credenciales por Pod Identity.
