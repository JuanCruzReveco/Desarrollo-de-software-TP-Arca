# Etapa 1 — Persistencia con JPA/Hibernate

**Origen del código:** kit de la cátedra (`arca-spirng-main`), incorporado sin cambios de código en el tag `kit-catedra`. **Aporte propio de esta etapa:** `PersistenciaModeloTest` (14 pruebas) y este informe. **Entorno:** Windows 11, JDK Temurin 17.0.21, Gradle 8.10.2 (wrapper), H2 en memoria. Se usó un asistente de IA (ver README).

## Decisiones y alternativas

- **Superclases mapeadas.** `EntityId` (`Long`, `IDENTITY`) y `AuditoriaApp` son `@MappedSuperclass`: no generan tablas. La alternativa, `@Inheritance` entre entidades, agregaría tabla o discriminador sin ganar nada, porque nunca se consulta una entidad "genérica".
- **`FacturaVenta` como raíz del agregado.** `@OneToMany(mappedBy = "factura", cascade = ALL, orphanRemoval = true)`. Con `mappedBy` el lado propietario es el detalle, que contiene la clave foránea `factura_id`. Sin `mappedBy` Hibernate crearía una tabla intermedia; una prueba lo comprueba.
- **Catálogos sin cascada.** Cliente, punto de venta, moneda, condición de IVA y lista–artículo tienen existencia propia: ninguna operación sobre la factura se propaga hacia ellos. `Cliente` es raíz de otro agregado (contacto y domicilio, `OneToOne` con cascada y huérfanos).
- **`LAZY` en todos los `ManyToOne`** con `open-in-view: false`: lo que necesita la respuesta se resuelve en el servicio.
- **`EstadoFactura` con `EnumType.STRING`**, no ordinal: reordenar el enum no corrompe los datos.
- **Unicidad:** usuario, número de punto de venta, (lista, artículo) y (punto de venta, número de factura). El número es nulo en un borrador, y la base admite varios nulos, por eso conviven varios borradores.
- **Auditoría** con `@PrePersist`/`@PreUpdate`; `fechaAlta` y `usuarioCarga` son `updatable = false`.

## SQL generado (extracto real del arranque, `show-sql: true`)

```sql
insert into factura_venta (cae, ..., cliente_id, condicion_iva_id, estado, ..., punto_venta_id, ..., tipo_moneda_id, ..., id)
    values (?, ..., default)
insert into factura_venta_detalle (cantidad, descripcion, factura_id, importe_iva, importe_neto, importe_subtotal,
                                   lista_precio_articulo_id, porcentaje_bonificacion, precio_unitario, id)
    values (?, ?, ?, ?, ?, ?, ?, ?, ?, default)
```

La cabecera se inserta primero y cada renglón después, con `factura_id` en el propio detalle: la clave foránea está del lado del detalle, como indica `mappedBy`.

## Por qué la factura es raíz y qué queda al eliminar un borrador

Detalles y cabecera se crean, cambian y desaparecen juntos y los renglones no tienen sentido fuera de su factura: por eso se persisten por cascada desde la raíz y no hay repositorio de detalles. Al eliminar un borrador se borran la cabecera y todos sus renglones. **Permanecen** el cliente, el punto de venta, la moneda, la condición de IVA y los artículos de lista de precios.

## Dificultades y resolución

- **El equipo no tenía Java.** Se instaló Temurin 17.0.21 con winget y se fijó `JAVA_HOME`.
- **Las 14 pruebas nuevas pasaron al primer intento**, lo que por sí solo no prueba nada: podrían no estar verificando lo que dicen. Se hizo una **prueba de mutación**: se rompió a propósito el código de producción, una mutación por vez, y se comprobó que falla la prueba correspondiente. Después se restauró el original.

| Mutación en el código de producción | Pruebas que fallaron |
|---|---|
| Quitar `orphanRemoval = true` | `quitarUnDetalleDeLaColeccion…`, `vaciarLaColeccionGestionada…` |
| Quitar `mappedBy = "factura"` | `laClaveForaneaEstaEnElDetalleYNoHayTablaIntermedia` |
| `EnumType.STRING` → `ORDINAL` | `elEstadoSeGuardaComoTextoYNoComoPosicion` |
| Quitar `unique = true` de `Usuario.usuario` | `noSePuedeRepetirElNombreDeUsuario` |

## Evidencia

- `PersistenciaModeloTest`: 14/14. Guardar una factura con dos renglones y releerla tras limpiar el contexto; relación inversa; cliente opcional; borrar un borrador conserva catálogos; huérfanos; vaciar la colección sin reemplazarla; violaciones reales de unicidad (usuario, punto de venta, lista–artículo, número de factura) y de nulidad (sin estado, sin punto de venta).
- `AggregateAuditTest` (kit): 2/2. Suite completa del backend: **76/76** en H2.
- **No verificado aquí:** PostgreSQL (no hay Docker en este equipo). Un resultado en H2 no demuestra el mismo resultado en PostgreSQL.
