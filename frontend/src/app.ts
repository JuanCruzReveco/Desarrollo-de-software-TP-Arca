/**
 * Lógica de UI: manipula el DOM, mantiene el estado de los ítems en memoria y
 * conecta los eventos de los formularios con FacturaService (capa HTTP).
 */
import { ApiError, FacturaService } from './services/FacturaService.js';
import type {
  EstadoFactura,
  FacturaDetalleRequestDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  OpcionDTO,
} from './models/factura.dto.js';

// ============================ Estado en memoria ============================

interface ItemEnMemoria extends FacturaDetalleRequestDTO {
  etiqueta: string;
  precioUnitario: number;
}

const estado = {
  items: [] as ItemEnMemoria[],
  facturas: [] as FacturaReporteDTO[],
  filtros: {} as FacturaFiltrosDTO,
  editandoId: null as number | null,
  articulos: [] as OpcionDTO[],
  monedas: [] as OpcionDTO[],
  guardando: false,
  busqueda: 0,
  edicion: 0,
};

const apiBaseUrl = document.querySelector<HTMLMetaElement>('meta[name="api-base-url"]')?.content.trim() || 'http://localhost:8080/api';
const api = new FacturaService(apiBaseUrl);

// ============================ Helpers DOM ============================

function $<T extends HTMLElement>(selector: string): T {
  const el = document.querySelector<T>(selector);
  if (!el) throw new Error(`No se encontró el elemento ${selector}`);
  return el;
}

function mostrarImporte(importe: number, codigo = 'PES'): string {
  const iso = ({ PES: 'ARS', DOL: 'USD' } as Record<string, string>)[codigo];
  if (!iso) return `${codigo} ${new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(importe)}`;
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: iso }).format(importe);
}

function monedaSeleccionada(): string {
  const id = Number($<HTMLSelectElement>('[name="tipoMonedaId"]').value);
  return estado.monedas.find(m => m.id === id)?.codigoMoneda ?? 'PES';
}

function agregarCelda(fila: HTMLTableRowElement, texto: string | number, clase = ''): HTMLTableCellElement {
  const celda = fila.insertCell();
  celda.textContent = String(texto);
  celda.className = clase;
  return celda;
}
const formatoFecha = (iso: string) => new Date(iso + 'T00:00:00').toLocaleDateString('es-AR');

function mostrarMensaje(texto: string, tipo: 'ok' | 'error'): void {
  const el = $<HTMLParagraphElement>('#mensaje');
  el.textContent = texto;
  el.className = `mensaje mensaje--${tipo}`;
  el.hidden = false;
  window.clearTimeout((el as unknown as { timer?: number }).timer);
  if (tipo === 'ok') (el as unknown as { timer?: number }).timer = window.setTimeout(() => { el.textContent = ''; }, 6000);
}

function manejarError(err: unknown): void {
  if (err instanceof ApiError) mostrarMensaje(err.mensajeCompleto, 'error');
  else if (err instanceof TypeError) mostrarMensaje('No se pudo conectar con el backend (¿está levantado en :8080?)', 'error');
  else mostrarMensaje(String(err), 'error');
  console.error(err);
}

function llenarSelect(select: HTMLSelectElement, opciones: OpcionDTO[], conVacio?: string): void {
  select.innerHTML = '';
  if (conVacio !== undefined) select.add(new Option(conVacio, ''));
  for (const o of opciones) select.add(new Option(o.etiqueta, String(o.id)));
}

// ============================ Catálogos ============================

async function cargarCatalogos(): Promise<void> {
  const [pv, cli, iva, mon, art] = await Promise.all([
    api.obtenerPuntosVenta(),
    api.obtenerClientes(),
    api.obtenerCondicionesIva(),
    api.obtenerTiposMoneda(),
    api.obtenerListaPrecioArticulos(),
  ]);
  const form = $<HTMLFormElement>('#form-factura');
  llenarSelect(form.elements.namedItem('puntoVentaId') as HTMLSelectElement, pv);
  llenarSelect(form.elements.namedItem('clienteId') as HTMLSelectElement, cli, 'Consumidor Final');
  llenarSelect(form.elements.namedItem('condicionIvaId') as HTMLSelectElement, iva);
  llenarSelect(form.elements.namedItem('tipoMonedaId') as HTMLSelectElement, mon);
  estado.monedas = mon;
  renderItems();
  estado.articulos = art;
  llenarSelect($<HTMLSelectElement>('#item-articulo'), art);
}

// ============================ 1. Filtros ============================

