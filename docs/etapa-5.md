# Etapa 5 — Frontend TypeScript e integración

**Origen del código:** kit de la cátedra, con dos correcciones posteriores del frontend y cuatro pruebas de regresión. **Aportes de esta etapa:** verificación en un navegador real, correcciones de interacción y este informe. **Entorno de la verificación manual original:** Windows 11, Node 24.21 / npm 11.19, TypeScript resuelto por `package-lock.json`, navegador integrado de la aplicación de escritorio de Claude; backend en 8080 y frontend en 5173. Se utilizaron asistentes de IA (ver README).

## Decisiones y alternativas

- **Sin framework**, TypeScript `strict` y módulos ES2020. Estructura: `models/factura.dto.ts` (tipos del contrato), `services/FacturaService.ts` (cliente HTTP) y `app.ts` (DOM y estado).
- **Cliente HTTP centralizado.** La URL base sale del `meta name="api-base-url"`; toda petición lleva `X-Usuario: sistema`; un `204` se resuelve sin leer JSON; un error conserva el estado HTTP y los errores por campo en `ApiError`, con mensaje de respaldo si el cuerpo no es JSON.
- **Servidor como autoridad.** El formulario muestra el precio de catálogo y un total estimado **neto, sin IVA**, pero el request no envía precio ni importes: el IVA y el total definitivo los calcula el backend.
- **Consistencia asíncrona.** Mientras se guarda se bloquean los controles (un solo request aunque se haga clic varias veces) y se liberan con éxito o error; los datos del formulario se conservan si falla. Las acciones de emitir, anular y eliminar se bloquean por factura mientras esperan respuesta. Cada búsqueda y edición lleva un identificador de solicitud y una respuesta tardía no pisa a una más reciente, ni restaura una edición cancelada.
- **Seguridad del DOM.** El contenido externo se inserta con nodos y `textContent`, nunca como HTML.
- **Accesibilidad básica.** Región `role="status"` con `aria-live="polite"` y `aria-busy` durante el guardado; los errores permanecen hasta una acción posterior.
- **Moneda.** `PES` y `DOL` se muestran como ARS y USD; un código desconocido se deja visible y no se convierte entre monedas.

## Dificultades y resolución

- Un valor inválido en los campos del siguiente renglón impedía guardar una factura cuyos renglones ya estaban cargados, por la validación HTML del formulario principal. Se asignaron esos campos a un formulario separado; siguen validándose al agregar el renglón.
- Un doble clic en Emitir o Anular podía iniciar dos `PATCH` antes de recibir la primera respuesta. Se bloquean las acciones mutadoras de esa factura durante la petición y se libera el bloqueo al finalizar.
- La cancelación de la anulación usa `prompt()`, no soportado por el navegador integrado: la app mostró el error sin enviar el `PATCH`. Se usó un stub temporal para completar el circuito (ver `etapa-4.md`).
- Los `npm` muestran avisos de paquetes deprecados al instalar (`whatwg-encoding`); `npm ci` termina con **0 vulnerabilidades** y los avisos no afectan la ejecución.

## Evidencia

- **`npm run build`:** `tsc` sin errores en la verificación original. **`npm test`:** **34/34** pruebas con jsdom tras las correcciones (las 30 originales más cuatro casos de renglón pendiente inválido y doble clic en Emitir/Anular). Cubren además respuesta 204, errores JSON y no JSON, validaciones, texto que parece HTML, conservación de datos tras un error, respuestas en orden inverso, cancelaciones, monedas y URL base configurable.
- **Recorrido manual original en el navegador** (capturas en `docs/evidencia-etapa-5/`): carga de catálogos y de las 6 facturas de ejemplo sin errores en la consola (`00-carga-inicial.jpg`); formulario con renglón (`01-formulario-con-renglon.jpg`, estimación $42.930,00), borrador creado en la tabla (`02-borrador-creado.jpg`) y estado emitido con acciones incompatibles deshabilitadas (`03-emitida-y-botones.jpg`). El informe original también registró edición, anulación y eliminación de otro borrador, contrastadas contra la API, pero las capturas disponibles no muestran esos resultados finales.
- **Verificación parcial posterior a las correcciones:** en Chrome local se creó la factura de prueba ID 7 aun con un siguiente renglón inválido, se editó hasta tener dos ítems y se emitió con número 3. Por HTTP se confirmó que esa factura estaba `EMITIDA`, luego se anuló con `PATCH` y se consultó su estado `ANULADA` y fecha de anulación. También por HTTP se creó un borrador temporal ID 8, se eliminó (`204`) y se confirmó su ausencia (`404`). Los detalles reproducibles están en [smoke-http-2026-11-17.md](evidencia-etapa-5/smoke-http-2026-11-17.md). **No se tomaron capturas nuevas**. La anulación desde el diálogo del navegador quedó sin confirmar por una limitación del control de ese diálogo; el `PATCH` directo verifica el backend, no la interacción de la interfaz.
- **Descargas:** los enlaces "Reporte PDF" y "Reporte Excel" apuntan a `/api/facturas/reporte?formato=pdf|xlsx`. Los archivos de ejemplo se verificaron por API (ver `etapa-2.md`); no se verificó un clic en los enlaces tras las correcciones.
- Backend: suite **76/76** en H2 (ver informes anteriores).

## Límites declarados

- Las pruebas con jsdom **no sustituyen** una prueba con lector de pantalla: solo se inspeccionó la región de estado y los atributos ARIA; no se certifica accesibilidad completa.
- El recorrido original se hizo en el navegador integrado y la verificación parcial posterior en Chrome local. No se repitió el circuito completo en Chrome ni se comprobó compatibilidad amplia entre navegadores.
- Las capturas son de baja resolución (vista reducida).
- El formato TXT está disponible por API y por la construcción de URL del servicio, sin botón propio, según el enunciado.
