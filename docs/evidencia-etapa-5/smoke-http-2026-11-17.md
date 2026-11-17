# Verificación HTTP del circuito de estados y eliminación — 17/11/2026

Se ejecutó contra el backend local en `http://127.0.0.1:8080`, con la base H2 en memoria. Los ID 7 y 8 correspondieron a facturas creadas durante esta sesión de prueba; no se modificaron las seis facturas de ejemplo. Esta verificación HTTP complementa el recorrido de interfaz: **no es evidencia de un clic en el navegador**. La base y estos registros desaparecen al detener el proceso.

## Resultados observados

| Paso | Solicitud | Respuesta observada |
|---|---|---|
| Consultar comprobante creado desde la interfaz | `GET /api/facturas/7` | `200`; estado `EMITIDA`, número `3`, total `51842.00`, CAE presente, dos detalles. |
| Anular comprobante | `PATCH /api/facturas/7/estado` con `{"estado":"ANULADA","observaciones":"Verificación de circuito de entrega"}` | `200`; respuesta con estado `ANULADA`, número `3` y dos ítems. |
| Confirmar anulación | `GET /api/facturas/7` | `200`; estado `ANULADA`, `fechaAnulacion: "2026-11-17"`. |
| Crear otro borrador temporal | `POST /api/facturas` con el cuerpo indicado abajo | `201`, `Location: http://127.0.0.1:8080/api/facturas/8`; estado `BORRADOR`, número nulo. |
| Eliminar solo ese borrador | `DELETE /api/facturas/8` | `204 No Content`. |
| Confirmar eliminación | `GET /api/facturas/8` | `404 Not Found`. |

## Reproducción

Levantar el backend con el perfil H2 por defecto (`.\gradlew.bat bootRun`). Los ID serán distintos en otra ejecución: usar los devueltos por cada `POST` y verificar antes de anular o eliminar. Se envió `X-Usuario: sistema` en las operaciones de escritura.

```http
PATCH http://127.0.0.1:8080/api/facturas/7/estado
Content-Type: application/json
X-Usuario: sistema

{"estado":"ANULADA","observaciones":"Verificación de circuito de entrega"}
```

```http
POST http://127.0.0.1:8080/api/facturas
Content-Type: application/json
X-Usuario: sistema

{"clienteId":1,"condicionIvaId":1,"puntoVentaId":1,"tipoMonedaId":1,"observaciones":"Borrador temporal para validar eliminación","detalles":[{"listaPrecioArticuloId":1,"cantidad":1,"porcentajeBonificacion":0}]}
```

```http
DELETE http://127.0.0.1:8080/api/facturas/8
X-Usuario: sistema
```
