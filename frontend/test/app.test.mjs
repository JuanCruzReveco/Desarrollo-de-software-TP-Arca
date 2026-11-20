import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { JSDOM } from 'jsdom';

const invoice = { id: 1, numeroFactura: 10, fechaEmision: '2026-10-29', clienteDenominacion: 'Cliente', condicionIva: 'RI', puntoVentaDescripcion: 'Principal', importeTotal: 121, cantidadItems: 1, estado: 'EMITIDA', codigoMoneda: 'PES' };
const createdInvoice = { ...invoice, numeroFactura: null, estado: 'BORRADOR' };
const tick = () => new Promise(resolve => setImmediate(resolve));

async function open(t, { invoices = [], article = 'Artículo', catalogs = {}, post, patch, get, prompt = null, apiBase, selectArticle = true } = {}) {
  let html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  if (apiBase) html = html.replace(/<meta name="api-base-url"[^>]*>/, '').replace('</head>', `<meta name="api-base-url" content="${apiBase}"></head>`);
  const dom = new JSDOM(html, { url: 'http://localhost:5173', runScripts: 'outside-only' });
  t.after(() => dom.window.close());
  const requests = [];
  const openedUrls = [];
  dom.window.prompt = () => prompt;
  dom.window.open = (...args) => { openedUrls.push(args); return null; };
  dom.window.HTMLElement.prototype.scrollIntoView = () => {};
  dom.window.fetch = async (url, init = {}) => {
    requests.push({ url, ...init });
    let body;
    if (init.method === 'POST') return post ? post() : { ok: true, status: 201, json: async () => createdInvoice };
    if (init.method === 'PATCH' && patch) return patch();
    if (init.method === 'PATCH') body = { ...invoice, estado: 'ANULADA' };
    else if (url.includes('/catalogos/')) {
      const parsed = new URL(url);
      const path = parsed.pathname.split('/').at(-1);
      if (path === 'clientes') body = catalogs.clients ?? [{ id: 1, etiqueta: 'Opción', valor: null }];
      else if (path === 'listas-precio') body = catalogs.lists ?? [{ id: 10, etiqueta: 'Lista general', valor: null }];
      else if (path === 'lista-precio-articulos') body = parsed.searchParams.has('listaPrecioId')
        ? catalogs.articlesByList?.[parsed.searchParams.get('listaPrecioId')] ?? [{ id: 1, etiqueta: article, valor: 100 }]
        : catalogs.allArticles ?? [{ id: 1, etiqueta: article, valor: 100 }];
      else if (path === 'tipos-moneda') body = [{ id: 1, etiqueta: 'Peso ($)', valor: null, codigoMoneda: 'PES' }, { id: 2, etiqueta: 'Dólar (U$S)', valor: null, codigoMoneda: 'DOL' }];
      else body = [{ id: 1, etiqueta: 'Opción', valor: 100 }];
    } else if (get) return get(url, init);
    else body = invoices;
    return { ok: true, status: 200, json: async () => body };
  };
  const context = dom.getInternalVMContext();
  const modules = new Map();
  const load = async url => {
    if (modules.has(url.href)) return modules.get(url.href);
    const module = new vm.SourceTextModule(await readFile(url, 'utf8'), { context, identifier: url.href });
    modules.set(url.href, module);
    await module.link(specifier => load(new URL(specifier, url)));
    return module;
  };
  await (await load(new URL('../dist/app.js', import.meta.url))).evaluate();
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  const document = dom.window.document;
  if (selectArticle) {
    document.querySelector('#btn-buscar-articulo').click();
    document.querySelector('#resultados-articulos [data-seleccionar-articulo]').click();
  }
  return { document, requests, openedUrls, click: selector => document.querySelector(selector).click(), submit: () => document.querySelector('#form-factura').dispatchEvent(new dom.window.Event('submit', { cancelable: true })) };
}

test('item descriptions remain literal text, never executable markup', async t => {
  const ui = await open(t);
  ui.document.querySelector('#item-descripcion').value = '<img src=x onerror=alert(1)>';
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-items img'), null);
  assert.match(ui.document.querySelector('#tabla-items tbody').textContent, /<img src=x onerror=alert\(1\)>/);
});

