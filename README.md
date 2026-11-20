# Sistema de Facturación ARCA

Trabajo práctico integrador **individual** de Desarrollo de Software (UTN FRM, 2026): sistema de facturación con backend Java/Spring Boot y frontend HTML/CSS/TypeScript. Permite crear y editar borradores, emitir y anular facturas, consultar por filtros y exportar reportes.

> **Declaración de origen y uso de herramientas.** La base de este repositorio proviene del **kit de nivelación publicado por la cátedra** (`arca-spirng-main`), incorporado completo en el commit inicial. Sobre esa base se agregaron pruebas de persistencia y se corrigieron problemas de interacción del frontend. Las correcciones posteriores solicitadas por el profesor se documentan en [correcciones-profesor.md](docs/correcciones-profesor.md). Para los exploradores y la impresión se usó `TP 6 Grupal  3.zip` como **referencia de patrón de interfaz**, sin trasladar integración fiscal real. Se utilizaron asistentes de IA (Claude y Codex) para preparar, revisar y verificar la entrega. Los resultados citados distinguen la evidencia propia de la heredada del kit.

El backend y el frontend se ejecutan por separado. El **CAE es simulado**: no se consultan servicios fiscales de ARCA. El encabezado **`X-Usuario` identifica auditoría, sin autenticar** a quien realiza la solicitud.

## Contenido

