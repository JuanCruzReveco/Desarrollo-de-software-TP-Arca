/**
 * Interfaces TypeScript espejo de los DTOs de Spring Boot.
 * Los nombres y tipos de los campos deben coincidir EXACTAMENTE con el JSON del backend.
 *   Long / long  -> number        double -> number
 *   LocalDate    -> string (ISO "yyyy-MM-dd")
 */

export type EstadoFactura = 'BORRADOR' | 'EMITIDA' | 'ANULADA';

/** Espejo de FacturaReporteDTO (respuesta de GET /api/facturas, POST, PUT y PATCH). */
export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number | null;
  fechaEmision: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
  estado: EstadoFactura;
  codigoMoneda?: string;
}

/** Espejo de FacturaDetalleResponseDTO (renglón dentro de FacturaDetalladaDTO). */
export interface FacturaDetalleResponseDTO {
  id: number;
  listaPrecioArticuloId: number;
  articuloCodigo: string;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  porcentajeBonificacion: number;
  importeNeto: number;
  importeIva: number;
  importeSubtotal: number;
}

/** Espejo de FacturaDetalladaDTO (respuesta de GET /api/facturas/{id}). */
export interface FacturaDetalladaDTO {
  id: number;
  numero: number | null;
  fechaEmision: string;
  estado: EstadoFactura;
  clienteId: number | null;
  clienteDenominacion: string;
  condicionIvaId: number;
  condicionIva: string;
  puntoVentaId: number;
  puntoVentaDescripcion: string;
  tipoMonedaId: number;
  tipoMoneda: string;
  importeTotal: number;
  importeCobrado: number;
  importeSaldo: number;
  cae: string | null;
  caeFechaVencimiento: string | null;
  fechaAnulacion: string | null;
  observaciones: string | null;
  detalles: FacturaDetalleResponseDTO[];
}

/** Espejo de FacturaDetalleRequestDTO. */
export interface FacturaDetalleRequestDTO {
  listaPrecioArticuloId: number;
  cantidad: number;
  porcentajeBonificacion?: number;
  descripcion?: string;
}

/** Espejo de FacturaRequestDTO (cuerpo de POST y PUT). */
export interface FacturaRequestDTO {
  clienteId: number | null;
  condicionIvaId: number;
  puntoVentaId: number;
  tipoMonedaId: number;
  fechaEmision?: string;
  observaciones?: string;
  detalles: FacturaDetalleRequestDTO[];
}

/** Espejo de CambioEstadoRequestDTO (cuerpo de PATCH /api/facturas/{id}/estado). */
export interface CambioEstadoRequestDTO {
  estado: EstadoFactura;
  observaciones?: string;
}

/** Filtros opcionales del GET /api/facturas (query params). */
export interface FacturaFiltrosDTO {
  fechaDesde?: string;
  fechaHasta?: string;
  estado?: EstadoFactura | '';
  montoMinimo?: number;
}

/** Espejo de OpcionDTO (catálogos para los <select>). */
export interface OpcionDTO {
  id: number;
  etiqueta: string;
  valor: number | null;
  codigoMoneda?: string;
}

/** Espejo de ProblemDetail (RFC 7807) que devuelve el backend ante errores. */
export interface ProblemDetail {
  status: number;
  title: string;
  detail: string;
  errores?: Record<string, string>;
}