const response = body => ({ ok: true, status: 200, json: async () => body });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const search = (ui, state) => {
  ui.document.querySelector('[name="estado"]').value = state;
  ui.document.querySelector('#form-filtros').dispatchEvent(new ui.document.defaultView.Event('submit', { cancelable: true }));
};
const detail = id => ({ ...createdInvoice, id, puntoVentaId: 1, clienteId: null, condicionIvaId: 1, tipoMonedaId: 1, detalles: [{ listaPrecioArticuloId: 1, cantidad: 1, precioUnitario: 100, descripcion: `Detalle ${id}` }] });

test('latest search wins when responses arrive in reverse order', async t => {
  const older = deferred(), newer = deferred();
  const ui = await open(t, { get: url => url.includes('BORRADOR') ? older.promise : url.includes('EMITIDA') ? newer.promise : response([]) });
  search(ui, 'BORRADOR'); search(ui, 'EMITIDA');
  newer.resolve(response([invoice])); await tick();
  older.resolve(response([createdInvoice])); await tick();
  assert.match(ui.document.querySelector('#tabla-facturas tbody').textContent, /EMITIDA/);
  assert.match(ui.document.querySelector('#link-pdf').href, /estado=EMITIDA/);
});

test('saving locks form and invoice changes until the response', async t => {
  const pending = deferred();
  const ui = await open(t, { invoices: [createdInvoice], post: () => pending.promise });
  ui.click('#btn-agregar-item'); ui.submit();
  assert.equal(ui.document.querySelector('[name="observaciones"]').disabled, true);
  ui.click('#btn-agregar-item'); ui.click('[data-quitar]'); ui.click('[data-accion="editar"]');
  assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, 1);
  assert.equal(ui.requests.filter(r => /facturas\/1$/.test(r.url)).length, 0);
  pending.resolve(response(createdInvoice)); await tick();
  assert.equal(ui.document.querySelector('[name="observaciones"]').disabled, false);
});

test('canceling a pending edit prevents late response replacing new work', async t => {
  const pending = deferred();
  const ui = await open(t, { get: url => /facturas\/1$/.test(url) ? pending.promise : response([createdInvoice]) });
  ui.click('[data-accion="editar"]');
  assert.equal(ui.document.querySelector('#btn-cancelar-edicion').hidden, false);
  ui.click('#btn-cancelar-edicion'); ui.click('#btn-agregar-item');
  pending.resolve(response(detail(1))); await tick();
  assert.equal(ui.document.querySelector('#titulo-alta').textContent, 'Nueva factura');
  assert.doesNotMatch(ui.document.querySelector('#tabla-items tbody').textContent, /Detalle 1/);
});

test('oversized description is rejected before adding an item', async t => {
  const ui = await open(t);
  ui.document.querySelector('#item-descripcion').value = 'a'.repeat(256);
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, 0);
  assert.equal(ui.document.querySelector('#item-descripcion').maxLength, 255);
});

test('nonstandard error body produces useful fallback', async t => {
  const ui = await open(t, { post: () => ({ ok: false, status: 400, json: async () => ({ error: 'Bad Request' }) }) });
  ui.document.defaultView.console.error = () => {};
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  assert.match(ui.document.querySelector('#mensaje').textContent, /HTTP 400/);
  assert.doesNotMatch(ui.document.querySelector('#mensaje').textContent, /undefined/);
});

test('validation error is announced and remains available without timeout', async t => {
  const ui = await open(t);
  assert.equal(ui.document.querySelector('#mensaje').hidden, false, 'live region must exist before announcing');
  const timers = [];
  ui.document.defaultView.setTimeout = callback => { timers.push(callback); return 1; };
  ui.submit();
  const message = ui.document.querySelector('#mensaje');
  assert.equal(message.getAttribute('aria-live'), 'polite');
  assert.equal(message.getAttribute('role'), 'status');
  for (const callback of timers) callback();
  assert.equal(message.hidden, false);
});

test('latest empty search keeps empty state, count and report links despite stale error', async t => {
  const older = deferred(), newer = deferred();
  const ui = await open(t, { get: url => url.includes('BORRADOR') ? older.promise : url.includes('ANULADA') ? newer.promise : response([invoice]) });
  search(ui, 'BORRADOR'); search(ui, 'ANULADA');
  newer.resolve(response([])); await tick();
  older.resolve({ ok: false, status: 500, json: async () => ({ title: 'Error', detail: 'viejo' }) }); await tick();
  assert.equal(ui.document.querySelector('#contador').textContent, '0 comprobante(s)');
  assert.equal(ui.document.querySelector('#sin-resultados').hidden, false);
  assert.match(ui.document.querySelector('#link-xlsx').href, /estado=ANULADA/);
  assert.doesNotMatch(ui.document.querySelector('#mensaje').textContent, /viejo/);
});