- [Estructura del proyecto](#estructura-del-proyecto)
- [Arquitectura y modelo](#arquitectura-y-modelo)
- [Tecnologías y requisitos](#tecnologías-y-requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Configuración y PostgreSQL](#configuración-y-postgresql)
- [API y reglas de negocio](#api-y-reglas-de-negocio)
- [Reportes](#reportes)
- [Pruebas y verificación](#pruebas-y-verificación)
- [Correcciones del profesor](docs/correcciones-profesor.md)
- [Origen del código y declaración de uso](#origen-del-código-y-declaración-de-uso)
- [Problemas frecuentes y límites](#problemas-frecuentes-y-límites)

## Estructura del proyecto

El árbol muestra las fuentes, la configuración y los documentos principales; omite dependencias y resultados de compilación.

```text
facturacion-arca/
├── README.md
├── build.gradle                         # Dependencias y tareas del backend
├── settings.gradle                      # Nombre del proyecto Gradle
├── gradlew / gradlew.bat                 # Ejecutables del wrapper
├── gradle/wrapper/                       # Gradle 8.10.2
├── docker-compose.yml                   # PostgreSQL 16; solo la base de datos
├── requests.http                        # Solicitudes manuales de la API
├── src/
│   ├── main/
│   │   ├── java/ar/edu/utn/frm/facturacion/
│   │   │   ├── FacturacionApplication.java
│   │   │   ├── config/                  # CORS, auditoría, propiedades y datos iniciales
│   │   │   ├── controller/              # API de facturas y catálogos
│   │   │   ├── dto/                     # Entrada, salida, filtros y opciones
│   │   │   ├── exception/               # Excepciones y respuestas ProblemDetail
│   │   │   ├── model/                   # Entidades, superclases y estado de factura
│   │   │   ├── repository/              # Spring Data JPA y consulta JPQL
│   │   │   ├── service/                 # Operaciones y reglas transaccionales
│   │   │   └── report/                  # Exportación PDF, XLSX y TXT
│   │   └── resources/
│   │       ├── application.yml          # H2 por defecto y perfil postgres
│   │       └── fonts/                   # Fuente del PDF y licencia
│   └── test/
│       ├── java/ar/edu/utn/frm/facturacion/
│       │                               # Pruebas de dominio, HTTP y reportes
│       └── resources/application-test.yml
├── frontend/
│   ├── index.html                      # Página y URL base de la API
│   ├── styles.css
│   ├── package.json / package-lock.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── app.ts                      # DOM, formularios y estado de la interfaz
│   │   ├── models/factura.dto.ts        # Tipos del contrato HTTP
│   │   └── services/FacturaService.ts   # Cliente fetch y errores de la API
│   └── test/app.test.mjs                # Regresiones de interfaz y cliente HTTP
├── scripts/Verify-Postgres.ps1          # Verificación en una base temporal aislada
└── docs/
    ├── etapa-1.md … etapa-5.md          # Informes de cada entrega (decisiones, dificultades, evidencia)
    └── kit-catedra/                     # Documentos que acompañaban al kit, sin modificar
        ├── VERIFICACION.md
        ├── AUDITORIA-2026-10-30.md
        └── BACKEND-FIXES.md / FRONTEND-FIXES.md / REPORT-FIXES.md
```

`build/` contiene los resultados de Gradle; `.gradle/`, su caché local. `frontend/dist/` contiene JavaScript compilado y `frontend/node_modules/`, dependencias npm. Estas rutas están excluidas en `.gitignore`. Los documentos de `docs/kit-catedra/` se conservan tal como llegaron con el kit: las rutas `tmp/` y `build/test-results/` que mencionan (capturas y resultados de las verificaciones del autor del kit) **no forman parte de este repositorio**.

## Arquitectura y modelo

El backend se organiza por capas dentro de una sola aplicación. La interfaz consume JSON y archivos por HTTP:

```text
frontend/index.html + src/app.ts
              │
              ▼
frontend/src/services/FacturaService.ts
              │ HTTP /api
              ▼
controller → service → repository → H2 / PostgreSQL
                 │
                 └── model: relaciones y cálculo de importes

controller → report: PDF / XLSX / TXT a partir de DTOs
```

| Paquete Java | Responsabilidad y clases principales |
|---|---|
| `config` | `FacturacionProperties` agrupa propiedades; `WebConfig` configura CORS e interceptor; `UsuarioActualInterceptor` resuelve auditoría; `DataLoader` carga ejemplos. |
| `controller` | `FacturaRestController` recibe operaciones y descargas; `CatalogoRestController` ofrece opciones de los formularios. |
| `dto` | Contratos independientes de las entidades: requests de factura/detalle/estado; respuestas de reporte y detalle; filtros y opciones. |
| `service` | `FacturaService` aplica reglas, delimita transacciones y convierte entidades a DTOs. `CatalogoService` consulta catálogos. |
| `repository` | Interfaces Spring Data y fragmento `FacturaReporteRepository`, implementado con JPQL dinámico en `FacturaReporteRepositoryImpl`. |
| `model` | Entidades persistentes, importes, auditoría compartida y transiciones de `EstadoFactura`. |
| `report` | `ReporteFacturasService` construye los archivos descargables en memoria. |
| `exception` | `ApiExceptionHandler` traduce validaciones, recursos inexistentes y reglas de negocio a errores HTTP. |

Las transacciones se delimitan en los servicios. Con `spring.jpa.open-in-view=false`, los controladores trabajan con DTOs y no dependen de una sesión de persistencia abierta durante la presentación.

### Entidades y relaciones

Hay **14 clases `@Entity` y 2 superclases `@MappedSuperclass`**. Las superclases no son entidades adicionales.

| Área | Entidades |
|---|---|
| Facturación | `FacturaVenta`, `FacturaVentaDetalle`, `PuntoVenta` |
| Clientes | `Cliente`, `Contacto`, `Domicilio` |
| Artículos y precios | `Articulo`, `Rubro`, `Marca`, `ListaPrecio`, `ListaPrecioArticulo` |
| Catálogos fiscales y monetarios | `CondicionIva`, `TipoMoneda` |
| Auditoría | `Usuario` |

`EntityId` define la identidad compartida y `AuditoriaApp` aporta campos y callbacks de auditoría. `EstadoFactura` es un enum; `UsuarioActual` mantiene el contexto de auditoría mediante `ThreadLocal`.

- `FacturaVenta` referencia cliente opcional, condición de IVA, moneda y punto de venta. Administra sus detalles con cascada y `orphanRemoval`.
- `FacturaVentaDetalle` pertenece a una factura y referencia `ListaPrecioArticulo`, del que se obtiene el precio.
- `ListaPrecioArticulo` vincula artículo y lista de precios. El artículo referencia rubro y marca y define su alícuota de IVA.
- `Cliente` administra contacto y domicilio mediante asociaciones uno a uno con cascada y eliminación de huérfanos.

## Tecnologías y requisitos

| Componente | Configuración |
|---|---|
| Java | Código fuente y destino Java 17; usar JDK 17 o 21 para los comandos siguientes. |
| Spring Boot | `3.3.5`, con Web, Data JPA y Validation. |
| Gradle | Wrapper `8.10.2`; no requiere instalación global. |
| Persistencia | Jakarta Persistence/Hibernate; H2 o PostgreSQL 16. |
| Anotaciones auxiliares | Lombok, con procesamiento de anotaciones. |
| PDF / Excel | OpenPDF `2.0.3` y Apache POI `5.3.0`. |
| Frontend | HTML5, CSS y TypeScript, sin framework; módulos ES2020. |
| Dependencias npm | TypeScript declarado `^5.6.3`, resuelto `5.9.3` en el lockfile; `http-server` y jsdom `26.1.0`. |
| Pruebas | Spring Boot Test/JUnit Platform y MockMvc; Node.js y jsdom. |

Se necesita Node.js con npm para el frontend; usar Node.js 18.13 o superior para los comandos y pruebas documentados, que utilizan módulos VM experimentales. Docker con Compose es opcional y se utiliza para PostgreSQL. La primera ejecución del wrapper y `npm ci` requieren acceso a sus repositorios de dependencias.

## Puesta en marcha

Ejecutar desde la raíz del proyecto, salvo donde se indique otra carpeta.

### 1. Backend con H2

H2 está incluida como dependencia y funciona **en memoria, dentro del proceso del backend**. No hay que instalarla, iniciar un servicio de base de datos ni levantar Docker. Al iniciar Spring Boot con la configuración por defecto, se crea la base `facturacion`; Hibernate prepara sus tablas y el cargador incorpora los datos de ejemplo.

Ejecutar uno de estos comandos desde la raíz del proyecto y mantener la terminal abierta:

En PowerShell:

```powershell
.\gradlew.bat bootRun
```

En Linux/macOS:

```bash
./gradlew bootRun
```

El backend escucha en `http://localhost:8080`. Comprobarlo abriendo `http://localhost:8080/api/facturas`. La raíz del backend no sirve la interfaz del frontend.

`DataLoader` crea usuarios, catálogos, clientes y seis facturas de ejemplo: tres emitidas, una anulada y dos borradores. Omite toda la carga si ya existe al menos un usuario; no completa automáticamente catálogos parcialmente cargados. Está deshabilitado en el perfil `test`.

#### Acceder a la consola de H2

1. Esperar a que el backend termine de iniciar y muestre `Started FacturacionApplication` en la terminal.
2. Abrir [la consola H2](http://localhost:8080/h2-console) en el navegador.
3. Completar los datos de conexión siguientes y pulsar **Connect**:

| Campo | Valor |
|---|---|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:facturacion` |
| User Name | `sa` |
| Password | Dejar vacío |

La URL configurada en Spring incluye `;DB_CLOSE_DELAY=-1`; la URL anterior conecta a esa misma base desde la consola integrada. Reemplazar cualquier URL de ejemplo que muestre la consola, como `jdbc:h2:~/test`.

Después de conectar, las tablas aparecen en el panel izquierdo. Para comprobarlas, ejecutar:

```sql
SHOW TABLES;
```

H2 es la opción por defecto: no activar el perfil `postgres` para seguir estos pasos. Si antes se definieron `SPRING_PROFILES_ACTIVE` o variables `SPRING_DATASOURCE_*`, quitarlas del entorno de esa terminal o de la configuración del IDE antes de iniciar, para utilizar los valores H2 de `application.yml`. El perfil `postgres` deshabilita la consola H2.

Para detener la aplicación y la base, presionar `Ctrl+C` en la terminal del backend. **Los datos se pierden al finalizar el proceso Java**; al volver a iniciar con H2 se crea una base nueva y se cargan nuevamente los ejemplos. Cerrar solo la pestaña de la consola no detiene la base.

### 2. Frontend en otra terminal

```text
cd frontend
npm ci
npm run dev
```

Abrir `http://localhost:5173`. `npm run dev` compila TypeScript una vez e inicia `http-server`; no recompila automáticamente. Para recompilar al guardar, dejar `npm run watch` en una terminal adicional dentro de `frontend/`.

`index.html` carga `dist/app.js` y define el backend en:

```html
<meta name="api-base-url" content="http://localhost:8080/api">
```

### 3. Recorrido de uso

1. Consultar facturas, aplicar filtros con fecha visible **DD/MM/AAAA** y recorrer páginas de diez resultados.
2. Crear una factura: buscar y seleccionar el cliente en el explorador (o elegir Consumidor Final), seleccionar una lista de precios, buscar el artículo dentro de esa lista y agregar al menos un renglón.
3. Editar el borrador y comprobar los importes calculados por el servidor.
4. Emitirlo para asignar número y CAE simulado.
5. Pulsar **Imprimir** para abrir el PDF de esa factura, con CAE simulado, vencimiento y páginas numeradas. También se puede descargar el reporte general o anular la factura emitida.

Para recorrer la API sin interfaz, utilizar [requests.http](requests.http) con un cliente compatible. Sus IDs son ejemplos: reemplazarlos por los obtenidos de los catálogos y de la respuesta de creación si la base fue modificada.

### 4. Ejecutable del backend

En PowerShell:

```powershell
.\gradlew.bat bootJar
java -jar build/libs/facturacion-arca-1.0.0.jar
```

En Linux/macOS, reemplazar el primer comando por `./gradlew bootJar`. El JAR no incorpora el frontend, que se compila y sirve por separado.

## Configuración y PostgreSQL

[application.yml](src/main/resources/application.yml) contiene la configuración por defecto y un segundo documento YAML que se activa con el perfil `postgres`.

| Propiedad | Valor o comportamiento por defecto |
|---|---|
| `server.port` | `8080` |
| `spring.datasource.url` | `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1` |
| `spring.jpa.hibernate.ddl-auto` | `update` en desarrollo, tanto H2 como PostgreSQL |
| `spring.jpa.open-in-view` | `false` |
| `spring.h2.console.enabled` | `true`; `false` con `postgres` |
| `facturacion.iva-por-defecto` | `21.0`, si el artículo no define alícuota |
| `facturacion.usuario-por-defecto` | `sistema` |
| `facturacion.cors.allowed-origins` | `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` y `http://127.0.0.1:3000` |

Para iniciar PostgreSQL desde la raíz:

```text
docker compose up -d db
```

Luego iniciar el backend en PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=postgres"
```

O en Linux/macOS:

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

Compose utiliza `postgres:16`, puerto `5432`, base `facturacion` y usuario/contraseña de desarrollo `facturacion`. El volumen `pgdata` conserva los datos entre reinicios. `docker compose stop db` detiene el servicio sin eliminar el volumen. Compose no inicia el backend ni el frontend.

Para otra instancia, ajustar `spring.datasource.*` o definir `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y, cuando corresponda, `SPRING_DATASOURCE_DRIVER_CLASS_NAME` en el entorno del proceso. `SPRING_PROFILES_ACTIVE=postgres` también selecciona el perfil. La aplicación no incluye un cargador propio de archivos `.env`.

Si cambia el puerto del backend, actualizar el `meta` del frontend. Si cambia el origen de la interfaz, ajustar la lista CORS.

## API y reglas de negocio

Base local: `http://localhost:8080/api`. Las escrituras reciben JSON. El reporte general se descarga con `Content-Disposition: attachment`; el comprobante individual se abre en línea con `Content-Disposition: inline`.

### Facturas

| Método | Ruta | Resultado satisfactorio |
|---|---|---|
| `GET` | `/api/facturas` | `200`, listado de `FacturaReporteDTO`. |
| `GET` | `/api/facturas/{id}` | `200`, cabecera y renglones como `FacturaDetalladaDTO`. |
| `GET` | `/api/facturas/{id}/pdf` | `200`, PDF imprimible de una factura emitida o anulada con CAE; `404` si no existe y `409` si es borrador. |
| `POST` | `/api/facturas` | `201`, `Location` y `FacturaReporteDTO`; crea un borrador. |
| `PUT` | `/api/facturas/{id}` | `200`, `FacturaReporteDTO`; reemplaza cabecera y detalles del borrador. |
| `PATCH` | `/api/facturas/{id}/estado` | `200`, `FacturaReporteDTO`; emite o anula. |
| `DELETE` | `/api/facturas/{id}` | `204`; elimina físicamente un borrador y sus detalles. |
| `GET` | `/api/facturas/reporte` | Archivo; `formato=pdf` por defecto, `xlsx` o `txt`, con alias `excel` y `tsv`. |

Listado y reporte comparten los filtros opcionales `fechaDesde`, `fechaHasta` (formato `yyyy-MM-dd`, límites inclusivos), `estado` y `montoMinimo` (total mínimo inclusivo y finito). Se rechazan fechas invertidas y estados inválidos. El estado se normaliza a mayúsculas y se recortan espacios.

La consulta JPQL proyecta DTOs con `SELECT new`, utiliza parámetros y cuenta renglones con `LEFT JOIN`. Ordena por fecha de emisión descendente y luego ID descendente. La interfaz pagina el listado de facturas de diez en diez; la API devuelve la lista completa y no ofrece paginación de servidor ni filtro por punto de venta. Las fechas visibles se escriben y muestran como **DD/MM/AAAA** (día/mes/año); las fechas JSON y los parámetros de la API conservan **`yyyy-MM-dd`**.

### Contrato de escritura y catálogos

`FacturaRequestDTO` requiere `condicionIvaId`, `puntoVentaId`, `tipoMonedaId` y una lista `detalles` no vacía y sin elementos nulos. `clienteId` es opcional (`null` representa consumidor final); `fechaEmision` omitida toma la fecha actual; `observaciones` admite hasta 500 caracteres.

Cada `FacturaDetalleRequestDTO` requiere `listaPrecioArticuloId` y `cantidad` mayor que cero. Admite `porcentajeBonificacion` entre 0 y 100 y `descripcion` de hasta 255 caracteres. Si se omiten, la bonificación es cero y la descripción se toma del artículo. El servidor obtiene el precio de la lista; el request no lo fija.

El cambio de estado recibe, por ejemplo, `{"estado":"EMITIDA"}` o `{"estado":"ANULADA","observaciones":"Motivo"}`.

Los catálogos se consultan mediante `GET /api/catalogos/` seguido de `puntos-venta`, `clientes`, `condiciones-iva`, `tipos-moneda`, `listas-precio` o `lista-precio-articulos`. `GET /api/catalogos/lista-precio-articulos?listaPrecioId={id}` limita los artículos a una lista; sin parámetro devuelve todos. Las opciones contienen `id` y `etiqueta`; `valor` indica el precio en un artículo, `codigoMoneda` el código en una moneda y `listaPrecioId` identifica la lista de cada artículo. Los demás valores pueden ser nulos. Son consultas de solo lectura, sin CRUD público de catálogos. Los exploradores de clientes y artículos buscan y paginan **en el navegador** las opciones ya recibidas, de diez en diez.

### Estados, importes y auditoría

```text
BORRADOR ──emitir──> EMITIDA ──anular──> ANULADA
    │
    ├── editar cabecera y renglones
    └── eliminar físicamente
```

La emisión exige detalles, asigna el siguiente número del punto de venta y genera un CAE simulado de 14 dígitos con vencimiento a diez días. La anulación registra fecha y baja lógica. No hay regreso a borrador.

Las escrituras son transaccionales. Edición, emisión, anulación y eliminación bloquean la cabecera; emitir también bloquea el punto de venta para coordinar su numeración. La cascada y `orphanRemoval` administran los detalles al reemplazarlos.

Los importes se calculan con `BigDecimal` y redondeo `HALF_UP` a dos decimales. El IVA se calcula sobre el neto anterior a su redondeo; neto e IVA se redondean por separado y se suman. La persistencia mantiene campos `double`, por lo que se comprueba finitud y que la conversión no pierda centavos. Un fallo revierte la transacción. No hay conversión de divisas.

`X-Usuario` selecciona un usuario existente para auditoría. Si falta, está vacío o no existe, el interceptor intenta utilizar `sistema`, configurable como usuario por defecto. Los ejemplos incluyen `sistema` y `acortez`. El contexto se limpia al terminar la solicitud. Además de los callbacks de `AuditoriaApp`, cada `PUT` aceptado marca la modificación de cabecera aunque solo cambien renglones o se reenvíe el mismo contenido.

La interfaz bloquea acciones durante un guardado, conserva datos ante errores y descarta respuestas obsoletas de búsquedas o ediciones. La validación definitiva corresponde al backend.

### Errores HTTP

`ApiExceptionHandler` devuelve `ProblemDetail` con `status`, `title`, `detail` y `timestamp`, además de los campos estándar que complete Spring. La validación agrega un mapa `errores` de campo a mensaje.

| HTTP | Situaciones manejadas |
|---|---|
| `400` | JSON inválido, tipos incompatibles, validación de campos, filtros o formato de exportación inválidos. |
| `404` | Factura o referencia de catálogo inexistente. |
| `409` | Transición no permitida, modificación/eliminación fuera de borrador o importe no representable con la precisión exigida. |

## Reportes

El **reporte general** utiliza los mismos DTOs y filtros del listado y se genera en memoria como respuesta HTTP. El **comprobante individual** usa el detalle de una factura emitida o anulada, incluye datos del cliente, renglones, importes, número, CAE simulado y vencimiento, y marca cada página `Página X de Y`. Ambos se generan en memoria; no se guardan automáticamente en el servidor.

| Formato | Implementación |
|---|---|
| PDF | OpenPDF, fuente Unicode incrustada y cabecera de tabla repetida por página. |
| XLSX | Apache POI, con fórmulas de suma. |
| TXT/TSV | UTF-8, nueve columnas separadas por tabulaciones y una línea física por factura. |

PDF y XLSX totalizan solo las facturas `EMITIDA`, separadas por moneda. Borradores y anuladas pueden aparecer en el detalle, pero no suman como facturado. El TXT incluye el código de moneda sin ese resumen de totales.

El TXT escapa barra inversa como `\\`, tabulación como `\t`, retorno de carro como `\r` y salto de línea como `\n`. Las comillas permanecen literales. Para leerlo, separar primero filas y columnas y luego decodificar escapes; no es un CSV entrecomillado.

El PDF utiliza Noto Sans CJK SC Regular; fuente y licencia están en [fonts/](src/main/resources/fonts/). Un glifo no soportado provoca HTTP `400` con su código Unicode, en lugar de omitirse. TXT o XLSX permiten conservar ese texto. Un PDF vacío mantiene encabezados e informa que no hay facturas para los filtros.

## Pruebas y verificación

### Backend

Desde la raíz, en PowerShell:

```powershell
.\gradlew.bat test
```

En Linux/macOS:

```bash
./gradlew test
```

La suite original de 76 pruebas (62 del kit y 14 propias de `PersistenciaModeloTest`) cubre persistencia y restricciones, servicios y HTTP, validación, auditoría, concurrencia, cálculo y precisión monetaria, y contenido de PDF/XLSX/TXT. Las correcciones del profesor agregan pruebas del comprobante individual y de los catálogos; sus resultados actuales se registran en [correcciones-profesor.md](docs/correcciones-profesor.md). `DatosDePrueba` construye los fixtures. El perfil `test` usa H2 con `ddl-auto=create-drop`; el informe se genera en `build/reports/tests/test/index.html`.

`scripts/Verify-Postgres.ps1` ejecuta la suite contra un contenedor temporal identificado por su ID completo y comprueba su identidad antes de continuar. Espera `facturacion-arca-verificacion`, imagen `postgres:16`, puerto `127.0.0.1:55432` y base `facturacion_verificacion`. No crea el contenedor y utiliza `--offline`, por lo que necesita dependencias descargadas. La suite `create-drop` no debe apuntar a una base cuyos datos se deban conservar.

### Frontend

Desde `frontend/`:

```text
npm ci
npm test
```

`npm test` compila TypeScript y ejecuta `test/app.test.mjs` con jsdom y módulos VM. La suite actual pasó **47/47** pruebas, incluidas las de exploradores, selección de lista, fechas y acción Imprimir. Prueba DOM y cliente HTTP simulado; no equivale a una sesión completa en un navegador real. `npm run build` comprueba solo la compilación.

### Evidencia de verificación

Los informes de cada etapa ([etapa-1](docs/etapa-1.md) a [etapa-5](docs/etapa-5.md)) registran qué se ejecutó, en qué entorno y con qué resultado. Los documentos que acompañaban al kit ([VERIFICACION.md](docs/kit-catedra/VERIFICACION.md), [AUDITORIA-2026-10-30.md](docs/kit-catedra/AUDITORIA-2026-10-30.md), [backend](docs/kit-catedra/BACKEND-FIXES.md), [frontend](docs/kit-catedra/FRONTEND-FIXES.md) y [reportes](docs/kit-catedra/REPORT-FIXES.md)) son evidencias del autor del kit, fechadas el 30/10/2026: no sustituyen ejecutar las pruebas después de cambiar el código.

[Correcciones solicitadas por el profesor](docs/correcciones-profesor.md) registra los cambios posteriores a la entrega por etapas y sus comprobaciones. El informe de etapa 5 conserva la evidencia histórica de su fecha.

La tabla [docs/correspondencia-consignas.md](docs/correspondencia-consignas.md) indica en qué archivo se cumple cada consigna del TP «CRUD Factura Venta» del Campus (DTO de entrada, servicio transaccional, endpoints, interfaces TypeScript, `FacturaService.ts`, `index.html` y `app.ts`), y enlaza los informes del integrador.

## Origen del código y declaración de uso

**Origen.** El código fuente inicial de backend y frontend, la configuración, `requests.http` y las pruebas heredadas provienen del kit de nivelación de la cátedra (`arca-spirng-main`). Se incorporó completo en el primer commit del repositorio (tag `kit-catedra`). Los cambios posteriores se detallan a continuación.

**Qué se hizo sobre el kit en esta entrega.**

- Reorganización del repositorio: se excluyeron artefactos de compilación, dependencias, archivos temporales y una página de QA que apuntaba a otro puerto; los documentos del kit pasaron a `docs/kit-catedra/`.
- Este README, adaptado (se retiró lo que describía material que no se incluye) y con esta declaración.
- Pruebas propias de persistencia, `PersistenciaModeloTest` (14 pruebas): el kit no probaba restricciones de unicidad y nulidad con datos que las violen, ni que eliminar un borrador conserve los catálogos, ni la clave foránea del detalle. Se comprobó, rompiendo a propósito el código de producción, que estas pruebas fallan cuando el mapeo es incorrecto (detalle en [docs/etapa-1.md](docs/etapa-1.md)).
- Correcciones del frontend: los campos del próximo renglón se asociaron a un formulario separado para que un renglón incompleto no invalide el guardado de una factura que ya tiene detalles; las acciones de emitir, anular y eliminar se bloquean por factura mientras esperan respuesta para evitar peticiones duplicadas. Se agregaron cuatro pruebas de regresión en `frontend/test/app.test.mjs`.
- Correcciones posteriores a la devolución del profesor: exploradores de cliente y artículo con búsqueda y paginación de interfaz; selección explícita de lista de precios; fechas visibles en día/mes/año; PDF individual con CAE simulado, vencimiento y páginas numeradas. `TP 6 Grupal  3.zip` se consultó para el patrón de interacción; el comprobante sigue siendo académico y no invoca servicios fiscales.
- Los informes `docs/etapa-N.md` y la verificación de las cinco etapas.

**Uso de IA.** Se utilizó Claude (Anthropic) para armar el repositorio, ejecutar comprobaciones, escribir las pruebas propias de persistencia y redactar borradores de los informes. Se utilizó Codex (OpenAI) para revisar la entrega, corregir las fallas de interacción, implementar las correcciones pedidas por el profesor, agregar pruebas y actualizar la documentación. La verificación anterior a esta devolución fue **76/76** en backend H2 y **34/34** en frontend jsdom; los resultados posteriores se detallan en [correcciones-profesor.md](docs/correcciones-profesor.md). El código heredado y los cambios propios deben ser comprendidos y explicables por el estudiante en la defensa. Los commits anteriores hechos con asistencia de Claude llevan la línea `Co-Authored-By`.

**Alcance de la verificación.** Las pruebas se ejecutaron con **H2**. PostgreSQL 16 no se probó en este equipo (no hay Docker instalado); el kit documenta una ejecución propia sobre PostgreSQL, pero ese resultado no se reproduce aquí y no debe tomarse como evidencia de este repositorio. El PDF se revisa visualmente según se indica en [docs/etapa-2.md](docs/etapa-2.md).

**Enunciado.** El enunciado vigente es el publicado en el Campus Virtual (curso Desarrollo de Software, sección del TP ARCA). El documento no se incluye en el repositorio.

## Problemas frecuentes y límites

| Síntoma | Comprobación |
|---|---|
| La interfaz carga sin datos | Revisar que `/api/facturas` responda y que el `meta` `api-base-url` apunte al backend correcto. |
| Error CORS | Servir el frontend por HTTP y usar un origen permitido o agregar el origen exacto a la configuración. |
| Los cambios TypeScript no aparecen | Ejecutar `npm run build` o mantener `npm run watch`; `dev` solo compila al iniciar. |
| Gradle no encuentra Java | Comprobar `java -version` y `JAVA_HOME`; utilizar un JDK compatible, por ejemplo 17 o 21. |
| PostgreSQL no conecta | Revisar servicio `db`, disponibilidad del puerto 5432 y perfil activo `postgres`. |
| Faltan datos de ejemplo | El cargador omite toda la carga si ya existe un usuario; no repara datos parciales. |
| PDF rechaza un carácter | Revisar el código Unicode informado y utilizar TXT/XLSX si la fuente no lo soporta. |

Si PostgreSQL rechaza la zona horaria `America/Buenos_Aires` al ejecutar Java en Windows, establecer el identificador aceptado para ese proceso:

```powershell
.\gradlew.bat bootJar
java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar --spring.profiles.active=postgres
```

Esto no cambia la zona horaria del sistema operativo.

La implementación no incluye autenticación/autorización, integración fiscal real, registro de pagos, CRUD de catálogos, paginación **de servidor**, conversión de moneda ni migraciones versionadas. Los campos de cobrado/saldo pertenecen al modelo, pero no hay un flujo HTTP de cobros. El esquema se administra mediante Hibernate (`ddl-auto=update`), los reportes y comprobantes PDF se materializan en memoria y la configuración distribuida es de desarrollo.