function leerFiltros(): FacturaFiltrosDTO {
  const form = $<HTMLFormElement>('#form-filtros');
  const data = new FormData(form);
  const monto = data.get('montoMinimo') as string;
  return {
    fechaDesde: (data.get('fechaDesde') as string) || undefined,
    fechaHasta: (data.get('fechaHasta') as string) || undefined,
    estado: (data.get('estado') as EstadoFactura | '') || undefined,
    montoMinimo: monto ? Number(monto) : undefined,
  };
}

async function buscar(): Promise<void> {
  const filtros = leerFiltros();
  const solicitud = ++estado.busqueda;
  try {
    const facturas = await api.obtenerFacturas(filtros);
    if (solicitud !== estado.busqueda) return;
    estado.filtros = filtros;
    estado.facturas = facturas;
    renderFacturas();
    $<HTMLAnchorElement>('#link-pdf').href = api.urlReporte('pdf', estado.filtros);
    $<HTMLAnchorElement>('#link-xlsx').href = api.urlReporte('xlsx', estado.filtros);
  } catch (err) {
    if (solicitud === estado.busqueda) manejarError(err);
  }
}

// ============================ 2. Alta / edición ============================

function agregarItem(): void {
  if (estado.guardando) return;
  const select = $<HTMLSelectElement>('#item-articulo');
  const cantidad = Number($<HTMLInputElement>('#item-cantidad').value);
  const bonif = Number($<HTMLInputElement>('#item-bonif').value);
  const descripcion = $<HTMLInputElement>('#item-descripcion').value.trim();
  const opcion = estado.articulos.find((a) => a.id === Number(select.value));

  if (!opcion) return mostrarMensaje('Seleccioná un artículo', 'error');
  if (descripcion.length > 255) return mostrarMensaje('La descripción admite hasta 255 caracteres', 'error');
  if (!Number.isFinite(cantidad) || !(cantidad > 0)) return mostrarMensaje('La cantidad debe ser finita y mayor a cero', 'error');
  if (!Number.isFinite(bonif) || bonif < 0 || bonif > 100) return mostrarMensaje('La bonificación debe estar entre 0 y 100', 'error');

  estado.items.push({
    listaPrecioArticuloId: opcion.id,
    cantidad,
    porcentajeBonificacion: bonif,
    descripcion: descripcion || undefined,
    etiqueta: opcion.etiqueta,
    precioUnitario: opcion.valor ?? 0,
  });
  $<HTMLInputElement>('#item-cantidad').value = '1';
  $<HTMLInputElement>('#item-bonif').value = '0';
  $<HTMLInputElement>('#item-descripcion').value = '';
  renderItems();
}

function quitarItem(indice: number): void {
  if (estado.guardando) return;
  estado.items.splice(indice, 1);
  renderItems();
}

function renderItems(): void {
  const tbody = $<HTMLTableSectionElement>('#tabla-items tbody');
  tbody.innerHTML = '';
  let total = 0;
  estado.items.forEach((item, i) => {
    const subtotal = item.cantidad * item.precioUnitario * (1 - (item.porcentajeBonificacion ?? 0) / 100);
    total += subtotal;
    const tr = document.createElement('tr');
    const articulo = agregarCelda(tr, item.etiqueta);
    if (item.descripcion) {
      const descripcion = document.createElement('small');
      descripcion.textContent = item.descripcion;
      articulo.append(document.createElement('br'), descripcion);
    }
    agregarCelda(tr, item.cantidad, 'num');
    agregarCelda(tr, mostrarImporte(item.precioUnitario, monedaSeleccionada()), 'num');
    agregarCelda(tr, item.porcentajeBonificacion ?? 0, 'num');
    agregarCelda(tr, mostrarImporte(subtotal, monedaSeleccionada()), 'num');
    const quitar = document.createElement('button');
    quitar.type = 'button';
    quitar.className = 'btn btn--sm btn--peligro';
    quitar.dataset.quitar = String(i);
    quitar.textContent = 'Quitar';
    agregarCelda(tr, '').append(quitar);
    tbody.appendChild(tr);
  });
  $<HTMLTableCellElement>('#total-items').textContent = mostrarImporte(total, monedaSeleccionada());
}

function leerFormularioFactura(): FacturaRequestDTO {
  const form = $<HTMLFormElement>('#form-factura');
  const data = new FormData(form);
  const clienteId = data.get('clienteId') as string;
  return {
    clienteId: clienteId ? Number(clienteId) : null,
    condicionIvaId: Number(data.get('condicionIvaId')),
    puntoVentaId: Number(data.get('puntoVentaId')),
    tipoMonedaId: Number(data.get('tipoMonedaId')),
    fechaEmision: (data.get('fechaEmision') as string) || undefined,
    observaciones: (data.get('observaciones') as string) || undefined,
    detalles: estado.items.map(({ listaPrecioArticuloId, cantidad, porcentajeBonificacion, descripcion }) => ({
      listaPrecioArticuloId, cantidad, porcentajeBonificacion, descripcion,
    })),
  };
}

