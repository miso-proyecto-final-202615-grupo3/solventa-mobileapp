# Sprint 3 · Tarjetas de HU4.1.1 y HU4.1.2

Texto listo para copiar a las historias (#81, #82) y a sus tarjetas. Decisiones aprobadas el 07/11/2026. El detalle técnico está en los planes:

| Historia | Backend | App |
|---|---|---|
| HU4.1.1 | `solventa-backend/docs/planning/HU4.1.1-plan-tecnico.md` | `docs/planning/HU4.1.1-plan-tecnico.md` |
| HU4.1.2 | `solventa-backend/docs/planning/HU4.1.2-plan-tecnico.md` | `docs/planning/HU4.1.2-plan-tecnico.md` |

---

## HU4.1.1 — Registrar un siniestro (#81)

### Plan para la historia (comentario en #81)

> **Plan técnico.** El cliente elige una de sus pólizas vigentes, indica qué pasó (tipo, descripción y fecha) y confirma. El BFF Móvil valida el token y resuelve el cliente; MS Siniestros valida contra MS Pólizas que la póliza sea suya y esté vigente, toma los tipos de siniestro de la tabla `tipo_siniestro` (única fuente del catálogo por producto) y crea el siniestro en estado `REPORTADO` con un folio `SIN-AAAA-XXXXXXXX`. El envío es idempotente (`Idempotency-Key`): reintentar tras una falla de red no duplica el siniestro. Los seguros de vida no se reportan por la app.
>
> **Patrones y tácticas:** puertos y adaptadores (MS Pólizas detrás de `PolizasPort`), tabla de referencia como única fuente del catálogo, BFF por canal como intermediario, operación idempotente + reintento, timeouts con fallo explícito, autorización por dueño con `404` uniforme, validación de entrada y trazabilidad con `X-Correlation-Id`. En la app: MVVM + UDF, repositorio simulado/remoto, resultados sellados y estado que sobrevive al proceso.
>
> **Dependencias:** endpoint interno de pólizas por cliente (tarea #265, HU1.3.8); mientras tanto, adaptador de pólizas en memoria.

### Tarjetas

1. **Modelo de datos del siniestro asistido**
   Ampliar `SINIESTRO` (cliente, folio, canal, descripción, póliza y producto) y crear la tabla `tipo_siniestro` con su carga inicial, única fuente de los tipos por producto.

2. **Integración de MS Siniestros con MS Pólizas**
   Consultar las pólizas del cliente detrás de un puerto, con un adaptador HTTP y uno en memoria mientras llega el endpoint de #265.

3. **Endpoints de reporte y consulta en MS Siniestros**
   Pólizas elegibles, reportar un siniestro con validaciones de vigencia e idempotencia, consultarlo y consultar los tipos de siniestro.

4. **Endpoints de siniestros en el BFF Móvil**
   Exponer a la app las pólizas elegibles, el reporte con `Idempotency-Key` y la consulta, resolviendo el cliente desde el token.

5. **Contrato y despliegue de siniestros**
   Muestras JSON del contrato, variables en `docker-compose` y helm, y pólizas ficticias para pruebas locales.

6. **Tests unitarios y de integración Backend (pytest)**
   Casos de uso, catálogo, cliente HTTP, API de Siniestros y BFF, contrato e idempotencia concurrente en Postgres.

7. **Repositorio de siniestros en la app**
   Modelos y repositorio simulado y remoto contra el BFF, con manejo de errores e idempotencia.

8. **Flujo "Reportar un siniestro" en la app**
   Pantallas Póliza · Qué pasó · Revisar · Confirmación con validaciones, folio del siniestro y entrada desde Inicio.

9. **Tests App (JUnit + Compose) y prueba manual**
   Pruebas del validador, ViewModel, repositorio, contrato y UI; reporte completo contra el backend local sin duplicados.

---

## HU4.1.2 — Adjuntar evidencia multimedia y geolocalización (#82)

### Plan para la historia (comentario en #82)

> **Plan técnico.** Las evidencias se adjuntan a un siniestro ya creado (HU4.1.1), una por una y de forma independiente, así una que falle no afecta al reporte ni a las demás. Por cada foto o video: la app pide una carga a MS Siniestros (vía BFF), que valida las reglas del tipo de siniestro (tabla `tipo_siniestro`) y entrega una URL prefirmada de S3 con condiciones (llave, tipo, tamaño, cifrado y vencimiento); la app sube el archivo directo a S3 y confirma; el backend verifica el objeto (tamaño, tipo y firma de bytes) y lo marca `CARGADA` o lo rechaza y borra. La ubicación (GPS o red, con precisión) se guarda en el siniestro y es opcional. En local se usa MinIO como S3.
>
> **Patrones y tácticas:** Valet Key (URL prefirmada) para no pasar archivos por los servicios, limitar el acceso con política firmada, cifrado en reposo (SSE-KMS) y en tránsito, doble validación de entrada, máquina de estados por evidencia, aislamiento de fallas por elemento, bloqueo de la fila para respetar límites, puertos y adaptadores para el almacenamiento. En la app: cámara y selector del sistema (mínimo privilegio), compresión en el origen, cola de envío con reintentos y retroceso exponencial, pendientes guardados, adaptador de ubicación.
>
> **Dependencias:** HU4.1.1; permisos S3/KMS del rol de MS Siniestros (infraestructura).

### Tarjetas

1. **Modelo de evidencias y ubicación del siniestro**
   Tabla de evidencias con sus estados, ubicación en el siniestro y reglas de tipo, tamaño y cantidad por tipo de siniestro.

2. **Almacenamiento de evidencias en S3 con URL prefirmada**
   Generar cargas firmadas con condiciones y cifrado KMS, y verificar el archivo subido; MinIO en local.

3. **Endpoints de evidencias y ubicación (MS Siniestros y BFF Móvil)**
   Solicitar carga, confirmar carga, registrar la ubicación y ver el siniestro con sus evidencias.

4. **Infraestructura de evidencias**
   MinIO en `docker-compose`, configuración en helm y permisos S3/KMS del rol de MS Siniestros con infraestructura.

5. **Tests unitarios y de integración Backend (pytest)**
   Estados, reglas, casos de uso, adaptador S3, API, contrato, límite concurrente en Postgres y flujo real con MinIO.

6. **Captura de fotos y videos con permisos en la app**
   Permiso de cámara, cámara y galería del sistema, compresión de fotos y validación de videos en almacenamiento privado.

7. **Geolocalización del siniestro en la app**
   Permiso de ubicación, obtener la posición con su precisión y adjuntarla al siniestro.

8. **Envío de evidencias con reintentos en la app**
   Subir cada evidencia directo a S3 y confirmarla, con reintentos, estado por evidencia y pendientes guardados.

9. **Pantalla "Adjuntar evidencia" en la app**
   Fotos, videos y ubicación con el estado de cada evidencia, opción de reintentar y aviso de pendientes en Inicio.

10. **Tests App y prueba en dispositivo real**
    Pruebas de la cola, el ViewModel, la subida y la UI; prueba en un teléfono real con permisos negados y modo avión.

---

## Capacidad y orden sugerido

- Estimación total de las dos historias: **≈ 39 h** (HU4.1.1 ≈ 17 h, HU4.1.2 ≈ 22 h; detalle por tarea en cada plan). La capacidad neta por desarrollador es de ≈ 10 h por semana (página de velocidad de la wiki): ≈ 23 h desde hoy (07/11) hasta el cierre (22/11). **El plan supera la capacidad entre 9 y 16 h.** Recortes posibles sin perder criterios de aceptación:
  1. Dirección legible: mostrar solo coordenadas y precisión (−0,5 h).
  2. Pendientes solo en memoria mientras la app esté abierta (−1 h).
  3. Prueba con MinIO real en pytest: dejarla manual (−0,5 h).
  4. Video solo con la cámara, sin selector (−0,5 h).
  5. Pedir apoyo del equipo en las tarjetas 4 y 5 de HU4.1.2.

| Fechas | Trabajo |
|---|---|
| 07/11 – 12/11 | HU4.1.1 backend (tarjetas 1–6) y repositorio de la app (7). Acordar con Juan el endpoint de #265 y con infraestructura los permisos S3/KMS. |
| 13/11 – 17/11 | HU4.1.1 app (8–9). HU4.1.2 backend (1–5). |
| 18/11 – 22/11 | HU4.1.2 app (6–10) y prueba en dispositivo real. Documentación y wiki. |

## Pendientes con el equipo

1. **JuanTapia911 (#265):** endpoint interno de pólizas por cliente con `producto_codigo`, y los valores reales de `estado`.
2. **Infraestructura (Danny):** permisos S3/KMS del rol de MS Siniestros (plan de backend de HU4.1.2, sección 7.1); idealmente una cuenta de servicio propia en lugar de `solventa-app-sa`.
3. **Danny:** aplicar en dev los scripts `ALTER` de `insuranceclaims`.
4. **asr88 (HU3.2.1):** barra inferior "Pólizas · Siniestros · Perfil" y no duplicar endpoints de pólizas en el BFF Móvil.
