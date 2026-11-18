# Etapa 2 — Lombok, DTO, JPQL y reportes

**Origen del código:** kit de la cátedra. **Aporte propio de esta etapa:** verificación (incluida la revisión visual del PDF y del Excel) y este informe. **Entorno:** Windows 11, JDK 17.0.21, H2. Se usó un asistente de IA (ver README).

## Decisiones y alternativas

- **Lombok** como `compileOnly` + `annotationProcessor`; getters, setters y constructores puntuales, **sin `@Data` indiscriminado**. `FacturaVenta` excluye `detalles` y `FacturaVentaDetalle` excluye `factura` de `@ToString` y `@EqualsAndHashCode`.
- **Por qué la exclusión.** Si ambos lados de la relación entran en el `toString` generado, imprimir una factura imprime sus detalles, cada detalle imprime su factura, y así hasta `StackOverflowError`; `equals` y `hashCode` caen igual. Además, tocar una colección `LAZY` desde un método generado dispararía consultas inesperadas.
- **Proyección JPQL** en `FacturaReporteRepositoryImpl` con `SELECT new …FacturaReporteDTO(…)`. Alternativa: cargar entidades y mapear en memoria, descartada porque traería columnas, asociaciones y colecciones que el reporte no usa. **El orden del constructor importa:** la expresión se resuelve por posición y tipo, y por eso el DTO tiene un constructor que acepta `Long` (resultado de `COUNT`) y `EstadoFactura`.
- **Joins:** `LEFT JOIN` a cliente y detalles (una factura sin cliente o sin renglones no desaparece), `COALESCE(c.denominacion, 'Consumidor Final')`, `JOIN` a condición de IVA, punto de venta y moneda, `GROUP BY` de los campos no agregados y `ORDER BY fechaEmision DESC, id DESC`.
- **Totales:** PDF y Excel suman **solo `EMITIDA`**, separados por código de moneda; no convierten ni suman borradores ni anuladas.
- **PDF** con OpenPDF, A4 apaisado, fuente Noto Sans CJK incrustada (Identity-H); un glifo sin cobertura produce un error explícito en lugar de sustituirse. **Excel** con POI y fórmulas `SUMIFS`. **TXT** UTF-8, tabulaciones, una fila por factura, con escapes reversibles (`\\`, `\t`, `\r`, `\n`).

## Dificultades y resolución

- Al leer el TXT desde PowerShell los acentos aparecían rotos ("Juan PÃ©rez"). Era la decodificación de la consola, no el archivo: leído como UTF-8 sale "Juan Pérez", y el navegador también lo muestra bien.
- Los totales del PDF se contrastaron contra un cálculo manual en lugar de confiar solo en las pruebas.

## Evidencia

- **Pruebas automáticas (15):** `ReportTotalsTest` 4, `ReportPdfIntegrityTest` 6 (acentos, CJK, emoji y código no asignado rechazados, multipágina, vacío), `ReportTextIntegrityTest` 2, `ReportCurrencyTest` 2, `ReportOverflowHttpTest` 1. Todas en verde; suite completa **76/76** en H2.
- **Revisión visual del PDF** (41 filas, 2 páginas, `docs/muestras/etapa-2/reporte-multipagina.pdf`), renderizado a imagen y observado: A4 apaisado; título, fecha de generación y cantidad de registros; **cabecera de tabla repetida en la página 2**; borradores con guion en el número; sin textos recortados ni solapados. Totales al pie: **PES 3.622.647,00** = 1.149.500 + 2.424.142 + 49.005 (solo emitidas) y **DOL 11.858,00** = 9.800 × 1,21. Coinciden con el cálculo manual.
- **Excel** (`reporte.xlsx`): hoja `Facturas`; fechas como fecha (`dd/mm/yyyy`) y totales como número (`#,##0.00`); fórmula `=SUMIFS(G2:G7,H2:H7,"EMITIDA",I2:I7,I9)` con valor 3.622.647, igual al del PDF.
- **TXT:** 9 columnas separadas por tabulaciones, importes con dos decimales y punto, fechas ISO; no incluye pie de totales.

**Límite:** la revisión visual se hizo sobre el PDF generado en este equipo. No se probó con un lector de PDF distinto del usado para renderizar.
