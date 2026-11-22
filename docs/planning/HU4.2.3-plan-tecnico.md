# HU4.2.3 · Siniestro paramétrico automático — plan técnico (app)

Rama `feat/HU4.2.3-siniestro-parametrico` desde `dev`. Contrato canónico y supuestos (P1–P10): plan del backend
`solventa-backend/docs/planning/HU4.2.3-plan-tecnico.md`. Este archivo describe solo la app.

## Qué cambia para el cliente
- El siniestro paramétrico lo crea el backend; la app **no** lo reporta. Aparece solo en *Mis siniestros*.
- **Lista:** marca "Automático", tipo legible (`LLUVIA_EXTREMA` → "Lluvia extrema", `RETRASO_VUELO` → "Retraso de vuelo")
  y "Seguro paramétrico" cuando la póliza no está en el catálogo de reporte manual.
- **Detalle:** campo "Origen" (creado automáticamente; no hay que reportar ni enviar evidencias) y tarjeta "Compensación"
  con el monto y el estado del pago (`Pago en proceso` / `Pagada`). Sin sección de evidencias ni botón para agregarlas.
- **Notificación:** al crearse (estado `APROBADO`, `canal=PARAMETRICO`) el título es "Siniestro automático SIN-…" y el texto
  explica que la compensación fue aprobada. Los cambios siguientes (p. ej. `PAGADO`) usan la notificación de HU4.2.2.
  Tocarla abre el detalle (HU4.2.2).

## Contrato que consume la app (BFF Móvil)
- `GET /siniestros` y `GET /siniestros/{id}`: nuevo `canal` (`MOVIL|PARAMETRICO`, por defecto `MOVIL`).
- `GET /siniestros/{id}`: nuevo `indemnizacion` opcional `{ monto (texto decimal), moneda, estado }`.
- Push `SINIESTRO_ESTADO`: nuevo dato `canal`.
Valores desconocidos no rompen nada: canal desconocido = `MOVIL`, estado de pago desconocido = "En revisión",
monto ilegible = sin tarjeta de compensación.

## Supuestos de la app
| # | Supuesto |
|---|---|
| A1 | Los nombres de los tipos paramétricos viven en los recursos de la app (no están en el catálogo `tipo_siniestro`, que es del reporte manual). |
| A2 | El monto se muestra con `NumberFormat` del idioma del teléfono y sin decimales si es entero (COP). |
| A3 | No hay pantalla nueva: el detalle existente se adapta al canal. |

## Pruebas
- Unitarias: lectura del detalle paramétrico desde la muestra del contrato, canal/estado desconocidos, mensaje push
  paramétrico, formato del monto y nombres de tipos.
- Interfaz (compilan en CI): detalle con compensación y sin evidencias; marca "Automático" en la lista.
