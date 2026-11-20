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
  clientes: [] as OpcionDTO[],
  listasPrecio: [] as OpcionDTO[],
  articulos: [] as OpcionDTO[],
  articulosPorLista: new Map<number, OpcionDTO[]>(),
  monedas: [] as OpcionDTO[],
  guardando: false,
  accionesPendientes: new Set<number>(),
  busqueda: 0,
  edicion: 0,
  cargaArticulos: 0,
  paginaClientes: 1,
  paginaArticulos: 1,
  paginaFacturas: 1,
};

const TAMANO_PAGINA = 10;

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
function formatoFecha(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : iso;
}

function fechaHoyVisible(): string {
  const hoy = new Date();
  return `${String(hoy.getDate()).padStart(2, '0')}/${String(hoy.getMonth() + 1).padStart(2, '0')}/${hoy.getFullYear()}`;
}

function fechaIso(valor: string, campo: string): string | undefined {
  const texto = valor.trim();
  if (!texto) return undefined;
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
  if (!match) throw new Error(`${campo}: ingresá la fecha como DD/MM/AAAA`);
  const dia = Number(match[1]), mes = Number(match[2]), anio = Number(match[3]);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  if (fecha.getUTCFullYear() !== anio || fecha.getUTCMonth() !== mes - 1 || fecha.getUTCDate() !== dia) {
    throw new Error(`${campo}: ingresá una fecha válida`);
  }
  return `${match[3]}-${match[2]}-${match[1]}`;
}

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

function abrirDialogo(dialogo: HTMLDialogElement, foco: HTMLInputElement): void {
  if (typeof dialogo.showModal === 'function') dialogo.showModal();
  else dialogo.setAttribute('open', '');
  foco.focus();
}

function cerrarDialogo(dialogo: HTMLDialogElement, retorno: HTMLButtonElement): void {
  if (typeof dialogo.close === 'function') dialogo.close();
  else dialogo.removeAttribute('open');
  retorno.focus();
}

function normalizarBusqueda(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR');
}

function pagina<T>(opciones: T[], numero: number): { elementos: T[]; totalPaginas: number; numero: number } {
  const totalPaginas = Math.max(1, Math.ceil(opciones.length / TAMANO_PAGINA));
  const actual = Math.min(Math.max(numero, 1), totalPaginas);
  return { elementos: opciones.slice((actual - 1) * TAMANO_PAGINA, actual * TAMANO_PAGINA), totalPaginas, numero: actual };
}

// ============================ Catálogos ============================

async function cargarCatalogos(): Promise<void> {
  const [pv, cli, iva, mon, listas] = await Promise.all([
    api.obtenerPuntosVenta(),
    api.obtenerClientes(),
    api.obtenerCondicionesIva(),
    api.obtenerTiposMoneda(),
    api.obtenerListasPrecio(),
  ]);
  const form = $<HTMLFormElement>('#form-factura');
  llenarSelect(form.elements.namedItem('puntoVentaId') as HTMLSelectElement, pv);
  llenarSelect(form.elements.namedItem('condicionIvaId') as HTMLSelectElement, iva);
  llenarSelect(form.elements.namedItem('tipoMonedaId') as HTMLSelectElement, mon);
  llenarSelect($<HTMLSelectElement>('#lista-precio'), listas);
  estado.clientes = cli;
  estado.listasPrecio = listas;
  estado.monedas = mon;
  renderItems();
  $<HTMLInputElement>('[name="fechaEmision"]').value = fechaHoyVisible();
  if (listas.length > 0) {
    await cargarArticulosPorLista(listas[0].id);
  } else {
    $<HTMLButtonElement>('#btn-buscar-articulo').disabled = true;
  }
}

