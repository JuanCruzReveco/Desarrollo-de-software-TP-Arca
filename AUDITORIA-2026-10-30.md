# Auditoría del proyecto de facturación — 30 de octubre de 2026

> **Seguimiento de correcciones (30/10/2026):** se implementaron F01–F09, O01, O02 y la validación adicional de filtros. D01 se resolvió actualizando la documentación. La suite final H2 aprobó 62/62 pruebas y `bootJar`; PostgreSQL aprobó 62/62 tras ajustar la zona horaria de la JVM. Frontend aprobó 30/30 y TypeScript. Se verificaron los flujos ordinarios en navegador y el PDF Unicode multipágina. Los detalles y límites de comprobación se mantienen en [VERIFICACION.md](VERIFICACION.md). El cuerpo siguiente conserva la evidencia histórica previa a las correcciones y no debe interpretarse como lista actual de defectos abiertos.

## Resultado

La compilación y las pruebas existentes pasan, pero no cubren varios escenarios que reproducen errores. Esta revisión identifica **nueve defectos funcionales confirmados**, una observación de presentación PDF, una de accesibilidad comprobada en el código y una discrepancia documental. No se aplicaron correcciones de producción. No se confirmó un hallazgo crítico dentro del alcance académico declarado.

El proyecto se presenta como trabajo práctico local. La falta de autenticación real, el CAE simulado y la base H2 temporal están documentados y se distinguen de los defectos de comportamiento encontrados dentro del alcance actual.

## Alcance y metodología

- Inspección de frontend TypeScript, HTML, CSS, servicio HTTP, DTOs, configuración y pruebas.
- Revisión de backend: servicios, entidades, repositorios, validación, auditoría, controladores, excepciones, reportes y configuración de ejecución.
- Ejecución de las suites existentes, compilación y empaquetado; auditoría de dependencias npm sin aplicar cambios.
- Reproducciones controladas de escenarios que no cubre la suite: respuestas HTTP fuera de orden en jsdom, modificaciones durante guardado, cancelación de edición, límites de texto, actualización de auditoría y contenido de exportaciones.
- Se mantuvieron las fuentes sin cambios. Las compilaciones regeneran artefactos derivados. Este informe es el único archivo de documentación nuevo de la auditoría.
- Las comprobaciones de backend y reportes fueron realizadas por los revisores delegados; las de frontend se ejecutaron sobre el JavaScript compilado utilizando jsdom y respuestas HTTP controladas.

No se inicializó Git ni OpenSpec: la carpeta revisada no contiene un repositorio Git ni una raíz OpenSpec detectables. No se creó un cambio OPSX para una auditoría de solo lectura.

## Verificaciones ejecutadas

| Verificación | Resultado | Qué acredita / límite |
|---|---|---|
| Pruebas backend | 33 aprobadas | Comportamientos cubiertos por la suite; persistencia probada con H2 |
| Empaquetado `bootJar` | Correcto | La aplicación puede compilarse y empaquetarse |
| `npm test` | 18 aprobadas | Regresiones DOM/cliente HTTP existentes |
| TypeScript (`tsc`, incluido en `npm test`) | Correcto | Tipado y generación del frontend |
| `npm audit --json` | 0 vulnerabilidades informadas; 84 dependencias | Resultado del registro consultado durante esta revisión; no demuestra ausencia de vulnerabilidades desconocidas |
| Reproducciones adicionales | Nueve defectos funcionales confirmados | No se incorporaron como pruebas permanentes |

Las pruebas frontend y la auditoría npm se reejecutaron en la continuación del 30/10/2026. El aviso de Node sobre módulos VM experimentales no produjo fallos de pruebas.

**No verificado:** ejecución con PostgreSQL, comportamiento de bloqueos/SQL en ese motor, interacción en un navegador real, lector de pantalla, diseño responsive medido, carga de gran volumen ni auditoría completa de vulnerabilidades de las dependencias Java. No debe interpretarse el resultado H2 como certificación del perfil PostgreSQL.

## Prioridades

- **P1:** defecto de alta urgencia, con impacto grave demostrado en el alcance revisado. No se asigna esta categoría sin evidencia suficiente.
- **P2:** defecto funcional que debe corregirse, reproducible bajo un escenario concreto.
- **P3:** problema de presentación, accesibilidad o documentación con menor impacto inmediato.

