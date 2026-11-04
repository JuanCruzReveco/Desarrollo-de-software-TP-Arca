# Correcciones frontend — 30/10/2026

Se corrigieron F03, F04, F05, O02 y la parte frontend de F01/F08 de la auditoría.

- Cada búsqueda conserva su identificador; respuestas y errores anteriores se descartan. Tabla y reportes usan los mismos filtros confirmados.
- Mientras se guarda, el formulario y las acciones de facturas quedan bloqueados. Un error conserva los valores y desbloquea controles para reintentar.
- Cancelar o iniciar otra edición invalida cargas pendientes. El botón Cancelar está disponible desde el inicio de la carga.
- La descripción admite 255 caracteres; también se valida al agregar el renglón.
- Errores HTTP sin ProblemDetail muestran un mensaje de respaldo con el código HTTP.
- Los mensajes usan una región de estado `aria-live="polite"` y `aria-atomic="true"`, presente desde la carga inicial. Los errores permanecen hasta el siguiente mensaje; el texto de éxito se limpia a los seis segundos sin retirar la región del árbol accesible.

## Evidencia TDD

Archivo de pruebas: `frontend/test/app.test.mjs`. Baseline ejecutado: 18/18. Se añadieron seis regresiones: fallaron las seis antes de tocar producción. Tras implementar: 24/24. Se añadieron seis casos de triangulación: 30/30. `npm --prefix frontend test` compila TypeScript antes de ejecutar las pruebas.

La revisión de accesibilidad reforzó dos pruebas para exigir que la región estuviera presente antes del anuncio y permaneciera después de expirar un éxito: RED 2/30, corrección y GREEN 30/30.

| Tarea | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| F03 búsquedas | app.test.mjs | DOM/HTTP simulado | 18/18 | Falló orden inverso | Pasó | Vacío y error obsoleto | Sin refactor adicional |
| F04 guardado | app.test.mjs | DOM/HTTP simulado | 18/18 | Falló bloqueo | Pasó | PUT fallido conserva datos; POST y doble envío | Helper bloqueo; suite verde |
| F05 edición | app.test.mjs | DOM/HTTP simulado | 18/18 | Falló cancelación | Pasó | Dos ediciones resueltas al revés | Sin refactor adicional |
| F01 descripción | app.test.mjs | DOM | 18/18 | Falló 256 | Pasó | 255 preservados en payload | Sin refactor adicional |
| F08 errores | app.test.mjs | DOM/cliente HTTP | 18/18 | Mostraba undefined | Pasó | Cuerpo no JSON | Sin refactor adicional |
| O02 anuncios | app.test.mjs | DOM/temporizadores | 18/18 | Sin región accesible | Pasó | Error persistente y éxito temporal | Sin refactor adicional |

Las pruebas DOM no acreditan anuncios con un lector de pantalla real. Se ejecutó un recorrido en navegador integrado contra el JAR actualizado (puerto 18080): alta, bloqueo durante guardado, edición y relectura, cancelación y filtros con enlaces coherentes; consola sin errores ni advertencias. Captura en `tmp/browser-verification/edicion-verificada.jpg`. La página temporal de prueba fue eliminada; la configuración normal conserva el puerto 8080.