async function cargarArticulosPorLista(listaPrecioId: number): Promise<void> {
  const solicitud = ++estado.cargaArticulos;
  $<HTMLButtonElement>('#btn-buscar-articulo').disabled = true;
  try {
    const articulos = estado.articulosPorLista.get(listaPrecioId) ?? await api.obtenerListaPrecioArticulos(listaPrecioId);
    estado.articulosPorLista.set(listaPrecioId, articulos);
    if (solicitud !== estado.cargaArticulos) return;
    estado.articulos = articulos;
    estado.paginaArticulos = 1;
    renderArticulos();
  } finally {
    if (solicitud === estado.cargaArticulos) $<HTMLButtonElement>('#btn-buscar-articulo').disabled = estado.guardando;
  }
}

function seleccionarCliente(cliente: OpcionDTO | null): void {
  $<HTMLInputElement>('[name="clienteId"]').value = cliente ? String(cliente.id) : '';
  $<HTMLOutputElement>('#cliente-elegido').textContent = cliente?.etiqueta ?? 'Consumidor Final';
}

function seleccionarArticulo(articulo: OpcionDTO | null): void {
  $<HTMLInputElement>('#item-articulo').value = articulo ? String(articulo.id) : '';
  $<HTMLOutputElement>('#item-articulo-elegido').textContent = articulo?.etiqueta ?? 'Sin artículo seleccionado';
  $<HTMLElement>('#item-precio').textContent = articulo ? `Precio: ${mostrarImporte(articulo.valor ?? 0, monedaSeleccionada())}` : '';
}

function renderClientes(): void {
  const termino = normalizarBusqueda($<HTMLInputElement>('#buscar-cliente').value.trim());
  const filtrados = estado.clientes.filter(c => normalizarBusqueda(c.etiqueta).includes(termino));
  const resultado = pagina(filtrados, estado.paginaClientes);
  estado.paginaClientes = resultado.numero;
  const tbody = $<HTMLTableSectionElement>('#resultados-clientes');
  tbody.innerHTML = '';
  for (const cliente of resultado.elementos) {
    const fila = document.createElement('tr');
    agregarCelda(fila, cliente.etiqueta);
    const boton = document.createElement('button');
    boton.type = 'button'; boton.className = 'btn btn--sm'; boton.textContent = 'Seleccionar';
    boton.dataset.seleccionarCliente = String(cliente.id);
    agregarCelda(fila, '').append(boton);
    tbody.append(fila);
  }
  if (!filtrados.length) {
    const fila = document.createElement('tr');
    const celda = agregarCelda(fila, 'No se encontraron clientes.'); celda.colSpan = 2;
    tbody.append(fila);
  }
  $<HTMLElement>('#pagina-clientes').textContent = `Página ${resultado.numero} de ${resultado.totalPaginas}`;
  $<HTMLButtonElement>('#clientes-anterior').disabled = resultado.numero <= 1;
  $<HTMLButtonElement>('#clientes-siguiente').disabled = resultado.numero >= resultado.totalPaginas;
}

function renderArticulos(): void {
  const lista = $<HTMLSelectElement>('#lista-precio');
  $<HTMLElement>('#articulos-lista-actual').textContent = `Lista: ${lista.selectedOptions[0]?.textContent ?? 'ninguna'}`;
  const termino = normalizarBusqueda($<HTMLInputElement>('#buscar-articulo').value.trim());
  const filtrados = estado.articulos.filter(a => normalizarBusqueda(a.etiqueta).includes(termino));
  const resultado = pagina(filtrados, estado.paginaArticulos);
  estado.paginaArticulos = resultado.numero;
  const tbody = $<HTMLTableSectionElement>('#resultados-articulos');
  tbody.innerHTML = '';
  for (const articulo of resultado.elementos) {
    const fila = document.createElement('tr');
    agregarCelda(fila, articulo.etiqueta);
    agregarCelda(fila, mostrarImporte(articulo.valor ?? 0, monedaSeleccionada()), 'num');
    const boton = document.createElement('button');
    boton.type = 'button'; boton.className = 'btn btn--sm'; boton.textContent = 'Seleccionar';
    boton.dataset.seleccionarArticulo = String(articulo.id);
    agregarCelda(fila, '').append(boton);
    tbody.append(fila);
  }
  if (!filtrados.length) {
    const fila = document.createElement('tr');
    const celda = agregarCelda(fila, 'No se encontraron artículos en esta lista.'); celda.colSpan = 3;
    tbody.append(fila);
  }
  $<HTMLElement>('#pagina-articulos').textContent = `Página ${resultado.numero} de ${resultado.totalPaginas}`;
  $<HTMLButtonElement>('#articulos-anterior').disabled = resultado.numero <= 1;
  $<HTMLButtonElement>('#articulos-siguiente').disabled = resultado.numero >= resultado.totalPaginas;
}

