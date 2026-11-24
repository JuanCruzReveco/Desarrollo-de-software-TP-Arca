import type {
  CambioEstadoRequestDTO,
  ConfiguracionFiscalDTO,
  EstadoFactura,
  FacturaDetalladaDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  OpcionDTO,
  ProblemDetail,
} from '../models/factura.dto.js';

/** Error de la API con el ProblemDetail que devolvió el backend. */
export class ApiError extends Error {
  constructor(public readonly status: number, public readonly problem: ProblemDetail | null) {
    super(problem?.detail ?? `HTTP ${status}`);
  }

  /** Mensaje listo para mostrar: detalle + errores de validación campo por campo. */
  get mensajeCompleto(): string {
    if (!this.problem) return this.message;
    const campos = this.problem.errores
      ? ' ' + Object.entries(this.problem.errores).map(([k, v]) => `${k}: ${v}`).join('; ')
      : '';
    const title = typeof this.problem.title === 'string' ? this.problem.title : '';
    const detail = typeof this.problem.detail === 'string' ? this.problem.detail : this.message;
    return `${title ? title + ': ' : ''}${detail}${campos}`;
  }
}

/**
 * Capa de cliente HTTP: centraliza todas las llamadas al backend con fetch + async/await.
 * El backend corre en otro origen (localhost:8080), por eso Spring tiene CORS habilitado.
 */
export class FacturaService {
  constructor(
    private readonly baseUrl = 'http://localhost:8080/api',
    private readonly usuario = 'sistema',
  ) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
  }

  // ---------------------------- Facturas ----------------------------

  async obtenerFacturas(filtros: FacturaFiltrosDTO = {}): Promise<FacturaReporteDTO[]> {
    const params = new URLSearchParams();
    if (filtros.fechaDesde) params.set('fechaDesde', filtros.fechaDesde);
    if (filtros.fechaHasta) params.set('fechaHasta', filtros.fechaHasta);
    if (filtros.estado) params.set('estado', filtros.estado);
    if (filtros.montoMinimo !== undefined && !Number.isNaN(filtros.montoMinimo)) {
      params.set('montoMinimo', String(filtros.montoMinimo));
    }
    const qs = params.toString();
    return this.request<FacturaReporteDTO[]>(`/facturas${qs ? '?' + qs : ''}`);
  }

  async obtenerFactura(id: number): Promise<FacturaDetalladaDTO> {
    return this.request<FacturaDetalladaDTO>(`/facturas/${id}`);
  }

  async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return this.request<FacturaReporteDTO>('/facturas', { method: 'POST', body: JSON.stringify(factura) });
  }

  async actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return this.request<FacturaReporteDTO>(`/facturas/${id}`, { method: 'PUT', body: JSON.stringify(factura) });
  }

  async actualizarEstado(id: number, estado: EstadoFactura, observaciones?: string): Promise<FacturaReporteDTO> {
    const body: CambioEstadoRequestDTO = { estado, observaciones };
    return this.request<FacturaReporteDTO>(`/facturas/${id}/estado`, { method: 'PATCH', body: JSON.stringify(body) });
  }

  async eliminarFactura(id: number): Promise<void> {
    await this.request<void>(`/facturas/${id}`, { method: 'DELETE' });
  }

  /** URL de descarga del reporte con los mismos filtros del listado. */
  urlReporte(formato: 'pdf' | 'xlsx' | 'txt', filtros: FacturaFiltrosDTO = {}): string {
    const params = new URLSearchParams({ formato });
    if (filtros.fechaDesde) params.set('fechaDesde', filtros.fechaDesde);
    if (filtros.fechaHasta) params.set('fechaHasta', filtros.fechaHasta);
    if (filtros.estado) params.set('estado', filtros.estado);
    if (filtros.montoMinimo !== undefined && !Number.isNaN(filtros.montoMinimo)) {
      params.set('montoMinimo', String(filtros.montoMinimo));
    }
    return `${this.baseUrl}/facturas/reporte?${params.toString()}`;
  }

  urlFacturaPdf(id: number): string { return `${this.baseUrl}/facturas/${id}/pdf`; }

  // ---------------------------- Catálogos ----------------------------

  obtenerPuntosVenta(): Promise<OpcionDTO[]> { return this.request('/catalogos/puntos-venta'); }
  obtenerConfiguracionFiscal(): Promise<ConfiguracionFiscalDTO> { return this.request('/catalogos/configuracion-fiscal'); }
  obtenerClientes(): Promise<OpcionDTO[]> { return this.request('/catalogos/clientes'); }
  obtenerCondicionesIva(): Promise<OpcionDTO[]> { return this.request('/catalogos/condiciones-iva'); }
  obtenerTiposMoneda(): Promise<OpcionDTO[]> { return this.request('/catalogos/tipos-moneda'); }
  obtenerListasPrecio(): Promise<OpcionDTO[]> { return this.request('/catalogos/listas-precio'); }
  obtenerListaPrecioArticulos(listaPrecioId?: number): Promise<OpcionDTO[]> {
    return this.request(`/catalogos/lista-precio-articulos${listaPrecioId === undefined ? '' : `?listaPrecioId=${encodeURIComponent(String(listaPrecioId))}`}`);
  }

  // ---------------------------- Núcleo ----------------------------

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(this.baseUrl + path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Usuario': this.usuario,
        ...(init.headers ?? {}),
      },
    });

    if (!response.ok) {
      let problem: ProblemDetail | null = null;
      try { problem = (await response.json()) as ProblemDetail; } catch { /* sin cuerpo */ }
      throw new ApiError(response.status, problem);
    }
    if (response.status === 204) return undefined as T;   // DELETE -> No Content
    return (await response.json()) as T;
  }
}