test('latest edit wins over an earlier invoice response', async t => {
  const first = deferred(), second = deferred();
  const ui = await open(t, { get: url => /facturas\/1$/.test(url) ? first.promise : /facturas\/2$/.test(url) ? second.promise : response([createdInvoice, { ...createdInvoice, id: 2 }]) });
  ui.click('[data-accion="editar"][data-id="1"]'); ui.click('[data-accion="editar"][data-id="2"]');
  second.resolve(response(detail(2))); await tick(); first.resolve(response(detail(1))); await tick();
  assert.match(ui.document.querySelector('#titulo-alta').textContent, /#2/);
  assert.match(ui.document.querySelector('#tabla-items tbody').textContent, /Detalle 2/);
});

test('failed PUT preserves editing values and unlocks every form control', async t => {
  const pending = deferred();
  const ui = await open(t, { get: (url, init) => init.method === 'PUT' ? pending.promise : /facturas\/1$/.test(url) ? response(detail(1)) : response([createdInvoice]) });
  ui.document.defaultView.console.error = () => {};
  ui.click('[data-accion="editar"]'); await tick(); ui.submit();
  ui.click('#btn-cancelar-edicion');
  assert.match(ui.document.querySelector('#titulo-alta').textContent, /#1/);
  pending.resolve({ ok: false, status: 409, json: async () => ({ title: 'Conflicto', detail: 'Reintentar' }) }); await tick();
  assert.equal(ui.document.querySelector('#form-factura :disabled'), null);
  assert.match(ui.document.querySelector('#tabla-items tbody').textContent, /Detalle 1/);
  assert.equal(ui.document.querySelector('#form-factura').getAttribute('aria-busy'), 'false');
});

test('255 character description is accepted intact', async t => {
  const ui = await open(t);
  ui.document.querySelector('#item-descripcion').value = 'b'.repeat(255);
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  assert.equal(JSON.parse(ui.requests.find(r => r.method === 'POST').body).detalles[0].descripcion, 'b'.repeat(255));
});

test('error without JSON falls back to HTTP status', async t => {
  const ui = await open(t, { post: () => ({ ok: false, status: 502, json: async () => { throw new Error('not json'); } }) });
  ui.document.defaultView.console.error = () => {};
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  assert.equal(ui.document.querySelector('#mensaje').textContent, 'HTTP 502');
});

test('success uses the same announcement region and may expire', async t => {
  const ui = await open(t);
  const timers = [];
  ui.document.defaultView.setTimeout = callback => { timers.push(callback); return 1; };
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  const message = ui.document.querySelector('#mensaje');
  assert.match(message.textContent, /creada/);
  assert.equal(message.getAttribute('aria-atomic'), 'true');
  assert.equal(message.hidden, false);
  for (const callback of timers) callback();
  assert.equal(message.hidden, false);
  assert.equal(message.textContent, '');
});

test('API customer and catalog text cannot insert HTML nodes', async t => {
  const ui = await open(t, { article: '<b>Artículo</b>', invoices: [{ ...invoice, clienteDenominacion: '<img src=x>' }] });
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-facturas img'), null);
  assert.equal(ui.document.querySelector('#tabla-items b'), null);
  assert.match(ui.document.querySelector('#tabla-items tbody').textContent, /<b>Artículo<\/b>/);
});

test('canceling annulment leaves invoice unchanged without a PATCH', async t => {
  const ui = await open(t, { invoices: [invoice], prompt: null });
  ui.click('[data-accion="anular"]');
  await tick();
  assert.equal(ui.requests.filter(r => r.method === 'PATCH').length, 0);
});

test('accepting an empty annulment reason still annuls', async t => {
  const ui = await open(t, { invoices: [invoice], prompt: '' });
  ui.click('[data-accion="anular"]');
  await tick();
  const patch = ui.requests.find(r => r.method === 'PATCH');
  assert.deepEqual(JSON.parse(patch.body), { estado: 'ANULADA' });
});

test('repeated submit while saving produces only one invoice request', async t => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const ui = await open(t, { post: () => pending });
  ui.click('#btn-agregar-item');
  ui.submit(); ui.submit();
  assert.equal(ui.requests.filter(r => r.method === 'POST').length, 1);
  assert.equal(ui.document.querySelector('#btn-guardar').disabled, true);
  release({ ok: true, status: 201, json: async () => createdInvoice });
  await tick();
  assert.equal(ui.document.querySelector('#btn-guardar').disabled, false);
});

test('failed save releases the button and allows retry', async t => {
  let attempt = 0;
  const ui = await open(t, { post: async () => ++attempt === 1
    ? { ok: false, status: 409, json: async () => ({ title: 'Conflicto', detail: 'Reintentar' }) }
    : { ok: true, status: 201, json: async () => createdInvoice } });
  // Expected API errors are shown in the UI; keep test output quiet.
  ui.document.defaultView.console.error = () => {};
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  assert.equal(ui.document.querySelector('#btn-guardar').disabled, false);
  ui.submit(); await tick();
  assert.equal(ui.requests.filter(r => r.method === 'POST').length, 2);
});

for (const discount of ['-1', '101']) test(`discount ${discount} cannot enter invoice items`, async t => {
  const ui = await open(t);
  ui.document.querySelector('#item-bonif').value = discount;
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, 0);
  assert.equal(ui.document.querySelector('#mensaje').hidden, false);
});