| ID | Prioridad | Hallazgo | Evidencia |
|---|---|---|---|
| F01 | P2 | Descripción de renglón demasiado larga produce HTTP 500 | Reproducción HTTP con 256 caracteres |
| F02 | P2 | Cambiar únicamente detalles puede no actualizar la auditoría de cabecera | Reproducción de PUT y comparación con cambio de observaciones |
| F03 | P2 | Una búsqueda anterior sobrescribe los resultados de la última | Reproducción DOM con respuestas invertidas |
| F04 | P2 | Cambios realizados durante el guardado se descartan sin guardarse | Reproducción DOM y comparación del payload |
| F05 | P2 | Una respuesta de edición tardía revierte la cancelación | Reproducción DOM con GET pendiente |
| F06 | P2 | Tabulaciones y saltos de línea desestructuran el TXT | Generación con cliente `A\tB\nC` |
| F07 | P3 | El PDF pierde caracteres fuera del repertorio de la fuente | Generación con nombre `李华` |
| F08 | P3 | JSON malformado no respeta el contrato de errores ProblemDetail | Reproducción HTTP 400 con esquema predeterminado |
| F09 | P2 | Importes extremos finitos pierden centavos al convertirse a `double` | Factura con subtotal grande más 0,71; total pierde 0,71 |
| O01 | P3 | PDF multipágina no repite encabezados de columnas | PDF de 100 filas y cuatro páginas |
| O02 | P3 | Mensajes dinámicos sin mecanismo de anuncio accesible | Inspección HTML/TypeScript; sin lector de pantalla |
| D01 | P3 | Documento de verificación mantiene resultados pendientes ya resueltos | Comparación con la ejecución actual |

## Hallazgos funcionales

### F01 — Descripción de renglón sin validación de longitud

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/dto/FacturaDetalleRequestDTO.java:34`; `model/FacturaVentaDetalle.java:30` dentro del mismo paquete base. El campo visible está en `frontend/index.html:63`.

**Disparador:** enviar un POST válido salvo por una descripción de renglón de 256 caracteres. La interfaz tampoco limita la longitud de ese campo.

**Resultado confirmado:** la solicitud alcanza la persistencia y termina en HTTP 500 por la longitud de la columna (SQLState 22001), en lugar de un rechazo de validación 400. El control con 255 caracteres devuelve 201. Un PUT de 256 también devuelve 500 y el rollback conserva el contenido anterior.

**Causa:** el DTO no declara `@Size` y la entidad conserva la longitud predeterminada de la columna de texto, materializada como `VARCHAR(255)`. La entrada aceptada por la API no coincide con la capacidad de almacenamiento.

**Impacto:** una entrada ordinaria desde el formulario provoca un error interno y no permite guardar la factura. El usuario no recibe una explicación de validación por campo.

**Corrección sugerida:** establecer un límite explícito y coherente entre contrato, validación, columna y frontend. Si se necesitan descripciones más largas, ampliar el almacenamiento deliberadamente; no truncar silenciosamente.

**Prueba faltante:** POST y PUT con 255 caracteres aceptados y 256 rechazados con 400 y error de campo, o los límites que se acuerden; comprobar ausencia de persistencia parcial.

### F02 — Auditoría de cabecera incompleta en cambios exclusivos de detalles

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/service/FacturaService.java:77-81` y `116-145`; `src/main/java/ar/edu/utn/frm/facturacion/model/AuditoriaApp.java:58-62`.

**Disparador:** ejecutar PUT sobre un borrador cambiando únicamente la descripción de un renglón, con la misma cabecera y los mismos importes.

**Resultado confirmado:** en un contexto Spring/H2 aislado, `sistema` creó la factura y `acortez` cambió la descripción de `antes` a `despues`. El detalle se persistió, pero fecha y usuario de modificación de cabecera permanecieron idénticos. Como control, cambiar observaciones de cabecera sí actualizó fecha y usuario.

**Causa:** la auditoría depende de `@PreUpdate` de la entidad. La sustitución de renglones puede modificar filas hijas sin producir un UPDATE de los campos de cabecera cuando sus valores calculados permanecen iguales. Invocar `save` no garantiza ese callback en la cabecera.

**Impacto:** la fecha y el usuario de última modificación de la factura no representan necesariamente la última modificación de su contenido.

**Corrección sugerida:** registrar explícitamente la modificación del agregado al aceptar una edición, o adoptar un mecanismo de auditoría que contemple cambios de sus detalles. Mantener los callbacks como apoyo sin depender exclusivamente de la suciedad de columnas de cabecera.

**Prueba faltante:** modificar solo descripción bajo un usuario distinto y verificar contenido, fecha y usuario de modificación; repetir con cambio de cabecera como control y con una petición sin cambios para definir su semántica.

### F03 — Resultados de búsqueda y reporte inconsistentes por respuestas fuera de orden

