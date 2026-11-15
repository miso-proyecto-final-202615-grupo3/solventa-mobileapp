# HU4.1.1 · Mapa de commits por tarea

> **Commits creados el 14/11/2026.** HU4.1.1 y HU4.1.2 quedaron en la misma rama, `feat/HU4.1.1-registrar-siniestro` de cada repo, con un commit por tarjeta. El orden real y los commits extra (contrato real de Pólizas y renovación de sesión) están en [HU4.1.2-mapa-commits.md](HU4.1.2-mapa-commits.md). Esta lista queda como referencia de qué archivos van en cada tarjeta de HU4.1.1.

Cada archivo pertenece a un solo commit (no hace falta `git add -p`). Las modificaciones a pruebas existentes van en el commit que las rompe, para que cada commit quede en verde.

No incluir en estos commits los dos archivos que ya estaban en el *stage* de la app antes de empezar (`docs/diagramas/HU3.1.1/03-quien-valida-prueba-de-vida.png` y `.svg`): sácalos con `git restore --staged docs/diagramas/HU3.1.1` o súbelos aparte.

---

## Backend (`solventa-backend`)

### 0 · Planes técnicos
`docs(HU4.1.1-HU4.1.2): planes técnicos de registrar siniestro y evidencias`
```bash
git add docs/planning/HU4.1.1-plan-tecnico.md docs/planning/HU4.1.2-plan-tecnico.md
```

### T-B01 · Modelo de datos del siniestro asistido (tarjeta 1)
`feat(HU4.1.1): modelo del siniestro asistido y catálogo tipo_siniestro`
```bash
git add insuranceclaims_service/src/domain/siniestros/enums.py \
  insuranceclaims_service/src/domain/siniestros/catalogo.py \
  insuranceclaims_service/src/domain/siniestros/folio.py \
  insuranceclaims_service/src/domain/siniestros/errores.py \
  insuranceclaims_service/src/domain/siniestros/entities.py \
  insuranceclaims_service/src/domain/siniestros/repositories.py \
  insuranceclaims_service/src/infrastructure/persistence/models.py \
  insuranceclaims_service/src/infrastructure/persistence/siniestros_repository_impl.py \
  insuranceclaims_service/src/infrastructure/persistence/catalogo_tipos_siniestro_impl.py \
  insuranceclaims_service/src/infrastructure/persistence/seed_tipos_siniestro.py \
  scripts/sql/insuranceclaims_hu4_1_1.sql
```

### T-B02 · Integración de MS Siniestros con MS Pólizas (tarjeta 2)
`feat(HU4.1.1): puerto de pólizas con adaptador HTTP y en memoria`
```bash
git add insuranceclaims_service/src/application/ports/__init__.py \
  insuranceclaims_service/src/application/ports/polizas.py \
  insuranceclaims_service/src/infrastructure/gateways/__init__.py \
  insuranceclaims_service/src/infrastructure/gateways/http_polizas_client.py \
  insuranceclaims_service/src/infrastructure/gateways/polizas_en_memoria.py \
  insuranceclaims_service/src/infrastructure/gateways/polizas_factory.py \
  insuranceclaims_service/src/infrastructure/gateways/polizas_semilla.json \
  insuranceclaims_service/src/core/config.py \
  insuranceclaims_service/requirements.txt
```

### T-B03 · Endpoints de reporte y consulta en MS Siniestros (tarjeta 3)
`feat(HU4.1.1): casos de uso y API interna de siniestros`
```bash
git add insuranceclaims_service/src/application/use_cases/listar_polizas_elegibles_use_case.py \
  insuranceclaims_service/src/application/use_cases/reportar_siniestro_use_case.py \
  insuranceclaims_service/src/application/use_cases/consultar_siniestro_use_case.py \
  insuranceclaims_service/src/presentation/errors.py \
  insuranceclaims_service/src/presentation/dependencies.py \
  insuranceclaims_service/src/presentation/routers/siniestros.py \
  insuranceclaims_service/src/presentation/schemas/siniestros.py \
  insuranceclaims_service/src/main.py \
  insuranceclaims_service/tests/integration/test_health_api.py \
  insuranceclaims_service/tests/integration/test_lifespan.py
```

### T-B04 · Endpoints de siniestros en el BFF Móvil (tarjeta 4)
`feat(HU4.1.1): endpoints de siniestros en el BFF Móvil`
```bash
git add bff_mobile/src/core/config.py \
  bff_mobile/src/infrastructure/gateways/identity_service_gateway.py \
  bff_mobile/src/infrastructure/gateways/insuranceclaims_service_gateway.py \
  bff_mobile/src/presentation/cliente_resolver.py \
  bff_mobile/src/presentation/dependencies.py \
  bff_mobile/src/presentation/errors.py \
  bff_mobile/src/presentation/schemas.py \
  bff_mobile/src/presentation/routers/siniestros.py \
  bff_mobile/src/main.py
```