// Bypass native number sanitization to exercise the application boundary too.
for (const [selector, value] of [['#item-cantidad', 'Infinity'], ['#item-bonif', 'Infinity'], ['#item-bonif', 'abc']]) {
  test(`non-finite ${selector} ${value} cannot enter invoice items`, async t => {
    const ui = await open(t);
    const input = ui.document.querySelector(selector);
    input.type = 'text'; input.value = value;
    ui.click('#btn-agregar-item');
    assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, 0);
  });
}

for (const [amount, discount] of [['0', '0'], ['1', '100']]) test(`quantity ${amount}, discount ${discount} obey boundaries`, async t => {
  const ui = await open(t);
  ui.document.querySelector('#item-cantidad').value = amount;
  ui.document.querySelector('#item-bonif').value = discount;
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, amount === '0' ? 0 : 1);
});

test('dollar invoices display USD rather than pesos', async t => {
  const ui = await open(t, { invoices: [{ ...invoice, codigoMoneda: 'DOL' }] });
  assert.match(ui.document.querySelector('#tabla-facturas tbody tr').children[6].textContent, /US\$/);
});

test('unknown currency remains visible instead of silently becoming pesos', async t => {
  const ui = await open(t, { invoices: [{ ...invoice, codigoMoneda: 'XYZ' }] });
  assert.match(ui.document.querySelector('#tabla-facturas tbody tr').children[6].textContent, /XYZ/);
});

test('changing currency updates the estimate format', async t => {
  const ui = await open(t);
  ui.click('#btn-agregar-item');
  const select = ui.document.querySelector('[name="tipoMonedaId"]');
  select.value = '2';
  select.dispatchEvent(new ui.document.defaultView.Event('change'));
  assert.match(ui.document.querySelector('#total-items').textContent, /US\$/);
});

test('API origin can be configured without changing compiled JavaScript', async t => {
  const ui = await open(t, { apiBase: 'https://facturas.example/api' });
  assert.ok(ui.requests.every(r => r.url.startsWith('https://facturas.example/api/')));
  assert.match(ui.document.querySelector('#link-pdf').href, /^https:\/\/facturas\.example\/api\/facturas\/reporte/);
});

test('configured API base normalizes trailing slash', async t => {
  const ui = await open(t, { apiBase: 'https://facturas.example/api/' });
  assert.equal(ui.requests[0].url, 'https://facturas.example/api/catalogos/puntos-venta');
});

test('unfinished next item does not invalidate an invoice with saved rows', async t => {
  const ui = await open(t);
  ui.click('#btn-agregar-item');
  const quantity = ui.document.querySelector('#item-cantidad');
  quantity.value = '0';
  assert.equal(quantity.checkValidity(), false);
  assert.equal(ui.document.querySelector('#form-factura').checkValidity(), true);
});

test('invalid pending discount stays in item editor without blocking invoice save', async t => {
  const ui = await open(t);
  ui.click('#btn-agregar-item');
  const discount = ui.document.querySelector('#item-bonif');
  discount.value = '101';
  assert.equal(discount.checkValidity(), false);
  assert.equal(ui.document.querySelector('#form-factura').checkValidity(), true);
  ui.submit();
  await tick();
  assert.equal(ui.requests.filter(r => r.method === 'POST').length, 1);
});