**Ubicación:** `frontend/src/app.ts:119-125`.

**Disparador reproducido:** buscar BORRADOR; antes de recibir respuesta, buscar EMITIDA; completar primero la segunda solicitud y luego la primera.

**Resultado observado:** selector `EMITIDA`, tabla con una factura `BORRADOR` y enlace PDF terminado en `estado=EMITIDA`.

**Causa:** cada respuesta asigna `estado.facturas` sin comprobar si sigue siendo la búsqueda vigente. Los enlaces se construyen con `estado.filtros`, variable global que ya puede corresponder a otra petición.

**Impacto:** el usuario ve un listado que contradice el filtro y descarga un reporte diferente de lo mostrado.

**Corrección sugerida:** capturar los filtros en cada ejecución y usar un identificador de solicitud para ignorar respuestas obsoletas; opcionalmente cancelar la petición previa. Actualizar listado y enlaces desde la misma búsqueda vigente.

**Prueba faltante:** dos consultas resueltas en orden inverso; verificar que lista, contador, estado vacío y enlaces pertenecen siempre a la última búsqueda.

### F04 — Pérdida de cambios introducidos mientras se guarda

**Ubicación:** `frontend/src/app.ts:209-225`, especialmente captura del request en 213, deshabilitación exclusiva de Guardar en 215 y limpieza incondicional en 224.

**Disparador reproducido:** agregar un renglón, guardar, mantener el POST pendiente y agregar un segundo renglón antes de recibir la respuesta.

**Resultado observado:** dos filas visibles durante la espera; el payload contiene un único detalle; al completarse la respuesta, el formulario queda con cero filas. El segundo renglón desaparece sin haberse enviado.

**Causa:** el request se captura al inicio, pero el resto del formulario continúa editable. La respuesta limpia todo el estado actual, incluso modificaciones posteriores a esa captura.

**Impacto:** pérdida de trabajo y posibilidad de creer que una factura incluye datos que no fueron enviados.

**Corrección sugerida:** bloquear temporalmente los controles que alteran el formulario y el cambio de factura, o conservar una versión del formulario y limpiar solo si no cambió desde el envío. Definir una política visible para cancelar/cambiar de edición durante una operación en curso.

**Prueba faltante:** intentar agregar/quitar renglones, cambiar cabecera y abrir otra factura durante un POST/PUT pendiente; comprobar que se bloquean o se preservan sin pérdidas. Mantener las pruebas existentes contra doble envío y de recuperación tras error.

### F05 — La cancelación no invalida la carga pendiente de una edición

**Ubicación:** `frontend/src/app.ts:234-247` y `255-267`.

**Disparador reproducido:** abrir la factura #1 para editar; iniciar la carga de #2; mientras su GET está pendiente, pulsar Cancelar edición; completar después el GET de #2.

**Resultado observado:** tras cancelar se muestra “Nueva factura”; al llegar la respuesta tardía reaparece “Editando factura #2 (borrador)” y su renglón.

**Causa:** `limpiarFormulario` resetea el estado, pero no invalida las solicitudes de edición pendientes. `editarFactura` aplica incondicionalmente la respuesta después del `await`.

**Impacto:** una acción de cancelación queda revertida; el formulario vuelve a apuntar a otra factura y puede sobrescribir trabajo iniciado tras cancelar.

**Corrección sugerida:** usar un identificador de sesión de edición que cambie al cancelar/abrir otra factura, comprobarlo antes de aplicar el GET y, si corresponde, abortar la carga anterior. Es un arreglo diferente del bloqueo durante guardado de F04.

**Prueba faltante:** cancelar durante un GET, abrir dos facturas con respuestas invertidas y comenzar un alta después de cancelar; ninguna respuesta antigua debe reemplazar el contexto vigente.

### F06 — TXT tabulado sin tratamiento de separadores en los datos

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/report/ReporteFacturasService.java:167-178`, especialmente 172-174.

**Disparador confirmado:** generar el reporte con una denominación de cliente que contiene tabulación y salto de línea, representada como `A\tB\nC`. Los catálogos no son editables por la API actual: este escenario depende de los datos cargados en la base.

**Resultado:** el valor introduce columnas y filas adicionales en el texto exportado.

**Causa:** los campos se concatenan directamente entre separadores, sin un contrato de escape o normalización.

**Impacto:** al importar el archivo tabulado se desplazan columnas y se fragmentan registros. El archivo deja de representar fielmente una fila por factura.

**Corrección sugerida:** definir un formato de escape compatible con el consumidor del TSV y aplicarlo a todos los textos; si se normalizan caracteres de control, documentar esa transformación. No asumir que las denominaciones carecen de separadores.

**Prueba faltante:** clientes, condiciones IVA y puntos de venta con tabulaciones, CR/LF y comillas; verificar ida y vuelta mediante un lector compatible y un número constante de columnas.

### F07 — Pérdida de caracteres Unicode en PDF

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/report/ReporteFacturasService.java:60-62` y 79-81.

