# Verificación de correcciones — actualización del 30 de octubre de 2026

## Alcance

Correcciones funcionales de frontend, concurrencia, validación, importes y reportes. El proyecto conserva su alcance académico: CAE simulado e identificación de auditoría mediante `X-Usuario`, sin autenticación.

## Ejecución reproducible

Desde la raíz, con Java 17 o superior:

```powershell
.\gradlew.bat test bootJar --no-daemon
```

Desde `frontend`, con Node.js 18.13 o superior:

```powershell
npm ci --ignore-scripts
npm test
npm run build
```

## Evidencia TDD

| Tarea | Archivo de prueba | Capa | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| DOM seguro, cancelar, guardado, validación, moneda y API | `frontend/test/app.test.mjs` | DOM y cliente HTTP | Compilación inicial correcta, sin pruebas previas | Fallos reproducidos antes de modificar producción | 18/18 | Entrada/API, cancelar/aceptar, pendiente/error, rangos y monedas | Renderizado con nodos DOM; compilación y pruebas verdes |
| Concurrencia y elementos nulos | `ConcurrentFacturaTest`, `ValidationFacturaTest` | Integración H2 y HTTP | 13/13 originales | 7/7 fallaron por defectos esperados | 7/7 | Emisiones con 2 y 6 hilos, edición/borrado, uno/dos elementos nulos | Sin refactor adicional |
| Redondeo | `MonetaryCalculationTest` | Unidad | 13/13 | Dos fallos esperados | 3/3 | Caso fraccionario, bonificación y alícuotas | Incluido en suite verde de 29 pruebas |
| Moneda | `ReportCurrencyTest` | Integración H2 | 13/13 | Dos fallos esperados | 2/2 | Creación, listado y catálogos PES/DOL | Incluido en suite verde de 29 pruebas |
| Totales de reportes | `ReportTotalsTest` | PDF/XLSX/TXT reales | Suite anterior verde | 4/4 fallos esperados | 4/4 | Estados mezclados, dos monedas y ausencia de emitidas | Incluido en suite verde de 29 pruebas |
| Desbordamiento monetario | `MonetaryOverflowTest`, `ReportOverflowHttpTest` | Unidad y HTTP | 29/29 | 4/4 fallos esperados | 4/4 aprobadas; incluidas en suite final | Renglón/suma fuera de rango, importe grande finito, rechazo HTTP sin persistencia | Sin refactor posterior |

La base original se reparó antes de cambiar producción: el fixture transaccional leía campos públicos sobre un proxy Spring y difería los borrados de usuarios hasta después de nuevas inserciones. Se reemplazó ese acceso por getters y se agregó el `flush` necesario. Las 13 pruebas originales pasaron después de esta reparación.

## Correcciones de la auditoría del 30/10/2026

Se implementaron F01–F09, O01, O02 y la validación adicional de filtros. Los contratos de longitud, precisión monetaria, auditoría de PUT, escapes TXT y cobertura de fuente PDF se explican en README. La documentación D01 queda actualizada con resultados ejecutados.

### TDD Cycle Evidence

| Tarea | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| F01/F08 y filtros | AuditRegressionHttpTest | HTTP/H2 | 33/33 históricos | Longitud, JSON y NaN reproducidos | 12/12 | POST/PUT 255/256, JSON vacío/tipos, finitud/fechas, rollback | Validación compartida |
| F02 auditoría | AggregateAuditTest | Integración H2 | 33/33 | Usuario de cabecera no cambia | 2/2 | Detalle, cabecera y reenvío sin cambios | Registro explícito reutilizado |
| F09 precisión | MonetaryPrecisionTest y AuditRegressionHttpTest | Unidad/HTTP/H2 | 33/33 | Total pierde 0,71 | 7/7 unitarias y caso HTTP | Neto/IVA/subtotal/total/saldo; límites | Sin refactor adicional |
| F03/F04/F05/F01/F08/O02 frontend | frontend/test/app.test.mjs | DOM/HTTP simulado | 18/18 | 6 fallos y 2 refuerzos de región accesible | 30/30 | Respuestas invertidas, cancelar, POST/PUT/error, 255/256, errores no estándar, mensajes | Helper de bloqueo |
| F06 TXT | ReportTextIntegrityTest | Exportación real | 33/33 | Separadores desestructuran filas | 2/2 | Escapes/comillas/lectura reversible | Escape centralizado |
| F07 Unicode PDF | ReportPdfIntegrityTest | PDF/extracción | 33/33 | Chino ausente y glifos no cubiertos aceptados | 4/4 | Acentos/CJK; emoji y código no asignado rechazados | Fuente encapsulada |
| O01 encabezados PDF | ReportPdfIntegrityTest | PDF multipágina | 33/33 | Sin cabecera página 2/vacío | 2/2 | 100 filas y reporte vacío | Sin refactor adicional |

Detalles de cada ciclo: [backend](BACKEND-FIXES.md), [frontend](FRONTEND-FIXES.md) y [reportes](REPORT-FIXES.md).

## Estado de verificación actual

- Frontend: 30/30 pruebas aprobadas; TypeScript compila. La auditoría npm sin vulnerabilidades corresponde a la revisión previa; no se cambiaron dependencias frontend.
- Backend: `clean test bootJar` aprobado, 62/62 pruebas (33 históricas y 29 nuevas), sin fallos. Persistencia de esta ejecución probada con H2.
- PDF: renderizadas e inspeccionadas páginas 1 y 2 de 100 filas con `José Muñoz 李华 東京 서울`; glifos visibles y cabeceras repetidas sin recortes ni solapamientos. Evidencia en `tmp/report-verification/`.
- Navegador real integrado: comprobados carga de catálogos y seis facturas, creación del borrador #7, controles deshabilitados mientras se guarda, edición persistida al reabrir, cancelación y búsquedas BORRADOR/EMITIDA con enlaces de reporte coherentes. Sin errores ni advertencias de consola. Se usó el JAR nuevo en puerto 18080 para preservar un backend anterior en 8080. Captura en `tmp/browser-verification/edicion-verificada.jpg`; página temporal de prueba eliminada.
- PostgreSQL 16: segunda ejecución aprobada, 62/62 pruebas, con conexión JDBC PostgreSQL real confirmada. La primera ejecución falló al iniciar los contextos porque la JVM de Windows enviaba el alias `America/Buenos_Aires`, rechazado por esa imagen; se repitió con `JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires`, limitado al proceso de prueba. El README incluye el equivalente para arrancar el JAR. No se cambiaron las fuentes para adaptar ese problema del entorno.
- Evidencia de ambas bases conservada en `build/test-results/h2-verified/` y `build/test-results/postgres-verified/`, además de los informes correspondientes en `build/reports/`.
- Node muestra un aviso por el uso experimental de módulos VM en el entorno de pruebas.

Los cálculos internos usan `BigDecimal`, pero la API y las entidades conservan campos `double`. No se realizó una migración a almacenamiento decimal exacto. Tampoco se implementaron autenticación real, integración fiscal, migraciones versionadas o paginación/exportaciones de gran volumen.

La inspección del árbol accesible confirmó la región de estado; no se probó un lector de pantalla ni se certifica accesibilidad completa. Las carreras HTTP se prueban de forma determinista con promesas diferidas en jsdom; el recorrido de navegador confirma el flujo integrado ordinario.

El proceso que ya estaba activo en el puerto 8080 conserva la versión anterior. Para usar las correcciones hay que reiniciar ese backend con el JAR recién generado `build/libs/facturacion-arca-1.0.0.jar` o con `gradlew.bat bootRun`. La verificación no reemplazó ese proceso ni alteró sus datos.