test('double clicking Emitir sends only one PATCH while the first is pending', async t => {
  const pending = deferred();
  const ui = await open(t, { invoices: [createdInvoice], patch: () => pending.promise });
  ui.click('[data-accion="emitir"]');
  ui.click('[data-accion="emitir"]');
  assert.equal(ui.requests.filter(r => r.method === 'PATCH').length, 1);
  pending.resolve(response(invoice));
  await tick();
});

test('double clicking Anular sends only one PATCH while the first is pending', async t => {
  const pending = deferred();
  const ui = await open(t, { invoices: [invoice], prompt: '', patch: () => pending.promise });
  ui.click('[data-accion="anular"]');
  ui.click('[data-accion="anular"]');
  assert.equal(ui.requests.filter(r => r.method === 'PATCH').length, 1);
  pending.resolve(response({ ...invoice, estado: 'ANULADA' }));
  await tick();
});

test('client explorer searches, selects the ID, and can restore Consumidor Final', async t => {
  const clients = Array.from({ length: 12 }, (_, i) => ({ id: i + 11, etiqueta: `Cliente ${i + 1} (20${String(i).padStart(9, '0')})`, valor: null }));
  const ui = await open(t, { catalogs: { clients } });
  ui.click('#btn-buscar-cliente');
  assert.equal(ui.document.querySelector('#dialogo-clientes').open, true);
  assert.match(ui.document.querySelector('#pagina-clientes').textContent, /1 de 2/);
  ui.click('#clientes-siguiente');
  assert.ok(ui.document.querySelector('[data-seleccionar-cliente="22"]'));
  ui.document.querySelector('#buscar-cliente').value = 'Cliente 12';
  ui.document.querySelector('#buscar-cliente').dispatchEvent(new ui.document.defaultView.Event('input'));
  assert.match(ui.document.querySelector('#pagina-clientes').textContent, /1 de 1/);
  ui.click('[data-seleccionar-cliente="22"]');
  assert.equal(ui.document.querySelector('[name="clienteId"]').value, '22');
  assert.match(ui.document.querySelector('#cliente-elegido').textContent, /Cliente 12/);
  ui.click('#btn-consumidor-final');
  assert.equal(ui.document.querySelector('[name="clienteId"]').value, '');
  assert.equal(ui.document.querySelector('#cliente-elegido').textContent, 'Consumidor Final');
});

test('new invoice requires an explicit article choice from the explorer', async t => {
  const ui = await open(t, { selectArticle: false });
  assert.equal(ui.document.querySelector('#item-articulo').value, '');
  assert.equal(ui.document.querySelector('#item-articulo-elegido').textContent, 'Sin artículo seleccionado');
  ui.click('#btn-agregar-item');
  assert.equal(ui.document.querySelector('#tabla-items tbody').children.length, 0);
  assert.match(ui.document.querySelector('#mensaje').textContent, /Seleccioná un artículo/);
});

test('new invoice adds only the article explicitly chosen from its price list', async t => {
  const ui = await open(t, { selectArticle: false, catalogs: { articlesByList: { 10: [
    { id: 101, etiqueta: 'A - Arroz', valor: 120, listaPrecioId: 10 },
    { id: 102, etiqueta: 'B - Aceite', valor: 85, listaPrecioId: 10 },
  ] } } });
  ui.click('#btn-buscar-articulo');
  ui.click('[data-seleccionar-articulo="102"]');
  assert.equal(ui.document.querySelector('#item-articulo').value, '102');
  ui.click('#btn-agregar-item');
  assert.match(ui.document.querySelector('#tabla-items tbody').textContent, /Aceite/);
  ui.submit();
  await tick();
  assert.equal(JSON.parse(ui.requests.find(r => r.method === 'POST').body).detalles[0].listaPrecioArticuloId, 102);
});

