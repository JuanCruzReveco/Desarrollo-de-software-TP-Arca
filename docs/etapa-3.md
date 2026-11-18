# Etapa 3 — API REST de lectura

**Origen del código:** kit de la cátedra. **Aporte propio de esta etapa:** verificación contra la API en ejecución y este informe. **Entorno:** Windows 11, JDK 17.0.21, H2, backend en el puerto 8080. Se usó un asistente de IA (ver README).

## Decisiones y alternativas

- **Capas:** el controlador maneja HTTP y DTO; `FacturaService` (`@Transactional(readOnly = true)`) coordina y convierte; el repositorio consulta. **Las entidades nunca se devuelven**: `GET /api/facturas` responde `FacturaReporteDTO` y el detalle `FacturaDetalladaDTO`. Un DTO evita serializar asociaciones `LAZY` (que con `open-in-view: false` fallarían) y permite controlar exactamente qué campos salen.
- **Filtros opcionales** (`fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`) con límites inclusivos. El estado se normaliza (recorta espacios y pasa a mayúsculas). No se impuso un mínimo cero en el backend porque el contrato no lo define; el formulario web sí restringe el campo.
- **Errores en `ProblemDetail`** (RFC 7807) con estado, título, detalle y marca temporal, centralizados en `ApiExceptionHandler`. **Una consulta sin resultados es `200` con lista vacía; un recurso inexistente es `404`.** Mezclarlos haría imposible distinguir "no hay datos" de "id inválido".
- **Reporte** con el mismo filtro que el listado y `Content-Disposition: attachment`.
- **CORS** limitado a `localhost` y `127.0.0.1` en los puertos 5173 y 3000. **No autentica a nadie:** CORS solo le indica al navegador qué orígenes pueden leer la respuesta; cualquier cliente que no sea un navegador (curl, un script) ignora CORS y puede llamar a la API igual.

## Dificultades y resolución

- Al probar el formato inválido del reporte obtuve un `404` inesperado. Era un error mío al armar la URL (faltaba la barra); corregida, el servidor responde `400` con el mensaje esperado.

## Evidencia (API en ejecución con 41 facturas)

| Consulta | Resultado |
|---|---|
| `GET /api/facturas` | 200, 41 filas |
| `?estado=EMITIDA` y `?estado=%20emitida%20` | 200, 4 filas ambas (normalización) |
| `?estado=BORRADOR` | 200, 35 filas |
| `?montoMinimo=1000000` | 200, 2 filas |
| `?fechaDesde=2026-04-01&fechaHasta=2026-04-10` | 200, 2 filas |
| `?fechaDesde=2030-01-01` (sin resultados) | **200, 0 filas** |
| `?estado=XXX` · fechas invertidas · `?montoMinimo=abc` | **400** las tres |
| `GET /api/facturas/9999` | **404** |
| `reporte?formato=docx` | **400**, "Formato no soportado: docx (pdf \| xlsx \| txt)" |
| Catálogos (puntos de venta, clientes, condiciones de IVA, monedas, lista–artículo) | 200: 2, 3, 3, 2 y 7 filas |
| Tipos de contenido | `pdf` → `application/pdf`; `xlsx`/`excel` → `…spreadsheetml.sheet`; `txt`/`tsv` → `text/tab-separated-values;charset=UTF-8` |
| CORS | `localhost:5173` y `127.0.0.1:3000` permitidos; `evil.example` → **403** |

- **Pruebas automáticas:** `FacturaRestControllerTest` 5/5 (ciclo completo, 400 con errores por campo, reportes descargables, 204 y luego 404, catálogos) y `AuditRegressionHttpTest` 12/12 (JSON truncado/vacío/de tipo incorrecto, `NaN`, fechas). Suite completa **76/76** en H2.
- `requests.http` incluye ejemplos de cada operación.