**Disparador confirmado:** generar un PDF con un cliente denominado `李华`.

**Resultado:** los caracteres del nombre se pierden en el PDF generado.

**Causa:** las fuentes Helvetica usadas con la codificación WinAnsi no cubren ese repertorio. El resto de las capas acepta el texto, pero la exportación no preserva esos caracteres.

**Impacto:** nombres e identificadores textuales pueden quedar incompletos o vacíos en un documento exportado.

**Corrección sugerida:** incorporar una fuente con cobertura adecuada, incrustarla y configurar codificación Unicode. Definir el repertorio admitido y la respuesta cuando falten glifos.

**Prueba faltante:** reporte con caracteres latinos acentuados y nombres de otros alfabetos; comparar el texto recuperado del PDF y verificar visualmente la presencia de glifos.

### F08 — El contrato ProblemDetail no cubre JSON malformado

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/exception/ApiExceptionHandler.java:21-43`; consumidor `frontend/src/services/FacturaService.ts:18-24`.

**Disparador confirmado:** enviar JSON malformado a un endpoint que recibe un cuerpo JSON.

**Resultado:** HTTP 400 con el esquema predeterminado de errores, en lugar del ProblemDetail uniforme documentado. El código de estado es correcto; la anomalía está en el cuerpo del contrato.

**Causa:** el advice cubre validación de argumentos y ciertos errores de parámetros, pero no la excepción de lectura/deserialización del cuerpo.

**Impacto:** los consumidores que esperan `title` y `detail` no reciben el formato anunciado; el frontend puede construir un mensaje con propiedades ausentes en lugar de una explicación útil.

**Corrección sugerida:** normalizar los errores de lectura del cuerpo con el mismo formato, sin exponer detalles internos del parser; reforzar el fallback del cliente cuando una respuesta no cumpla el esquema esperado.

**Prueba faltante:** JSON truncado, tipo incompatible y cuerpo vacío cuando sea obligatorio; verificar status, media type y campos ProblemDetail. Probar también la presentación frontend de un error no estándar.

### F09 — Pérdida de centavos con magnitudes extremas aceptadas

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/model/FacturaVenta.java:107-112`, campos monetarios desde 52; `src/main/java/ar/edu/utn/frm/facturacion/model/FacturaVentaDetalle.java:39`.

**Disparador confirmado:** POST con el artículo de lista #1 (precio 950000), cantidad 10000000000, y el de lista #5 (precio 6400), cantidad 0.0001. Con IVA, los subtotales resultan 11495000000000000,00 y 0,71.

**Resultado:** la factura se acepta, pero el total recuperado es 11495000000000000,00: pierde los 0,71 del segundo renglón. Se trata de magnitudes extremas; no se observó este fallo con importes ordinarios.

**Causa:** el cálculo suma con `BigDecimal`, pero convierte el resultado a `double`. La comprobación de finitud evita infinito, pero no detecta la pérdida de resolución decimal de un número todavía finito.

**Impacto:** el total de una entrada aceptada puede no coincidir con la suma decimal de sus renglones. La corrección anterior de overflow no cubre esta pérdida de precisión.

**Corrección sugerida:** imponer límites de negocio compatibles con la precisión garantizada, o preservar `BigDecimal`/decimal exacto en todo el almacenamiento y contrato monetario. No basta con `isFinite`.

**Prueba faltante:** reproducir esa suma y valores alrededor del límite admitido; verificar igualdad exacta a centavos entre total y suma de renglones, o rechazo explícito y sin persistencia cuando excedan el límite.

## Observaciones adicionales

### O01 — Encabezados ausentes en páginas posteriores del PDF (P3)

**Ubicación:** `src/main/java/ar/edu/utn/frm/facturacion/report/ReporteFacturasService.java:68-76`.

**Disparador y evidencia:** un reporte de 100 filas produjo cuatro páginas; desde la segunda no se repiten los encabezados.

**Causa:** se añaden las celdas iniciales pero no se configura la fila como encabezado repetible de la tabla.

**Impacto:** las páginas separadas pierden contexto de columnas, especialmente al imprimir o compartir páginas individuales.

