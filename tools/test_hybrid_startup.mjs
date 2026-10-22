import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const scriptUrl = new URL('../app/src/hybrid/assets/hybrid/selection.mjs', import.meta.url);
const source = await readFile(scriptUrl, 'utf8');
const geometrySource = await readFile(new URL('../app/src/hybrid/assets/hybrid/selection-geometry.mjs', import.meta.url), 'utf8');
const frame = (requestId, pageIndex = 0, zoom = 1) => ({ requestId, pageIndex, zoom,
  widthPx: 612, heightPx: 792, leftPx: 0, topPx: 0, density: 1 });
const deferred = () => {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
};

// Only the browser DOM and third-party PDF parser are substituted. The actual
// first-party module owns frame queuing, cancellation and all bridge messages.
async function overlay({ legacyDom = false, workerMode = "real-worker", workerFailure = null, render = async () => {}, fetchPdf = async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(1) }) } = {}) {
  const messages = [];
  const host = { style: {}, firstChild: null,
    removeChild() { this.firstChild = null; }, appendChild(child) { this.firstChild = child; } };
  if (!legacyDom) host.replaceChildren = () => {};
  const globals = {
    URL, fetch: fetchPdf, Worker: class Worker {},
    document: { querySelector: () => host, createElement: () => ({ style: { setProperty() {} }, querySelectorAll: () => [] }) },
    HybridBridge: { message: json => {
      const message = JSON.parse(json);
      if (message.type === 'scriptReady') assert.equal(typeof globals.hybridFrame, 'function');
      messages.push(message);
    } },
    addEventListener() {},
  };
  globals.window = globals;
  const context = vm.createContext(globals);
  const page = { rotate: 0, view: [0, 0, 612, 792], getTextContent: async () => ({}),
    getViewport: ({ scale }) => ({ scale, width: 612 * scale, height: 792 * scale, transform: [scale, 0, 0, scale, 0, 0] }) };
  const pdf = { numPages: 2, getPage: async () => page };
  const pdfjs = new vm.SyntheticModule(['GlobalWorkerOptions', 'PDFWorker', 'getDocument', 'TextLayer'], function () {
    this.setExport('GlobalWorkerOptions', {});
    this.setExport('PDFWorker', class {
      constructor() {
        this.port = workerMode === 'real-worker' ? new globals.Worker() : {};
        this.promise = workerFailure ? Promise.reject(new Error(workerFailure)) : Promise.resolve();
      }
    });
    this.setExport('getDocument', () => ({ promise: Promise.resolve(pdf) }));
    this.setExport('TextLayer', class { render() { return render(); } });
  }, { context });
  const worker = new vm.SyntheticModule([], function () {}, { context });
  const geometry = new vm.SourceTextModule(geometrySource, { context });
  const module = new vm.SourceTextModule(source, { context, initializeImportMeta: meta => { meta.url = scriptUrl.href; } });
  await module.link(specifier => ({ './pdf.mjs': pdfjs, './pdf.worker.mjs': worker, './selection-geometry.mjs': geometry })[specifier]);
  await module.evaluate();
  return { globals, messages };
}

test('module announces its installed receiver without relying on a DOMContentLoaded event', async () => {
  const { messages } = await overlay();
  assert.equal(messages.filter(message => message.type === 'scriptReady').length, 1);
  assert.ok(messages.some(message => message.type === 'diagnostic' && message.stage === 'document-fetch'));
});

test('readiness echoes the exact request, page and zoom sent by native rendering', async () => {
  const { globals, messages } = await overlay();
  await globals.hybridFrame(frame(12, 1, 2));
  const ready = messages.filter(message => message.type === 'ready');
  assert.equal(ready.length, 1);
  assert.deepEqual(ready[0].frame, frame(12, 1, 2));
  assert.equal(ready[0].pageIndex, 1);
});

test('a newer frame suppresses readiness from an older asynchronous text render', async () => {
  const started = deferred(), finish = deferred();
  let renders = 0;
  const { globals, messages } = await overlay({ render: async () => {
    if (++renders === 1) { started.resolve(); await finish.promise; }
  } });
  const old = globals.hybridFrame(frame(1));
  await started.promise;
  const current = globals.hybridFrame(frame(2, 1, 2));
  finish.resolve();
  await Promise.all([old, current]);
  assert.deepEqual(messages.filter(message => message.type === 'ready').map(message => message.frame.requestId), [2]);
});

test('an uncorrelated frame never produces readiness', async () => {
  const { globals, messages } = await overlay();
  const uncorrelated = frame(1);
  delete uncorrelated.requestId;
  await globals.hybridFrame(uncorrelated);
  assert.equal(messages.filter(message => message.type === 'ready').length, 0);
  assert.equal(messages.filter(message => message.type === 'error').length, 1);
});

test('startup fetch failure is reported rather than silently waiting for a frame', async () => {
  const { messages } = await overlay({ fetchPdf: async () => { throw new Error('local source unavailable'); } });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(messages.filter(message => message.type === 'error').length, 1);
});

test('document parsing reports whether the PDF worker is real or an in-process fallback', async () => {
  for (const workerMode of ['real-worker', 'in-process']) {
    const { globals, messages } = await overlay({ workerMode });
    await globals.hybridFrame(frame(1));
    const parsed = messages.find(message => message.stage === 'document-loaded');
    assert.equal(parsed.workerMode, workerMode);
  }
});
test('worker startup failure is surfaced without reporting text readiness', async () => {
  const { messages } = await overlay({ workerFailure: 'worker could not start' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(messages.filter(message => message.type === 'ready').length, 0);
  assert.ok(messages.some(message => message.type === 'error' && /worker could not start/.test(message.message)));
});
test('an obsolete asynchronous render failure cannot fail the current frame', async () => {
  const started = deferred(), finish = deferred();
  let renders = 0;
  const { globals, messages } = await overlay({ render: async () => {
    if (++renders === 1) { started.resolve(); await finish.promise; throw new Error('obsolete page failed'); }
  } });
  const old = globals.hybridFrame(frame(1));
  await started.promise;
  const current = globals.hybridFrame(frame(2, 1));
  finish.resolve();
  await Promise.all([old, current]);
  assert.equal(messages.filter(message => message.type === 'error').length, 0);
  assert.deepEqual(messages.filter(message => message.type === 'ready').map(message => message.frame.requestId), [2]);
});

test('renders with the Chrome 69 DOM without Element.replaceChildren', async () => {
  const { globals, messages } = await overlay({ legacyDom: true });
  await globals.hybridFrame(frame(21));
  assert.equal(messages.filter(message => message.type === 'ready').length, 1);
  assert.equal(messages.filter(message => message.type === 'error').length, 0);
});