// ============================ 1. Filtros ============================

function leerFiltros(): FacturaFiltrosDTO {
  const form = $<HTMLFormElement>('#form-filtros');
  const data = new FormData(form);
  const monto = data.get('montoMinimo') as string;
  return {
    fechaDesde: fechaIso(data.get('fechaDesde') as string, 'Desde'),
    fechaHasta: fechaIso(data.get('fechaHasta') as string, 'Hasta'),
    estado: (data.get('estado') as EstadoFactura | '') || undefined,
    montoMinimo: monto ? Number(monto) : undefined,
  };
}

async function buscar(): Promise<void> {
  const solicitud = ++estado.busqueda;
  try {
    const filtros = leerFiltros();
    const facturas = await api.obtenerFacturas(filtros);
    if (solicitud !== estado.busqueda) return;
    estado.filtros = filtros;
    estado.facturas = facturas;
    estado.paginaFacturas = 1;
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
  const articuloId = $<HTMLInputElement>('#item-articulo').value;
  const cantidad = Number($<HTMLInputElement>('#item-cantidad').value);
  const bonif = Number($<HTMLInputElement>('#item-bonif').value);
  const descripcion = $<HTMLInputElement>('#item-descripcion').value.trim();
  const opcion = estado.articulos.find((a) => a.id === Number(articuloId));

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
  const fechaEmision = fechaIso(data.get('fechaEmision') as string, 'Fecha de emisión');
  if (!fechaEmision) throw new Error('Fecha de emisión: ingresá una fecha en formato DD/MM/AAAA');
  return {
    clienteId: clienteId ? Number(clienteId) : null,
    condicionIvaId: Number(data.get('condicionIvaId')),
    puntoVentaId: Number(data.get('puntoVentaId')),
    tipoMonedaId: Number(data.get('tipoMonedaId')),
    fechaEmision,
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
  let request: FacturaRequestDTO;
  try { request = leerFormularioFactura(); }
  catch (err) { return mostrarMensaje(String(err instanceof Error ? err.message : err), 'error'); }
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
  document.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLButtonElement>('[form="form-item"]').forEach(el => { el.disabled = bloqueado; });
  $<HTMLButtonElement>('#btn-buscar-articulo').disabled = bloqueado || estado.listasPrecio.length === 0;
  $<HTMLFormElement>('#form-factura').setAttribute('aria-busy', String(bloqueado));
  renderFacturas();
}

function limpiarFormulario(trasGuardar = false): void {
  if (estado.guardando && !trasGuardar) return;
  ++estado.edicion;
  $<HTMLFormElement>('#form-factura').reset();
  $<HTMLInputElement>('[name="fechaEmision"]').value = fechaHoyVisible();
  seleccionarCliente(null);
  seleccionarArticulo(null);
  $<HTMLSelectElement>('#lista-precio').value = estado.listasPrecio[0] ? String(estado.listasPrecio[0].id) : '';
  if (estado.listasPrecio[0]) void cargarArticulosPorLista(estado.listasPrecio[0].id);
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
    const todosArticulos = await api.obtenerListaPrecioArticulos();
    if (solicitud !== estado.edicion || estado.guardando) return;
    const primeraListaId = todosArticulos.find(a => a.id === det.detalles[0]?.listaPrecioArticuloId)?.listaPrecioId
      ?? estado.listasPrecio[0]?.id;
    if (primeraListaId !== undefined) {
      $<HTMLSelectElement>('#lista-precio').value = String(primeraListaId);
      await cargarArticulosPorLista(primeraListaId);
      if (solicitud !== estado.edicion || estado.guardando) return;
    }
    seleccionarArticulo(null);
    const form = $<HTMLFormElement>('#form-factura');
    (form.elements.namedItem('puntoVentaId') as HTMLSelectElement).value = String(det.puntoVentaId);
    seleccionarCliente(det.clienteId ? estado.clientes.find(c => c.id === det.clienteId) ?? { id: det.clienteId, etiqueta: det.clienteDenominacion, valor: null } : null);
    (form.elements.namedItem('condicionIvaId') as HTMLSelectElement).value = String(det.condicionIvaId);
    (form.elements.namedItem('tipoMonedaId') as HTMLSelectElement).value = String(det.tipoMonedaId);
    (form.elements.namedItem('fechaEmision') as HTMLInputElement).value = formatoFecha(det.fechaEmision);
    (form.elements.namedItem('observaciones') as HTMLInputElement).value = det.observaciones ?? '';
    estado.items = det.detalles.map((d) => ({
        listaPrecioArticuloId: d.listaPrecioArticuloId,
        cantidad: d.cantidad,
        porcentajeBonificacion: d.porcentajeBonificacion,
        descripcion: d.descripcion,
        etiqueta: todosArticulos.find((a) => a.id === d.listaPrecioArticuloId)?.etiqueta ?? `#${d.listaPrecioArticuloId}`,
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
  const resultado = pagina(estado.facturas, estado.paginaFacturas);
  estado.paginaFacturas = resultado.numero;
  $<HTMLElement>('#pagina-facturas').textContent = `Página ${resultado.numero} de ${resultado.totalPaginas}`;
  $<HTMLButtonElement>('#facturas-anterior').disabled = resultado.numero <= 1;
  $<HTMLButtonElement>('#facturas-siguiente').disabled = resultado.numero >= resultado.totalPaginas;

  for (const f of resultado.elementos) {
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
      boton.disabled = !habilitada || estado.guardando || estado.accionesPendientes.has(f.id);
      boton.textContent = etiqueta;
      acciones.append(boton);
    }
    if (f.estado === 'EMITIDA' || f.estado === 'ANULADA') {
      const imprimir = document.createElement('button');
      imprimir.type = 'button'; imprimir.className = 'btn btn--sm';
      imprimir.dataset.accion = 'imprimir'; imprimir.dataset.id = String(f.id);
      imprimir.textContent = 'Imprimir';
      imprimir.disabled = estado.guardando || estado.accionesPendientes.has(f.id);
      acciones.append(imprimir);
    }
    tbody.appendChild(tr);
  }
}

async function accionFila(accion: string, id: number): Promise<void> {
  if (estado.guardando || estado.accionesPendientes.has(id)) return;
  const factura = estado.facturas.find((f) => f.id === id);
  if (!factura) return;
  const mutacion = accion === 'emitir' || accion === 'anular' || accion === 'eliminar';
  if (mutacion) {
    estado.accionesPendientes.add(id);
    renderFacturas();
  }
  try {
    switch (accion) {
      case 'imprimir':
        window.open(api.urlFacturaPdf(id), '_blank', 'noopener');
        return;
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
  } finally {
    if (mutacion) {
      estado.accionesPendientes.delete(id);
      renderFacturas();
    }
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
  $<HTMLSelectElement>('[name="tipoMonedaId"]').addEventListener('change', () => {
    renderItems(); renderArticulos();
    const articulo = estado.articulos.find(a => a.id === Number($<HTMLInputElement>('#item-articulo').value));
    seleccionarArticulo(articulo ?? null);
  });
  $<HTMLSelectElement>('#lista-precio').addEventListener('change', () => {
    seleccionarArticulo(null);
    $<HTMLInputElement>('#buscar-articulo').value = '';
    estado.articulos = [];
    renderArticulos();
    const id = Number($<HTMLSelectElement>('#lista-precio').value);
    if (id) void cargarArticulosPorLista(id).catch(manejarError);
  });
  $<HTMLButtonElement>('#btn-buscar-cliente').addEventListener('click', () => {
    if (estado.guardando) return;
    $<HTMLInputElement>('#buscar-cliente').value = '';
    estado.paginaClientes = 1; renderClientes();
    abrirDialogo($<HTMLDialogElement>('#dialogo-clientes'), $<HTMLInputElement>('#buscar-cliente'));
  });
  $<HTMLButtonElement>('#btn-consumidor-final').addEventListener('click', () => { if (!estado.guardando) seleccionarCliente(null); });
  $<HTMLInputElement>('#buscar-cliente').addEventListener('input', () => { estado.paginaClientes = 1; renderClientes(); });
  $<HTMLButtonElement>('#clientes-anterior').addEventListener('click', () => { --estado.paginaClientes; renderClientes(); });
  $<HTMLButtonElement>('#clientes-siguiente').addEventListener('click', () => { ++estado.paginaClientes; renderClientes(); });
  $<HTMLButtonElement>('#cerrar-clientes').addEventListener('click', () => cerrarDialogo($<HTMLDialogElement>('#dialogo-clientes'), $<HTMLButtonElement>('#btn-buscar-cliente')));
  $<HTMLTableSectionElement>('#resultados-clientes').addEventListener('click', ev => {
    const boton = (ev.target as HTMLElement).closest<HTMLButtonElement>('[data-seleccionar-cliente]');
    const cliente = estado.clientes.find(c => c.id === Number(boton?.dataset.seleccionarCliente));
    if (!boton || !cliente || estado.guardando) return;
    seleccionarCliente(cliente);
    cerrarDialogo($<HTMLDialogElement>('#dialogo-clientes'), $<HTMLButtonElement>('#btn-buscar-cliente'));
  });
  $<HTMLButtonElement>('#btn-buscar-articulo').addEventListener('click', () => {
    if (estado.guardando || !estado.listasPrecio.length) return;
    $<HTMLInputElement>('#buscar-articulo').value = '';
    estado.paginaArticulos = 1; renderArticulos();
    abrirDialogo($<HTMLDialogElement>('#dialogo-articulos'), $<HTMLInputElement>('#buscar-articulo'));
  });
  $<HTMLInputElement>('#buscar-articulo').addEventListener('input', () => { estado.paginaArticulos = 1; renderArticulos(); });
  $<HTMLButtonElement>('#articulos-anterior').addEventListener('click', () => { --estado.paginaArticulos; renderArticulos(); });
  $<HTMLButtonElement>('#articulos-siguiente').addEventListener('click', () => { ++estado.paginaArticulos; renderArticulos(); });
  $<HTMLButtonElement>('#cerrar-articulos').addEventListener('click', () => cerrarDialogo($<HTMLDialogElement>('#dialogo-articulos'), $<HTMLButtonElement>('#btn-buscar-articulo')));
  $<HTMLTableSectionElement>('#resultados-articulos').addEventListener('click', ev => {
    const boton = (ev.target as HTMLElement).closest<HTMLButtonElement>('[data-seleccionar-articulo]');
    const articulo = estado.articulos.find(a => a.id === Number(boton?.dataset.seleccionarArticulo));
    if (!boton || !articulo || estado.guardando) return;
    seleccionarArticulo(articulo);
    cerrarDialogo($<HTMLDialogElement>('#dialogo-articulos'), $<HTMLButtonElement>('#btn-buscar-articulo'));
  });
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
  $<HTMLButtonElement>('#facturas-anterior').addEventListener('click', () => { --estado.paginaFacturas; renderFacturas(); });
  $<HTMLButtonElement>('#facturas-siguiente').addEventListener('click', () => { ++estado.paginaFacturas; renderFacturas(); });
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