### T-B05 · Contrato y despliegue de siniestros (tarjeta 5)
`chore(HU4.1.1): contrato, compose, helm y documentación de siniestros`
```bash
git add docs/contracts/siniestros \
  docker-compose.yml .env.example \
  deployment/helm/config/insuranceclaims_service/values.yaml \
  deployment/helm/config/bff_mobile/values.yaml \
  README.md
```

### T-B06 · Tests unitarios y de integración Backend (tarjeta 6)
`test(HU4.1.1): pruebas de siniestros en MS Siniestros y BFF Móvil`
```bash
git add insuranceclaims_service/pytest.ini \
  insuranceclaims_service/tests/__init__.py \
  insuranceclaims_service/tests/fakes.py \
  insuranceclaims_service/tests/unit/test_catalogo.py \
  insuranceclaims_service/tests/unit/test_folio.py \
  insuranceclaims_service/tests/unit/test_polizas_adaptadores.py \
  insuranceclaims_service/tests/unit/test_reportar_siniestro.py \
  insuranceclaims_service/tests/integration/test_siniestros_api.py \
  insuranceclaims_service/tests/integration/test_sin_pii_en_logs.py \
  insuranceclaims_service/tests/integration/test_idempotencia_postgres.py \
  bff_mobile/tests/unit/test_cliente_resolver_y_claims_gateway.py \
  bff_mobile/tests/integration/test_siniestros_api.py \
  bff_mobile/tests/contract/test_siniestros_contract.py
```

---

## App (`solventa-mobileapp`)

### 0 · Planes y preparación del sprint
`docs(HU4.1.1-HU4.1.2): planes técnicos, tarjetas y preparación del sprint 3`
```bash
git add docs/planning/HU4.1.1-plan-tecnico.md docs/planning/HU4.1.2-plan-tecnico.md \
  docs/planning/Sprint3-tarjetas-HU4.1.1-HU4.1.2.md docs/planning/preparacion-reunion-sprint3.md \
  docs/planning/HU4.1.1-mapa-commits.md
```

### T-M01 · Repositorio de siniestros en la app (tarjeta 7)
`feat(HU4.1.1): repositorio de siniestros simulado y remoto`
```bash
git add app/build.gradle.kts \
  app/src/main/java/com/solventa/app/data/claims \
  app/src/main/java/com/solventa/app/di/AppContainer.kt
```

### T-M02 · Flujo "Reportar un siniestro" en la app (tarjeta 8)
`feat(HU4.1.1): flujo de reporte de siniestro en la app`
```bash
git add app/src/main/java/com/solventa/app/feature/claims/report \
  app/src/main/res/drawable/ic_check_circle.xml \
  app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml \
  app/src/main/java/com/solventa/app/navigation/SolventaNavHost.kt \
  app/src/main/java/com/solventa/app/feature/home/HomeScreen.kt
```

### T-M03 · Tests App (JUnit + Compose) y prueba manual (tarjeta 9)
`test(HU4.1.1): pruebas del reporte de siniestro y documentación`
```bash
git add app/src/test/java/com/solventa/app/data/claims \
  app/src/test/java/com/solventa/app/feature/claims \
  app/src/test/resources/contracts/siniestros \
  app/src/androidTest/java/com/solventa/app/feature/claims \
  README.md docs/contratos-pendientes.md
```

---

## Verificación hecha

| Qué | Resultado |
|---|---|
| `insuranceclaims_service` pytest (SQLite) | 75 en verde (+1 de Postgres que se salta sin `TEST_POSTGRES_URL`) |
| Idempotencia concurrente en Postgres (contenedor en la red de compose) | En verde: 10 reportes simultáneos con la misma clave → 1 siniestro |
| `bff_mobile` pytest | 97 en verde |
| Flujo real con `docker compose` (MS Siniestros + Postgres) | Pólizas elegibles, `201` y luego `200` con la misma clave (1 registro), `422 TIPO_NO_PERMITIDO`, catálogo en inglés, mismo `X-Correlation-Id` en logs |
| Script `insuranceclaims_hu4_1_1.sql` | Aplicado dos veces sin error (idempotente) |
| App `testDebugUnitTest` | 171 en verde (39 nuevas) |
| App `compileDebugAndroidTestKotlin` | Compila |

## Pendiente de verificar

- `ClaimReportScreenTest` (Compose) en un emulador o dispositivo: aquí solo se compiló.
- Flujo completo app → BFF → Siniestros con un token real de Cognito (requiere las credenciales de `.env`).
- Prueba manual en un teléfono: reporte completo y modo avión al enviar (reintento sin duplicado).
- DEP-1 (#265) y DEP-2 (estados de póliza) con JuanTapia911; `POLIZAS_ADAPTER=memoria` mientras tanto, también en helm.
