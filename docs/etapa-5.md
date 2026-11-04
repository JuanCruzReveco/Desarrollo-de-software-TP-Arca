# Etapa 5 — Frontend TypeScript e integración

**Origen del código:** kit de la cátedra. **Aporte propio de esta etapa:** verificación en un navegador real y este informe. **Entorno:** Windows 11, Node 24.21 / npm 11.19, TypeScript resuelto por `package-lock.json`, navegador integrado de la aplicación de escritorio de Claude; backend en 8080 y frontend en 5173. Se usó un asistente de IA (ver README).

## Decisiones y alternativas

- **Sin framework**, TypeScript `strict` y módulos ES2020. Estructura: `models/factura.dto.ts` (tipos del contrato), `services/FacturaService.ts` (cliente HTTP) y `app.ts` (DOM y estado).
- **Cliente HTTP centralizado.** La URL base sale del `meta name="api-base-url"`; toda petición lleva `X-Usuario: sistema`; un `204` se resuelve sin leer JSON; un error conserva el estado HTTP y los errores por campo en `ApiError`, con mensaje de respaldo si el cuerpo no es JSON.
- **Servidor como autoridad.** El formulario muestra el precio de catálogo y un total estimado **neto, sin IVA**, pero el request no envía precio ni importes: el IVA y el total definitivo los calcula el backend.
- **Consistencia asíncrona.** Mientras se guarda se bloquean los controles (un solo request aunque se haga clic varias veces) y se liberan con éxito o error; los datos del formulario se conservan si falla. Cada búsqueda y edición lleva un identificador de solicitud y una respuesta tardía no pisa a una más reciente, ni restaura una edición cancelada.
- **Seguridad del DOM.** El contenido externo se inserta con nodos y `textContent`, nunca como HTML.
- **Accesibilidad básica.** Región `role="status"` con `aria-live="polite"` y `aria-busy` durante el guardado; los errores permanecen hasta una acción posterior.
- **Moneda.** `PES` y `DOL` se muestran como ARS y USD; un código desconocido se deja visible y no se convierte entre monedas.

## Dificultades y resolución

- La cancelación de la anulación usa `prompt()`, no soportado por el navegador integrado: la app mostró el error sin enviar el `PATCH`. Se usó un stub temporal para completar el circuito (ver `etapa-4.md`).
- Los `npm` muestran avisos de paquetes deprecados al instalar (`whatwg-encoding`); `npm ci` termina con **0 vulnerabilidades** y los avisos no afectan la ejecución.

## Evidencia

- **`npm run build`:** `tsc` sin errores. **`npm test`:** **30/30** pruebas con jsdom (respuesta 204, errores JSON y no JSON, validaciones y límites de cantidad y bonificación, texto que parece HTML, doble envío, conservación de datos tras un error, respuestas en orden inverso, cancelación de edición y de la anulación, monedas, URL base configurable).
- **Recorrido completo en el navegador** (capturas en `docs/evidencia-etapa-5/`): carga de catálogos y de las 6 facturas de ejemplo sin errores en la consola (`00-carga-inicial.jpg`); crear borrador con renglón (`01-formulario-con-renglon.jpg`, estimación $42.930,00) y verlo en la tabla (`02-borrador-creado.jpg`); editarlo agregando un renglón; emitirlo (número 3 y CAE); anularlo (`03-emitida-y-botones.jpg`, con Emitir/Editar/Eliminar deshabilitados sobre la emitida); eliminar otro borrador. Los enlaces "Reporte PDF" y "Reporte Excel" apuntan a `/api/facturas/reporte?formato=pdf|xlsx`; la descarga de los archivos se verificó por API (ver `etapa-2.md`), no haciendo clic en los enlaces del navegador. Cada paso se contrastó además contra la API.
- Backend: suite **76/76** en H2 (ver informes anteriores).

## Límites declarados

- Las pruebas con jsdom **no sustituyen** una prueba con lector de pantalla: solo se inspeccionó la región de estado y los atributos ARIA; no se certifica accesibilidad completa.
- El recorrido se hizo en un único navegador (el integrado); no se probó en otros.
- Las capturas son de baja resolución (vista reducida).
- El formato TXT está disponible por API y por la construcción de URL del servicio, sin botón propio, según el enunciado.