async function guardarFactura(ev: Event): Promise<void> {
  ev.preventDefault();
  if (estado.guardando) return;
  if (estado.items.length === 0) return mostrarMensaje('Agregá al menos un renglón', 'error');
  const request = leerFormularioFactura();
  estado.guardando = true;
  ++estado.edicion;
  bloquearFormulario(true);
  try {
    if (estado.editandoId === null) {
      const creada = await api.crearFactura(request);
      mostrarMensaje(`Factura #${creada.id} creada en estado ${creada.estado} por ${mostrarImporte(creada.importeTotal, creada.codigoMoneda)}`, 'ok');
    } else {
      const actualizada = await api.actualizarFactura(estado.editandoId, request);
      mostrarMensaje(`Factura #${actualizada.id} actualizada: ${mostrarImporte(actualizada.importeTotal, actualizada.codigoMoneda)}`, 'ok');
    }
    limpiarFormulario(true);
    await buscar();
  } catch (err) {
    manejarError(err);
  } finally {
    estado.guardando = false;
    bloquearFormulario(false);
  }
}

function bloquearFormulario(bloqueado: boolean): void {
  document.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLButtonElement>('#form-factura input, #form-factura select, #form-factura button').forEach(el => { el.disabled = bloqueado; });
  $<HTMLFormElement>('#form-factura').setAttribute('aria-busy', String(bloqueado));
  renderFacturas();
}

function limpiarFormulario(trasGuardar = false): void {
  if (estado.guardando && !trasGuardar) return;
  ++estado.edicion;
  $<HTMLFormElement>('#form-factura').reset();
  estado.items = [];
  estado.editandoId = null;
  renderItems();
  $<HTMLHeadingElement>('#titulo-alta').textContent = 'Nueva factura';
  $<HTMLButtonElement>('#btn-guardar').textContent = 'Guardar factura';
  $<HTMLButtonElement>('#btn-cancelar-edicion').hidden = true;
}

/** Carga una factura BORRADOR en el formulario para editarla con PUT. */
async function editarFactura(f: FacturaReporteDTO): Promise<void> {
  if (estado.guardando) return;
  const solicitud = ++estado.edicion;
  $<HTMLButtonElement>('#btn-cancelar-edicion').hidden = false;
  try {
    const det = await api.obtenerFactura(f.id);
    if (solicitud !== estado.edicion || estado.guardando) return;
    const form = $<HTMLFormElement>('#form-factura');
    (form.elements.namedItem('puntoVentaId') as HTMLSelectElement).value = String(det.puntoVentaId);
    (form.elements.namedItem('clienteId') as HTMLSelectElement).value = det.clienteId ? String(det.clienteId) : '';
    (form.elements.namedItem('condicionIvaId') as HTMLSelectElement).value = String(det.condicionIvaId);
    (form.elements.namedItem('tipoMonedaId') as HTMLSelectElement).value = String(det.tipoMonedaId);
    (form.elements.namedItem('fechaEmision') as HTMLInputElement).value = det.fechaEmision ?? '';
    (form.elements.namedItem('observaciones') as HTMLInputElement).value = det.observaciones ?? '';
    estado.items = det.detalles.map((d) => ({
        listaPrecioArticuloId: d.listaPrecioArticuloId,
        cantidad: d.cantidad,
        porcentajeBonificacion: d.porcentajeBonificacion,
        descripcion: d.descripcion,
        etiqueta: estado.articulos.find((a) => a.id === d.listaPrecioArticuloId)?.etiqueta ?? `#${d.listaPrecioArticuloId}`,
        precioUnitario: d.precioUnitario,
    }));
    estado.editandoId = f.id;
    renderItems();
    $<HTMLHeadingElement>('#titulo-alta').textContent = `Editando factura #${f.id} (borrador)`;
    $<HTMLButtonElement>('#btn-guardar').textContent = 'Guardar cambios';
    $<HTMLButtonElement>('#btn-cancelar-edicion').hidden = false;
    $<HTMLElement>('#form-factura').scrollIntoView({ behavior: 'smooth' });
  } catch (err) {
    if (solicitud === estado.edicion) manejarError(err);
  }
}

// ============================ 3. Listado y acciones por fila ============================

