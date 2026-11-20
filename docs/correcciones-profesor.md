# Correcciones posteriores a la devolución del profesor — 19/11/2026

Este documento complementa los informes de etapas 1 a 5. El informe [etapa-5.md](etapa-5.md) conserva la evidencia de la entrega anterior; los números y capturas de ese informe no describen por sí solos esta versión corregida.

## Pedido y resultado

| Devolución | Solución implementada | Dónde mostrarla |
|---|---|---|
| Cliente y artículo como *explorer* | Ventanas de selección con búsqueda por nombre/CUIT o código/descripción y botones Anterior/Siguiente. Cada página muestra hasta diez opciones. El cliente puede quedar como Consumidor Final. | `frontend/index.html`, `frontend/src/app.ts` |
| Seleccionar lista de precios | Selector explícito antes de elegir artículo. El explorador muestra solamente artículos de esa lista; al cambiarla, se limpia la selección anterior. Al editar un borrador se recupera la lista del primer renglón. | `GET /api/catalogos/listas-precio`, `GET /api/catalogos/lista-precio-articulos?listaPrecioId={id}`, `CatalogoService`, `app.ts` |
| Cliente y datos de factura | Los campos de cabecera quedaron reunidos en un bloque identificado como «Cliente y datos de factura». | `frontend/index.html` |
| Mostrar primero el día | Las fechas visibles de emisión y filtros usan **DD/MM/AAAA**: día, mes, año; se valida que la fecha exista. El PDF individual presenta emisión y vencimiento con ese orden. **El contrato JSON y los parámetros HTTP siguen en `yyyy-MM-dd`** (ISO), por ejemplo `19/11/2026` en la interfaz corresponde a `2026-11-19` en la API. | `frontend/src/app.ts`, `ComprobanteFacturaPdfService` |
| Imprimir factura con CAE, vencimiento y paginado | La acción «Imprimir» abre el PDF individual de una factura `EMITIDA` o `ANULADA`. Contiene número de comprobante, cliente, CUIT/CUIL, domicilio, condición IVA, renglones, importes, **CAE simulado**, fecha de vencimiento y pie `Página X de Y`. Una anulada se marca como tal. El borrador no tiene CAE y responde `409`. | `GET /api/facturas/{id}/pdf`, `ComprobanteFacturaPdfService` |
| Paginado | El listado de comprobantes y ambos exploradores usan páginas de hasta diez filas en el navegador. El PDF numera cada página con su total real. | `frontend/src/app.ts`, `ComprobanteFacturaPdfService` |

Los exploradores y el listado hacen **paginación local**: cargan las listas completas desde la API y luego buscan/cortan en el navegador. No se añadió un contrato HTTP paginado ni búsquedas de catálogos en el servidor.

## Contratos nuevos

| Solicitud | Respuesta y regla |
|---|---|
| `GET /api/catalogos/listas-precio` | `200`, opciones de lista con `id` y `etiqueta`. |
| `GET /api/catalogos/lista-precio-articulos?listaPrecioId=1` | `200`, opciones de los artículos asociados a esa lista. Cada opción incluye `id` del vínculo `ListaPrecioArticulo`, `etiqueta`, `valor` como precio y `listaPrecioId`. Sin filtro conserva las opciones de todas las listas; una lista inexistente devuelve `[]`. |
| `GET /api/facturas/{id}/pdf` | `200`, `application/pdf`, `Content-Disposition: inline`. Solo factura emitida o anulada con número y CAE; borrador `409`, ID inexistente `404`. |

El request de alta/edición **no** añade un campo `listaPrecioId`: envía `listaPrecioArticuloId` en cada detalle. El servidor consulta ese vínculo y obtiene el precio; no acepta un precio enviado por el navegador. Las fechas de filtros y de `FacturaRequestDTO` continúan en ISO.

El PDF individual y el reporte de facturas son recursos distintos: `/api/facturas/{id}/pdf` imprime **un comprobante** con CAE y vencimiento; `/api/facturas/reporte?formato=pdf` exporta **un listado** filtrado y descargable.

## Validación ejecutada

- Backend: `gradlew test` con perfil de pruebas H2: **87/87 pruebas aprobadas**, 18 suites, sin fallos. Las pruebas nuevas cubren el PDF con datos de factura, fecha día primero, CAE/vencimiento, varias páginas y reglas HTTP, además del catálogo de listas y del filtro de artículos.
- Frontend: `npm test`: **51/51 pruebas aprobadas** con TypeScript y jsdom. Se añadieron casos de fechas, exploradores, paginación, selección de lista y acción Imprimir.
- Se generaron muestras en [factura-demo.pdf](muestras/factura-demo.pdf) y [factura-demo-multipagina.pdf](muestras/factura-demo-multipagina.pdf); la segunda tiene tres páginas. Se revisaron sus páginas renderizadas y el texto extraído.

Estas pruebas no sustituyen un recorrido integral **backend + navegador real** de esta versión. La suite backend corrió sobre H2; no demuestra el comportamiento en PostgreSQL. Tampoco hay autorización fiscal real: el CAE es **simulado** y el PDF declara que el comprobante académico no tiene validez fiscal.

## Origen de la referencia

Para esta corrección se inspeccionó el archivo `TP 6 Grupal  3.zip` proporcionado como trabajo de referencia. Se tomó como guía el patrón de exploradores, selección de lista e impresión individual. No se copió su integración con ARCA ni se presenta el CAE de este proyecto como uno autorizado por el organismo. La base de este repositorio sigue siendo el kit de nivelación de la cátedra, como explica el [README](../README.md#origen-del-código-y-declaración-de-uso).
