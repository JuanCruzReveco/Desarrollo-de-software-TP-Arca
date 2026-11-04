# Correcciones de reportes — 30/10/2026

## Contratos

- F06: TXT UTF-8 con nueve columnas y una línea física por factura. Los campos de texto escapan barra invertida como `\\`, tabulación como `\t`, retorno como `\r` y salto de línea como `\n`. Las comillas permanecen literales. El lector separa primero filas y columnas y decodifica escapes en una única pasada. Un importador TSV genérico verá los escapes literalmente; no se usa el dialecto CSV con campos entrecomillados multilínea.
- F07: fuente Noto Sans CJK SC incluida en recursos, codificación Identity-H e incrustación/subconjunto PDF. Licencia SIL OFL 1.1 y procedencia en `src/main/resources/fonts/`. Verificados acentos, chino, japonés y coreano. Antes de generar se comprueba cada punto de código de los campos textuales; si falta el glifo se rechaza con HTTP 400, identificación `U+...` y sugerencia de exportar TXT o Excel. No se promete cobertura universal de Unicode.
- O01: fila de cabecera repetida en las páginas que contienen datos. El reporte vacío conserva encabezados y muestra una fila explicativa.

## Evidencia inicial TDD

| Tarea | Test | Capa | Safety Net | RED | GREEN | Triangulación | Refactor |
|---|---|---|---|---|---|---|---|
| F06 | ReportTextIntegrityTest | Exportación real | 33 pruebas históricas verdes | 1 fallo reproducido por separadores | 2/2 aprobadas | Lector reversible, escapes literales, comillas y campos adicionales | Escape centralizado |
| F07 | ReportPdfIntegrityTest | PDF real y extracción | 33 pruebas históricas verdes | Nombre chino ausente; posteriormente 2 glifos no cubiertos aceptados en reproducción Java | 4/4 aprobadas | Acentos/CJK preservados; emoji y código sin asignar rechazados | Carga de fuente encapsulada |
| O01 | ReportPdfIntegrityTest | PDF multipágina real | 33 pruebas históricas verdes | Cabecera ausente desde página 2; reporte vacío sin cabecera en reproducción Java | 2/2 aprobadas | 100 filas y reporte vacío | Sin refactor adicional |

La corrida mínima aprobó 3 pruebas nuevas de reportes y las 4 pruebas anteriores de totales, además de los 5 casos mínimos del agente backend. La corrida posterior coordinada por el agente principal aprobó las 8 pruebas de integridad de reportes. Los fallos de triangulación backend de esa corrida son ajenos a reportes y se informaron al agente backend; la suite global y el empaquetado se documentan en VERIFICACION.md.

## Revisión visual

Se generó un reporte de 100 filas con `José Muñoz 李华 東京 서울` y se renderizaron las páginas 1 y 2 usando Poppler. Los glifos son visibles, los encabezados se repiten y no se observaron recortes ni solapamientos. Evidencia local en `tmp/report-verification/unicode-multipagina.pdf`, `page-1.png` y `page-2.png`. Poppler emitió avisos de fuentes de sustitución Symbol/ArialUnicode, pero la fuente incrustada renderizó los textos del reporte correctamente.

## Límites

La comprobación visual cubrió las primeras dos páginas de un reporte de cien filas. La prueba automatizada comprueba cabeceras en cada página con datos. No se incorporaron escrituras complejas no cubiertas por la fuente ni una certificación fiscal. El TXT requiere un lector que decodifique el contrato de escapes documentado para reconstruir controles originales.