function renderFacturas(): void {
  const tbody = $<HTMLTableSectionElement>('#tabla-facturas tbody');
  tbody.innerHTML = '';
  $<HTMLElement>('#contador').textContent = `${estado.facturas.length} comprobante(s)`;
  $<HTMLParagraphElement>('#sin-resultados').hidden = estado.facturas.length > 0;

  for (const f of estado.facturas) {
    const tr = document.createElement('tr');
    const esBorrador = f.estado === 'BORRADOR';
    const esEmitida = f.estado === 'EMITIDA';
    agregarCelda(tr, f.numeroFactura ?? '—', 'num');
    agregarCelda(tr, formatoFecha(f.fechaEmision));
    agregarCelda(tr, f.clienteDenominacion);
    agregarCelda(tr, f.condicionIva);
    agregarCelda(tr, f.puntoVentaDescripcion);
    agregarCelda(tr, f.cantidadItems, 'num');
    agregarCelda(tr, mostrarImporte(f.importeTotal, f.codigoMoneda), 'num');
    const badge = document.createElement('span');
    badge.className = `badge badge--${f.estado}`;
    badge.textContent = f.estado;
    agregarCelda(tr, '').append(badge);
    const acciones = agregarCelda(tr, '', 'acciones-fila');
    for (const [accion, etiqueta, habilitada, clase] of [
      ['emitir', 'Emitir', esBorrador, 'btn--ok'],
      ['editar', 'Editar', esBorrador, ''],
      ['anular', 'Anular', esEmitida, 'btn--peligro'],
      ['eliminar', 'Eliminar', esBorrador, 'btn--peligro'],
    ] as const) {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = `btn btn--sm ${clase}`;
      boton.dataset.accion = accion;
      boton.dataset.id = String(f.id);
      boton.disabled = !habilitada || estado.guardando;
      boton.textContent = etiqueta;
      acciones.append(boton);
    }
    tbody.appendChild(tr);
  }
}

async function accionFila(accion: string, id: number): Promise<void> {
  if (estado.guardando) return;
  const factura = estado.facturas.find((f) => f.id === id);
  if (!factura) return;
  try {
    switch (accion) {
      case 'emitir': {
        const r = await api.actualizarEstado(id, 'EMITIDA');
        mostrarMensaje(`Factura emitida con número ${r.numeroFactura}`, 'ok');
        break;
      }
      case 'anular': {
        const motivo = window.prompt('Motivo de la anulación (opcional):');
        if (motivo === null) return;
        await api.actualizarEstado(id, 'ANULADA', motivo || undefined);
        mostrarMensaje(`Factura N° ${factura.numeroFactura} anulada`, 'ok');
        break;
      }
      case 'eliminar': {
        if (!window.confirm(`¿Eliminar definitivamente el borrador #${id}?`)) return;
        await api.eliminarFactura(id);
        mostrarMensaje(`Borrador #${id} eliminado`, 'ok');
        break;
      }
      case 'editar':
        await editarFactura(factura);
        return;
    }
    await buscar();
  } catch (err) {
    manejarError(err);
  }
}

// ============================ Wiring de eventos ============================

function conectarEventos(): void {
  $<HTMLFormElement>('#form-filtros').addEventListener('submit', (ev) => { ev.preventDefault(); void buscar(); });
  $<HTMLButtonElement>('#btn-limpiar-filtros').addEventListener('click', () => {
    $<HTMLFormElement>('#form-filtros').reset();
    void buscar();
  });

  $<HTMLButtonElement>('#btn-agregar-item').addEventListener('click', agregarItem);
  $<HTMLSelectElement>('[name="tipoMonedaId"]').addEventListener('change', renderItems);
  $<HTMLTableSectionElement>('#tabla-items tbody').addEventListener('click', (ev) => {
    const btn = (ev.target as HTMLElement).closest<HTMLButtonElement>('[data-quitar]');
    if (btn) quitarItem(Number(btn.dataset.quitar));
  });
  $<HTMLFormElement>('#form-factura').addEventListener('submit', (ev) => void guardarFactura(ev));
  $<HTMLButtonElement>('#btn-cancelar-edicion').addEventListener('click', () => limpiarFormulario());

  $<HTMLTableSectionElement>('#tabla-facturas tbody').addEventListener('click', (ev) => {
    const btn = (ev.target as HTMLElement).closest<HTMLButtonElement>('[data-accion]');
    if (btn && !btn.disabled) void accionFila(btn.dataset.accion!, Number(btn.dataset.id));
  });
}

// ============================ Arranque ============================

async function iniciar(): Promise<void> {
  $<HTMLElement>('.topbar__api code').textContent = apiBaseUrl.replace(/\/+$/, '');
  conectarEventos();
  renderItems();
  try {
    await cargarCatalogos();
    await buscar();
  } catch (err) {
    manejarError(err);
  }
}

document.addEventListener('DOMContentLoaded', () => void iniciar());
