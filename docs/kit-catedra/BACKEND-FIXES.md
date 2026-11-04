# Correcciones de backend — 30/10/2026

## Cambios

- F01: descripción de detalle limitada explícitamente a 255 caracteres en DTO y columna. Un exceso recibe ProblemDetail 400 con error del campo.
- F02: cada PUT aceptado registra la modificación del agregado, incluidos cambios exclusivos de detalles y reenvíos del mismo contenido. Los callbacks JPA continúan completando la auditoría.
- F08: JSON malformado, vacío o incompatible recibe ProblemDetail 400 con mensaje estable, sin detalles internos del parser.
- F09: antes de almacenar neto, IVA, subtotal, total o saldo, el valor decimal redondeado debe sobrevivir la conversión a `double` y de vuelta sin alterarse. Se rechaza con 409 si pierde centavos; se conservan importes grandes exactamente representables y el contrato numérico existente.
- Los filtros de listado y reportes comparten validación de monto finito y orden de fechas.

## Evidencia TDD

La corrida inicial ejecutó 33 pruebas históricas aprobadas y seis regresiones fallidas esperadas (tres de backend y tres de reportes). Una segunda corrida confirmó RED de auditoría y precisión monetaria (2/2). La corrida compartida siguiente confirmó GREEN de las cinco regresiones mínimas backend y las pruebas de reportes.

| Tarea | Archivo de prueba | Capa | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| F01 longitud | `AuditRegressionHttpTest.java` | HTTP/H2 | 33/33 históricos | 256 produce error SQL | Aprobado en suite 62/62 | POST/PUT 255/256 y ausencia de cambios parciales | Sin refactor adicional |
| F02 auditoría | `AggregateAuditTest.java` | Integración H2 | 33/33 históricos | Usuario de cabecera no cambia | Aprobado en suite 62/62 | Detalle, cabecera y reenvío sin cambios | Callback reutiliza registro explícito |
| F08 cuerpo inválido | `AuditRegressionHttpTest.java` | HTTP | 33/33 históricos | JSON truncado sin media type ProblemDetail | Aprobado en suite 62/62 | Truncado, vacío y tipo incompatible | Sin refactor adicional |
| F09 precisión | `MonetaryPrecisionTest.java`, `AuditRegressionHttpTest.java` | Unidad/HTTP/H2 | 33/33 históricos | Total finito pierde 0,71 | Aprobado en suite 62/62 | Renglón, total, saldo, bordes y rechazo sin persistencia | Sin refactor adicional |
| Filtros | `AuditRegressionHttpTest.java` | HTTP/H2 | 33/33 históricos | NaN aceptado | Aprobado en suite 62/62 | Infinitos, fechas invertidas/iguales y montos finitos | Validación compartida por listado y reportes |

Estado final: `clean test bootJar --no-daemon --offline` aprobado, 62/62 pruebas backend/reportes y JAR generado. Triangulación aprobada: 12 escenarios HTTP, dos de auditoría y siete de precisión. Los dos casos de fechas invertidas fallaron antes de introducir la validación compartida. Un dato inicial de la prueba de precisión del renglón resultó exactamente representable y fue sustituido por un neto decimal realmente no representable (`9500000000000095`); no se cambió producción para forzar ese resultado.

La ejecución H2 se preservó en `build/test-results/h2-verified` y `build/reports/tests/h2-verified` antes de verificar PostgreSQL. El script `scripts/Verify-Postgres.ps1` usa exclusivamente el contenedor temporal identificado, en puerto 55432, y restaura las variables de entorno del proceso al terminar.

La primera ejecución PostgreSQL detectó un problema de entorno: el JVM Windows envía `America/Buenos_Aires` y la imagen PostgreSQL 16 rechaza ese alias con `FATAL: invalid value for parameter "TimeZone"`. El script usa la zona canónica equivalente mediante `JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires`, preservando cualquier opción previa y restaurando el entorno después. Para arrancar el JAR con el perfil PostgreSQL en este entorno se pasa `-Duser.timezone=America/Argentina/Buenos_Aires` antes de `-jar`.

La segunda ejecución PostgreSQL 16 aprobó las mismas **62/62 pruebas**, incluidas concurrencia, HTTP, persistencia y auditoría. Los XML `AggregateAuditTest` y `AuditRegressionHttpTest` registran explícitamente `HikariPool ... Added connection org.postgresql.jdbc.PgConnection`, confirmando que no se reutilizó H2. Evidencia preservada en `build/test-results/postgres-verified` y `build/reports/tests/postgres-verified`. Solo se usó la base desechable `facturacion_verificacion` del contenedor dedicado en `127.0.0.1:55432`.

No se migró el almacenamiento a decimal exacto ni se incorporó autenticación o autorización fiscal real. La comprobación de precisión valida las conversiones de los cálculos actuales; no declara precisión decimal ilimitada.