test('article explorer filters by explicit price list and saves the selected article ID', async t => {
  const catalogs = {
    lists: [{ id: 10, etiqueta: 'Minorista', valor: null }, { id: 20, etiqueta: 'Mayorista', valor: null }],
    articlesByList: {
      10: [{ id: 101, etiqueta: 'A - Arroz', valor: 120, listaPrecioId: 10 }],
      20: [{ id: 202, etiqueta: 'B - Aceite', valor: 85.5, listaPrecioId: 20 }],
    },
  };
  const ui = await open(t, { catalogs });
  const list = ui.document.querySelector('#lista-precio');
  list.value = '20';
  list.dispatchEvent(new ui.document.defaultView.Event('change'));
  await tick();
  assert.ok(ui.requests.some(r => r.url.endsWith('/catalogos/lista-precio-articulos?listaPrecioId=20')));
  ui.click('#btn-buscar-articulo');
  assert.equal(ui.document.querySelector('[data-seleccionar-articulo="101"]'), null);
  ui.click('[data-seleccionar-articulo="202"]');
  assert.equal(ui.document.querySelector('#item-articulo').value, '202');
  assert.match(ui.document.querySelector('#item-precio').textContent, /85,50/);
  ui.click('#btn-agregar-item');
  ui.submit();
  await tick();
  assert.equal(JSON.parse(ui.requests.find(r => r.method === 'POST').body).detalles[0].listaPrecioArticuloId, 202);
});

test('article explorer paginates search results and clears selection when price list changes', async t => {
  const articles = Array.from({ length: 12 }, (_, i) => ({ id: i + 100, etiqueta: `Artículo ${i + 1}`, valor: i + 1, listaPrecioId: 10 }));
  const ui = await open(t, { catalogs: { lists: [{ id: 10, etiqueta: 'A', valor: null }, { id: 20, etiqueta: 'B', valor: null }], articlesByList: { 10: articles, 20: [{ id: 999, etiqueta: 'Otro', valor: 99, listaPrecioId: 20 }] } } });
  ui.click('#btn-buscar-articulo');
  assert.match(ui.document.querySelector('#pagina-articulos').textContent, /1 de 2/);
  ui.click('#articulos-siguiente');
  ui.click('[data-seleccionar-articulo="111"]');
  assert.equal(ui.document.querySelector('#item-articulo').value, '111');
  const list = ui.document.querySelector('#lista-precio');
  list.value = '20';
  list.dispatchEvent(new ui.document.defaultView.Event('change'));
  await tick();
  assert.equal(ui.document.querySelector('#item-articulo').value, '');
  assert.equal(ui.document.querySelector('#item-articulo-elegido').textContent, 'Sin artículo seleccionado');
});

test('article explorer searches codes and accented names within the chosen list', async t => {
  const ui = await open(t, { catalogs: { articlesByList: { 10: [
    { id: 100, etiqueta: 'A1 - Café molido', valor: 150, listaPrecioId: 10 },
    { id: 101, etiqueta: 'B2 - Té negro', valor: 80, listaPrecioId: 10 },
  ] } } });
  ui.click('#btn-buscar-articulo');
  const input = ui.document.querySelector('#buscar-articulo');
  input.value = 'cafe'; input.dispatchEvent(new ui.document.defaultView.Event('input'));
  assert.ok(ui.document.querySelector('[data-seleccionar-articulo="100"]'));
  assert.equal(ui.document.querySelector('[data-seleccionar-articulo="101"]'), null);
  input.value = 'sin resultado'; input.dispatchEvent(new ui.document.defaultView.Event('input'));
  assert.match(ui.document.querySelector('#resultados-articulos').textContent, /No se encontraron/);
});

test('editing restores the invoice customer, price list, and day-first date', async t => {
  const article10 = { id: 101, etiqueta: 'A - Arroz', valor: 120, listaPrecioId: 10 };
  const article20 = { id: 202, etiqueta: 'B - Aceite', valor: 85, listaPrecioId: 20 };
  const catalogs = {
    clients: [{ id: 5, etiqueta: 'Cliente elegido (20123456789)', valor: null }],
    lists: [{ id: 10, etiqueta: 'Minorista', valor: null }, { id: 20, etiqueta: 'Mayorista', valor: null }],
    articlesByList: { 10: [article10], 20: [article20] },
    allArticles: [article10, article20],
  };
  const draft = { ...createdInvoice, id: 2 };
  const ui = await open(t, { invoices: [draft], catalogs, get: url => /facturas\/2$/.test(url) ? response({ ...detail(2), clienteId: 5, clienteDenominacion: 'Cliente elegido', fechaEmision: '2026-11-09', detalles: [{ listaPrecioArticuloId: 202, cantidad: 1, precioUnitario: 85, descripcion: 'Aceite' }] }) : response([draft]) });
  ui.click('[data-accion="editar"]'); await tick();
  assert.equal(ui.document.querySelector('#lista-precio').value, '20');
  assert.equal(ui.document.querySelector('[name="clienteId"]').value, '5');
  assert.match(ui.document.querySelector('#cliente-elegido').textContent, /Cliente elegido/);
  assert.equal(ui.document.querySelector('[name="fechaEmision"]').value, '09/11/2026');
  assert.equal(ui.document.querySelector('#item-articulo').value, '');
});