**Corrección sugerida:** configurar la repetición de la fila de encabezado. **Prueba faltante:** generar un reporte multipágina y verificar encabezados en cada página que contiene datos.

### O02 — Mensajes dinámicos sin anuncio accesible (P3)

**Ubicación:** `frontend/index.html:17`; `frontend/src/app.ts:62-68`.

**Disparador:** cualquier éxito o error comunicado mediante `#mensaje`.

**Evidencia y causa:** el párrafo se actualiza y oculta a los seis segundos, pero no tiene `role` ni `aria-live`, y no se mueve el foco a él. Se confirma la ausencia del mecanismo en el código; no se realizó prueba con lector de pantalla.

**Impacto:** una persona que usa tecnología asistiva puede no recibir la notificación del resultado de la operación.

**Corrección sugerida:** utilizar una región de estado/alerta adecuada y una política de permanencia para errores que requieran intervención. **Prueba faltante:** validar anuncios y foco con teclado y lector de pantalla; una prueba DOM sola no sustituye esa comprobación.

### D01 — `VERIFICACION.md` desactualizado (P3)

**Ubicación:** `VERIFICACION.md:32` y `39`.

El documento conserva el desbordamiento monetario como pendiente de ejecución final y declara pendientes la suite de 33 pruebas y el empaquetado. La revisión actual obtuvo 33 pruebas aprobadas y `bootJar` correcto. Esto no invalida su evidencia histórica de TDD, pero sí hace que su sección de estado no represente el resultado actual.

**Corrección sugerida:** añadir un cierre fechado o actualizar el estado con evidencia de esta ejecución. Mantener explícitos PostgreSQL y navegador como no verificados. No se modificó ese documento en esta auditoría.

## Aspectos comprobados que no deben volver a reportarse como defectos abiertos

- Las pruebas de frontend actuales verifican que descripciones y datos de API se renderizan como texto y no insertan HTML ejecutable.
- Cancelar la solicitud de motivo de anulación no produce PATCH.
- El envío repetido mientras se guarda no duplica el POST; un error libera el botón para reintentar. Esto no resuelve F04, que trata cambios del contenido durante la espera.
- Se verifican límites de cantidad/bonificación y representación de monedas conocidas y desconocidas.
- La suite backend actual incluye correcciones de concurrencia, validación de elementos nulos, cálculo decimal, monedas, totales por estado y moneda y desbordamiento monetario. Su aprobación acredita esos casos, no todos los escenarios posibles ni el funcionamiento en PostgreSQL.

## Límites académicos documentados y riesgos para otro despliegue

Estos puntos no se presentan como regresiones nuevas del trabajo práctico:

- `X-Usuario` identifica auditoría, pero no autentica ni autoriza; no protege operaciones ante usuarios no confiables.
- El CAE es simulado: no demuestra autorización fiscal real.
- H2 en memoria pierde datos al terminar el proceso, tal como se advierte en README.
- Autenticación, integración fiscal, migraciones de base de datos y límites de consultas/exportaciones quedan fuera de esta demostración.
- Aunque los cálculos usan `BigDecimal`, API y entidades conservan campos `double`; no se hizo una migración integral a almacenamiento decimal exacto.

Antes de un despliegue con datos reales correspondería revisar esos requisitos y ejecutar pruebas específicas de persistencia, seguridad, rendimiento y recuperación. Esta auditoría no certifica preparación para producción ni cumplimiento fiscal.

Como mejora menor del contrato, se observaron filtros `montoMinimo=NaN`, `Infinity` y rangos de fechas invertidos que devuelven 200 con lista vacía (`FacturaRestController.java:44-48`, `82-87`). Conviene definir validación y respuesta 400 para entradas incoherentes; no se presenta como una caída ni se equipara en gravedad a los defectos principales.

## Orden de corrección propuesto

1. Corregir F01, F02 y F09 con pruebas HTTP/persistencia: entradas válidas frente al almacenamiento, auditoría completa del agregado y límites de precisión monetaria.
2. Corregir F03, F04 y F05 con pruebas de promesas diferidas que obliguen a resolver operaciones en distintos órdenes.
3. Corregir preservación de datos de exportación (F06 y F07) y contrato de errores (F08).
4. Mejorar encabezados PDF, avisos accesibles y actualizar la documentación de verificación.
5. Ejecutar después la suite en PostgreSQL y validar flujos de usuario en navegador real. Conservar los límites de esta auditoría hasta contar con esa evidencia.

Cada corrección debe empezar por una prueba de regresión que falle con el caso descrito y pasar después la suite pertinente. Este informe propone acciones; no las implementa ni afirma que los hallazgos estén resueltos.
