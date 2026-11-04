# Correspondencia con las consignas del Campus

Este repositorio responde a dos documentos del curso Desarrollo de Software (Campus Virtual): el **TP integrador de facturación ARCA** (cinco etapas) y el **TP «Operaciones de escritura en API REST (POST, PUT, DELETE)» + frontend CRUD de facturación**, que pertenece a la misma aplicación. Esta tabla indica dónde se cumple cada consigna del segundo.

## TP CRUD Factura Venta

| Consigna | Dónde está |
|---|---|
| **1. DTOs de entrada** que reciben IDs y no entidades anidadas (`clienteId`, `condicionIvaId`, `puntoVentaId`, `tipoMonedaId`, …) | `src/main/java/ar/edu/utn/frm/facturacion/dto/FacturaRequestDTO.java` y `FacturaDetalleRequestDTO.java`. El renglón referencia `listaPrecioArticuloId` (el registro lista–artículo) y no `articuloId` aislado, como fija el enunciado del integrador: de ahí sale el precio. |
| **2a. `@Transactional`** en las escrituras | `service/FacturaService.java`: `crear`, `actualizar`, `cambiarEstado` y `eliminar` son `@Transactional`; las lecturas, `readOnly = true`. |
| **2b. Crear:** buscar entidades por ID y lanzar excepción si no existen; mapear, calcular neto, IVA y total; guardar con cascada | `FacturaService.crear` y `aplicarRequest`: `RecursoNoEncontradoException` (→ 404) por cada referencia; los importes se calculan con `BigDecimal`; un solo `save()` persiste cabecera y detalles por cascada. |
| **2c. Actualizar:** buscar por ID (404 si no existe), actualizar cabecera e ítems | `FacturaService.actualizar`: bloquea la factura, exige estado `BORRADOR` (si no, 409), reemplaza cabecera y renglones y recalcula. |
| **2d. Eliminar / anular:** eliminación física o anulación lógica con fecha | Ambas: `eliminar` borra físicamente un **borrador**; la anulación lógica es `cambiarEstado` → `ANULADA`, con `fechaAnulacion` y baja lógica de auditoría. Un comprobante emitido no se borra: se anula. |
| **3. Endpoints** `POST` (201 + `FacturaReporteDTO`), `PUT` (200), `DELETE` (204), 404 | `controller/FacturaRestController.java`. El `POST` devuelve además el encabezado `Location`. En este proyecto el cambio de estado va por `PATCH /api/facturas/{id}/estado`, como pide el enunciado del integrador. |
| **4–5. Interfaces TypeScript espejo de los DTO** (`models/factura.dto.ts`) | `frontend/src/models/factura.dto.ts`: `FacturaReporteDTO`, `FacturaRequestDTO`, `FacturaDetalleRequestDTO`, además de `FacturaDetalladaDTO`, `FacturaFiltrosDTO`, `CambioEstadoRequestDTO`, `OpcionDTO` y `ProblemDetail`. |
| **6. `FacturaService.ts`** con `obtenerFacturas(filtros)`, `crearFactura`, `actualizarEstado(id, estado, observaciones?)`, `eliminarFactura` | `frontend/src/services/FacturaService.ts`, con esas firmas, más `obtenerFactura`, `actualizarFactura` y los catálogos. La URL base se toma del `meta api-base-url` (por defecto `http://localhost:8080/api`). |
| **7. `index.html`** con búsqueda, alta con tabla dinámica de renglones y listado con acciones por fila; **`app.ts`** | `frontend/index.html` (tres secciones: «Buscar comprobantes», «Nueva factura» y «Comprobantes») y `frontend/src/app.ts`. |

## Diferencias que conviene tener presentes

- El TP CRUD menciona `fechaAnulacion = new Date()`; el proyecto usa `LocalDate.now()` (`java.time`), que es el tipo del modelo.
- El TP CRUD describe un `PUT` que recibe «datos/estado»; aquí el `PUT` reemplaza cabecera y renglones de un borrador y el estado se cambia por `PATCH`, según el integrador.
- El TP CRUD figura como trabajo grupal; el integrador de este repositorio es **individual**.

## TP integrador (cinco etapas)

Cada etapa tiene su informe con decisiones, dificultades y evidencia: [etapa-1](etapa-1.md), [etapa-2](etapa-2.md), [etapa-3](etapa-3.md), [etapa-4](etapa-4.md) y [etapa-5](etapa-5.md). Los tags `etapa-1` a `etapa-5` marcan cada entrega.