test('invoice and filter dates display day first while API receives ISO dates', async t => {
  const ui = await open(t);
  const date = ui.document.querySelector('[name="fechaEmision"]');
  assert.equal(date.type, 'text');
  assert.match(date.value, /^\d{2}\/\d{2}\/\d{4}$/);
  date.value = '09/11/2026';
  ui.document.querySelector('[name="fechaDesde"]').value = '01/11/2026';
  ui.document.querySelector('[name="fechaHasta"]').value = '30/11/2026';
  ui.document.querySelector('#form-filtros').dispatchEvent(new ui.document.defaultView.Event('submit', { cancelable: true }));
  await tick();
  assert.ok(ui.requests.some(r => r.url.includes('fechaDesde=2026-11-01') && r.url.includes('fechaHasta=2026-11-30')));
  ui.click('#btn-agregar-item'); ui.submit(); await tick();
  assert.equal(JSON.parse(ui.requests.find(r => r.method === 'POST').body).fechaEmision, '2026-11-09');
});

test('impossible day-first dates are rejected before sending invoice or filters', async t => {
  const ui = await open(t);
  ui.document.defaultView.console.error = () => {};
  ui.document.querySelector('[name="fechaEmision"]').value = '31/02/2026';
  ui.click('#btn-agregar-item'); ui.submit();
  assert.equal(ui.requests.filter(r => r.method === 'POST').length, 0);
  assert.match(ui.document.querySelector('#mensaje').textContent, /fecha/i);
  ui.document.querySelector('[name="fechaDesde"]').value = '32/11/2026';
  const getCount = ui.requests.filter(r => r.url.includes('/facturas') && !r.method).length;
  ui.document.querySelector('#form-filtros').dispatchEvent(new ui.document.defaultView.Event('submit', { cancelable: true }));
  await tick();
  assert.equal(ui.requests.filter(r => r.url.includes('/facturas') && !r.method).length, getCount);
});

test('invoice cannot be saved with an empty issue date even if submit is dispatched directly', async t => {
  const ui = await open(t);
  ui.document.querySelector('[name="fechaEmision"]').value = '';
  ui.click('#btn-agregar-item'); ui.submit();
  assert.equal(ui.requests.filter(r => r.method === 'POST').length, 0);
  assert.match(ui.document.querySelector('#mensaje').textContent, /Fecha de emisión/);
});

test('print button opens invoice PDF for issued and annulled invoices only', async t => {
  const ui = await open(t, { invoices: [createdInvoice, { ...invoice, id: 2 }, { ...invoice, id: 3, estado: 'ANULADA' }] });
  assert.equal(ui.document.querySelector('[data-accion="imprimir"][data-id="1"]'), null);
  ui.click('[data-accion="imprimir"][data-id="2"]');
  ui.click('[data-accion="imprimir"][data-id="3"]');
  assert.deepEqual(ui.openedUrls.at(-1), ['http://localhost:8080/api/facturas/3/pdf', '_blank', 'noopener']);
  assert.equal(ui.openedUrls.length, 2);
});

test('invoice list paginates and a new filter resets to its first page', async t => {
  const invoices = Array.from({ length: 12 }, (_, i) => ({ ...invoice, id: i + 1, numeroFactura: i + 1 }));
  const ui = await open(t, { invoices, get: url => response(url.includes('estado=BORRADOR') ? [createdInvoice] : invoices) });
  assert.equal(ui.document.querySelector('#tabla-facturas tbody').children.length, 10);
  assert.match(ui.document.querySelector('#pagina-facturas').textContent, /1 de 2/);
  ui.click('#facturas-siguiente');
  assert.equal(ui.document.querySelector('#tabla-facturas tbody').children.length, 2);
  search(ui, 'BORRADOR'); await tick();
  assert.match(ui.document.querySelector('#pagina-facturas').textContent, /1 de 1/);
  assert.equal(ui.document.querySelector('#tabla-facturas tbody').children.length, 1);
});
