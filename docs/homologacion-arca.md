# Integración con ARCA en homologación

## Qué demuestra la defensa

La interfaz y el PDF son propios de este sistema. ARCA autoriza el comprobante mediante WSFEv1 y devuelve el CAE y su vencimiento. La homologación es un entorno de prueba: un CAE obtenido allí proviene del servicio de ARCA, pero **no tiene validez fiscal de producción**. No se debe presentar como autorizado ningún comprobante hasta comprobar una respuesta aprobada de WSFEv1.

## Preparación manual de las credenciales del grupo

1. Una persona del grupo con CUIT y Clave Fiscal entra al [portal de ARCA](https://auth.afip.gob.ar/) y adhiere **WSASS / Autoservicio de Acceso a APIs de Homologación** desde el Administrador de Relaciones de Clave Fiscal. El certificado debe ser propio de la persona autenticada.
2. En su computadora genera una clave privada RSA de al menos 2048 bits y un CSR. El `serialNumber` del CSR lleva `CUIT ` seguido de los 11 dígitos de la CUIT con la que se ingresó a WSASS. Seguir la [guía oficial para el CSR](https://www.afip.gob.ar/ws/WSASS/html/generarcsr.html).
3. En WSASS abre **Nuevo Certificado**, pega el CSR y guarda el certificado PEM que devuelve ARCA. Conserva la clave privada correspondiente; el certificado por sí solo no permite firmar el pedido de autenticación. [Manual de WSASS](https://www.afip.gob.ar/ws/WSASS/html/crearcertificado.html).
4. En **Crear autorización a servicio**, selecciona el alias del certificado, la **misma CUIT titular** y el servicio **Facturación Electrónica**. Esta versión de la aplicación exige que la CUIT emisora y la del certificado coincidan; no implementa representación delegada de otra CUIT. Solicita a WSAA un ticket para el servicio `wsfe`. [Autorización en WSASS](https://www.afip.gob.ar/ws/WSASS/html/crearautorizacion.html) · [Manual de WSFEv1](https://ftp.afip.gob.ar/ws/documentacion/ws-factura-electronica.asp).
5. Verifica que la CUIT representada tenga un punto de venta de Web Services apto para el ensayo. El número de punto de venta que use el sistema debe corresponder al habilitado; no alcanza con que exista un punto de venta de otra modalidad. [Puntos de venta de factura electrónica](https://ftp.afip.gob.ar/fe/emision-autorizacion/solicitud-autorizacion.asp).

En esta computadora Windows está disponible OpenSSL con Git. Para el paso 2 se puede ejecutar en **PowerShell** lo siguiente. El comando solicita la CUIT localmente y deja la clave y el CSR fuera del repositorio:

```powershell
$opensslArca = 'C:\Program Files\Git\usr\bin\openssl.exe'
$carpetaArca = Join-Path $env:USERPROFILE 'arca-homologacion'
$cuitArca = Read-Host 'CUIT propia, 11 digitos sin guiones'
if ($cuitArca -notmatch '^\d{11}$') { throw 'La CUIT debe tener 11 digitos' }
New-Item -ItemType Directory -Path $carpetaArca -Force | Out-Null
$claveArca = Join-Path $carpetaArca 'privada.key'
$pedidoArca = Join-Path $carpetaArca 'pedido.csr'
& $opensslArca genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $claveArca
& $opensslArca req -new -key $claveArca -subj "/C=AR/O=TP_Facturacion/CN=Facturacion_Homologacion/serialNumber=CUIT $cuitArca" -out $pedidoArca
```

Abrir **solo** `pedido.csr` para copiar su texto en WSASS. No enviar `privada.key` a nadie. El certificado PEM que devuelve WSASS se guarda en esa misma carpeta como `certificado-propio.pem`.

Las claves privadas, archivos `.p12` y contraseñas se guardan **fuera del repositorio**. El [certificado público de homologación](certificados/homologacion-emisor.pem) se versiona por decisión de su titular para que el docente pueda inspeccionar la identidad de homologación; incluye la CUIT del emisor y no sirve para firmar sin la clave privada. Si una clave privada se compartió por chat, su titular debe quitar la autorización asociada en WSASS y crear un par de claves y certificado nuevos. [Cómo eliminar una autorización](https://www.afip.gob.ar/ws/WSASS/html/eliminarautorizacion.html).

## Configuración local de la aplicación

El backend lee las siguientes variables de entorno; no las escribe en el repositorio:

| Variable | Valor |
|---|---|
| `ARCA_CERTIFICATE_PATH` | Ruta absoluta al certificado PEM nuevo de WSASS. |
| `ARCA_PRIVATE_KEY_PATH` | Ruta absoluta a la clave privada PKCS#8 correspondiente. |
| `ARCA_CUIT` | CUIT emisora autorizada en WSASS; debe coincidir con la CUIT del certificado, 11 dígitos sin guiones. |
| `ARCA_EMISOR_CONDICION_IVA` | Código de condición IVA real del emisor: `1` Responsable Inscripto, `4` Exento o `6` Monotributista. Sin este valor, la emisión se bloquea. |

La aplicación determina automáticamente el tipo fiscal según el emisor y el receptor: Responsable Inscripto a otro inscripto o monotributista, **A**; Responsable Inscripto a consumidor final o exento, **B**; emisor monotributista o exento, **C**. En C el IVA discriminado es cero y no se informa el bloque de alícuotas. Esta implementación cubre operaciones en pesos. La CUIT, la condición IVA real y el punto de venta deben corresponder a la autorización de homologación. La aplicación arranca sin credenciales para consultar catálogos y preparar borradores, pero no emite sin ellas. [Clases de comprobante según ARCA](https://www.arca.gob.ar/facturacion/regimen-general/comprobantes.asp).

Ejemplo de inicio en PowerShell, después de guardar el certificado PEM en la carpeta indicada y reemplazar `TU_CUIT_11_DIGITOS` por la CUIT autorizada:

```powershell
$env:ARCA_CERTIFICATE_PATH = Join-Path $env:USERPROFILE 'arca-homologacion\certificado-propio.pem'
$env:ARCA_PRIVATE_KEY_PATH = Join-Path $env:USERPROFILE 'arca-homologacion\privada.key'
$env:ARCA_CUIT = 'TU_CUIT_11_DIGITOS'
$env:ARCA_EMISOR_CONDICION_IVA = '6' # únicamente si el emisor es monotributista
.\gradlew.bat bootRun
```

En otra terminal, dentro de `frontend/`, ejecutar `npm ci` y luego `npm run dev`. La interfaz se abre en `http://localhost:5173` y el backend en `http://localhost:8080`. Para conservar reservas fiscales y resultados inciertos entre reinicios, usar **PostgreSQL persistente**; H2, la base predeterminada, es en memoria y se borra al detener el backend.

Si ya existe una base PostgreSQL creada con la versión anterior, su restricción única de numeración puede impedir series A/B/C separadas. Revisar y ejecutar la migración [Migrate-ArcaFiscalSeries.sql](../scripts/Migrate-ArcaFiscalSeries.sql) sobre una copia de esa base antes de usarla; esta migración aún no se verificó en PostgreSQL real. En una base H2 nueva se crea el esquema actualizado directamente.

## Flujo técnico para explicar

1. El usuario guarda una factura como borrador. Su ID de base de datos solo identifica ese registro local.
2. Al pedir la emisión, el backend valida los datos y obtiene de WSAA un `token` y un `sign` mediante un TRA firmado en CMS con el certificado y la clave privada. El ticket se reutiliza mientras sea válido.
3. WSFEv1 devuelve el último número autorizado para el punto de venta y tipo de comprobante. El sistema solicita el siguiente número con `FECAESolicitar`.
4. Solo una respuesta aprobada de ARCA permite marcar la factura como emitida y guardar el número fiscal, el CAE y el vencimiento. Una respuesta rechazada conserva la factura pendiente y muestra el motivo.
5. Si la conexión se interrumpe después de enviar la solicitud, antes de reintentar hay que consultar el comprobante mediante `FECompConsultar` para evitar duplicar una emisión.
6. El botón **Imprimir** produce un PDF local a partir de los datos guardados; no es un PDF devuelto por ARCA.

Una factura con CAE no se anula cambiando un estado en la base local. ARCA exige emitir la nota de crédito o débito que corresponda. Esta versión no implementa ese trámite; por eso la acción local **Anular** no debe aplicarse a comprobantes autorizados. [Respuesta oficial de ARCA](https://www.afip.gob.ar/genericos/guiavirtual/consultas_detalle.aspx?id=6490326).

El entorno de homologación usa WSAA en `https://wsaahomo.afip.gov.ar/ws/services/LoginCms` y WSFEv1 en `https://wswhomo.afip.gov.ar/wsfev1/service.asmx`. [Documentación oficial de WSAA](https://afip.gob.ar/ws/documentacion/wsaa.asp) · [Servicio oficial WSFEv1](https://wswhomo.afip.gob.ar/wsfev1/service.asmx).

## Estado de la comprobación

El 23/11/2026 se ejecutó una emisión de prueba en homologación con el certificado nuevo y autorización WSFE del emisor. ARCA aprobó una **factura C** (tipo fiscal 11): la API respondió HTTP 200, el registro quedó `EMITIDA` y el detalle guardó número fiscal, CAE y vencimiento del 03/12/2026. No se publica el valor del CAE ni la clave privada. El certificado público que el titular autorizó a incluir está en [homologacion-emisor.pem](certificados/homologacion-emisor.pem). Esta prueba acredita la conexión de homologación; no es una autorización fiscal de producción.

La suite local del 23/11/2026 pasó 98/98 pruebas de backend con H2 y 57/57 de frontend con jsdom. Las pruebas no sustituyen la autorización externa descrita arriba. La base H2 predeterminada es volátil: al reiniciar el backend desaparece el registro local de esa emisión, aunque la autorización de prueba permanece en ARCA.
