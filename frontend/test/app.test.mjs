import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { JSDOM } from 'jsdom';

const invoice = { id: 1, numeroFactura: 10, fechaEmision: '2026-10-29', clienteDenominacion: 'Cliente', condicionIva: 'RI', puntoVentaDescripcion: 'Principal', importeTotal: 121, cantidadItems: 1, estado: 'EMITIDA', codigoMoneda: 'PES' };
const createdInvoice = { ...invoice, numeroFactura: null, estado: 'BORRADOR' };
const tick = () => new Promise(resolve => setImmediate(resolve));

async function open(t, { invoices = [], article = 'Artículo', post, patch, get, prompt = null, apiBase } = {}) {
  let html = await readFile(new URL('../index.html', import.meta.url), 'utf8');
  if (apiBase) html = html.replace(/<meta name="api-base-url"[^>]*>/, '').replace('</head>', `<meta name="api-base-url" content="${apiBase}"></head>`);
  const dom = new JSDOM(html, { url: 'http://localhost:5173', runScripts: 'outside-only' });
  t.after(() => dom.window.close());
  const requests = [];
  dom.window.prompt = () => prompt;
  dom.window.HTMLElement.prototype.scrollIntoView = () => {};
  dom.window.fetch = async (url, init = {}) => {
    requests.push({ url, ...init });
    let body;
    if (init.method === 'POST') return post ? post() : { ok: true, status: 201, json: async () => createdInvoice };
    if (init.method === 'PATCH' && patch) return patch();
    if (init.method === 'PATCH') body = { ...invoice, estado: 'ANULADA' };
    else if (url.includes('/catalogos/')) {
      body = url.endsWith('/tipos-moneda')
        ? [{ id: 1, etiqueta: 'Peso ($)', valor: null, codigoMoneda: 'PES' }, { id: 2, etiqueta: 'Dólar (U$S)', valor: null, codigoMoneda: 'DOL' }]
        : [{ id: 1, etiqueta: url.endsWith('lista-precio-articulos') ? article : 'Opción', valor: 100 }];
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
  return { document, requests, click: selector => document.querySelector(selector).click(), submit: () => document.querySelector('#form-factura').dispatchEvent(new dom.window.Event('submit', { cancelable: true })) };
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
