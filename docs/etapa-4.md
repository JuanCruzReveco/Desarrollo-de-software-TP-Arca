# Etapa 4 — Escritura, estados y consistencia transaccional

**Origen del código:** kit de la cátedra. **Aporte propio de esta etapa:** verificación del circuito de escritura y este informe. **Entorno:** Windows 11, JDK 17.0.21, H2. Se usó un asistente de IA (ver README).

## Decisiones y alternativas

- **Cuatro operaciones:** `POST` (201, `Location`), `PUT` (200, solo borrador), `PATCH …/estado` (200, emitir o anular), `DELETE` (204, solo borrador). La eliminación física de un comprobante emitido se descarta: un comprobante con CAE **se anula, no se borra**.
- **Cálculo en el servidor con `BigDecimal`.** El neto se calcula sin redondear (`cantidad × precio × (1 − bonif/100)`), el IVA se calcula sobre ese neto y se redondea `HALF_UP` a dos decimales, el neto se redondea por separado y el subtotal suma ambos redondeados. Alternativa descartada: `Math.round` sobre `double`, que acumula error binario. El precio sale de `ListaPrecioArticulo`; el request no lo trae.
- **`double` en el modelo, control en los bordes.** Se conservan los campos `double` y, antes de persistir cada importe, se verifica que sea finito y que `BigDecimal.valueOf(double)` conserve el valor decimal calculado. Si no, se rechaza con un conflicto de negocio, sin persistencia parcial. Alternativa: migrar a `BigDecimal`/`NUMERIC`, que sería más robusta pero excede lo pedido.
- **Estados.** Solo `BORRADOR → EMITIDA → ANULADA`; cualquier otra transición o editar/eliminar fuera de borrador da `409`. Emitir exige renglones, asigna el siguiente número del punto de venta y genera un CAE simulado de 14 dígitos con vencimiento a 10 días.
- **Concurrencia con bloqueo pesimista.** `findParaActualizarById` usa `PESSIMISTIC_WRITE` sobre la factura antes de editar, cambiar estado o borrar, y `findParaNumerarById` bloquea también el punto de venta antes de `COALESCE(MAX(numero), 0) + 1`. La restricción única (punto de venta, número) queda como red de seguridad en la base. Alternativa: bloqueo optimista con `@Version`, que detecta el conflicto pero obliga a reintentar.
- **Auditoría** por `X-Usuario` (si falta o no existe, se usa `sistema`), con el contexto en `ThreadLocal` y limpieza al terminar la petición. Identifica al actor declarado; **no autentica ni autoriza**. Cada `PUT` registra la modificación aunque solo cambien renglones o se reenvíe lo mismo.

## Dificultades y resolución

- El navegador integrado donde se probó la interfaz no implementa `prompt()`, que usa la anulación para pedir el motivo. La aplicación mostró el error y **no envió ningún `PATCH`** (la factura siguió `EMITIDA`), que es el comportamiento correcto ante un diálogo cancelado. Para recorrer el circuito se reemplazó `prompt` por un stub temporal en la página. Esto es una limitación del navegador de prueba, no de la aplicación.

## Evidencia

**Circuito manual completo** (interfaz + API):

| Paso | Resultado observado |
|---|---|
| Crear borrador: mouse mayorista × 3 con 10 % | Estimación del navegador $42.930 (sin IVA); el servidor guardó **$51.945,30** = 42.930 × 1,21 |
| Editar (PUT) agregando teclado × 2 | 2 renglones, total **$105.185,30** = 51.945,30 + 44.000 × 1,21 |
| Emitir (PATCH) | N.º **3** en Sucursal Central (seguía a las emitidas 1 y 2), CAE de 14 dígitos, vencimiento +10 días, resultado `A` |
| Anular con motivo | `ANULADA`, fecha de anulación registrada, número conservado |
| `DELETE` sobre la anulada | **409** |
| `DELETE` sobre un borrador | **204**; luego `GET` del mismo id → **404** |

**Pruebas automáticas (kit):** `FacturaServiceTest` 8, `ValidationFacturaTest` 2 (renglones nulos → 400), `MonetaryCalculationTest` 3 (subtotal suma neto e IVA redondeados, mitad decimal hacia arriba, bonificación y alícuotas), `MonetaryPrecisionTest` 7 y `MonetaryOverflowTest` 3 (rechazo por pérdida de centavos y por desborde), `AuditRegressionHttpTest` 12 (límites 255/256, JSON inválido, rollback), `AggregateAuditTest` 2 y **`ConcurrentFacturaTest` 5** (`emisionesSimultaneas_tienenNumerosDistintosYConsecutivos`, `escrituraConcurrente_noAlteraFacturaQueSeEstaEmitiendo`, `emision_esperaReservaDelPuntoVenta`). Suite completa **76/76** en H2.

**Límite importante:** la concurrencia se probó **solo en H2**. El comportamiento de los bloqueos pesimistas puede diferir en PostgreSQL; un resultado en H2 no lo demuestra. PostgreSQL no se probó en este equipo.
