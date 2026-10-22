import * as pdfjs from './pdf.mjs';
import * as pdfjsWorker from './pdf.worker.mjs';
import { invertViewportTransform, rectToPdfQuad, assertRestoredQuads } from './selection-geometry.mjs';

pdfjs.GlobalWorkerOptions.workerSrc = new URL('./pdf.worker.mjs', import.meta.url).href;
globalThis.pdfjsWorker = pdfjsWorker;

const host = document.querySelector('#page');
const state = { pdf: null, loading: null, page: null, pageIndex: -1, viewport: null, frame: null, spans: [], queue: Promise.resolve(), generation: 0 };
const finitePositive = value => Number.isFinite(value) && value > 0;
function clearHost() {
  while (host.firstChild) host.removeChild(host.firstChild);
}

function bridge(data) {
  globalThis.HybridBridge.message(JSON.stringify(data));
  return data;
}

function reportError(error) {
  bridge({ type: 'error', message: error instanceof Error ? error.message : String(error), frame: state.frame && { ...state.frame } });
}

async function loadDocument() {
  if (state.pdf) return;
  if (!state.loading) state.loading = (async () => {
    bridge({ type: 'diagnostic', stage: 'document-fetch' });
    const response = await fetch('/document.pdf', { credentials: 'same-origin', redirect: 'error' });
    if (!response.ok) throw new Error(`PDF fetch failed (${response.status})`);
    const bytes = new Uint8Array(await response.arrayBuffer());
    const worker = new pdfjs.PDFWorker({ name: 'commonplace-hybrid' });
    await worker.promise;
    const workerMode = typeof Worker !== 'undefined' && worker.port instanceof Worker ? 'real-worker' : 'in-process';
    bridge({ type: 'diagnostic', stage: 'worker-ready', workerMode });
    const pdf = await pdfjs.getDocument({ data: bytes, worker, isEvalSupported: false, useSystemFonts: false, disableFontFace: false }).promise;
    bridge({ type: 'diagnostic', stage: 'document-loaded', pages: pdf.numPages, workerMode });
    return pdf;
  })();
  try { state.pdf = await state.loading; }
  finally { state.loading = null; }
}

function frameIsValid(frame) {
  return frame && Number.isSafeInteger(frame.requestId) && frame.requestId > 0 &&
    Number.isInteger(frame.pageIndex) && frame.pageIndex >= 0 &&
    finitePositive(frame.widthPx) && finitePositive(frame.heightPx) &&
    [frame.leftPx, frame.topPx, frame.density, frame.zoom].every(Number.isFinite) && frame.density > 0 && frame.zoom > 0;
}

async function renderFrame(frame, generation) {
  if (!frameIsValid(frame)) throw new TypeError('invalid native page frame');
  bridge({ type: 'diagnostic', stage: 'render-start', frame: { ...frame } });
  if (!state.pdf) await loadDocument();
  if (generation !== state.generation) return;
  if (frame.pageIndex >= state.pdf.numPages) throw new RangeError('page index is outside the PDF');

  const page = await state.pdf.getPage(frame.pageIndex + 1);
  if (generation !== state.generation) return;
  const cssWidth = frame.widthPx / frame.density;
  const cssHeight = frame.heightPx / frame.density;
  const baseViewport = page.getViewport({ scale: 1, rotation: page.rotate });
  const viewport = page.getViewport({ scale: cssWidth / baseViewport.width, rotation: page.rotate });
  const layer = document.createElement('div');
  layer.className = 'textLayer';
  layer.style.setProperty('--scale-factor', viewport.scale);
  layer.style.width = `${viewport.width}px`;
  layer.style.height = `${viewport.height}px`;
  clearHost();
  host.appendChild(layer);
  host.style.left = `${frame.leftPx / frame.density}px`;
  host.style.top = `${frame.topPx / frame.density}px`;
  host.style.width = `${cssWidth}px`;
  host.style.height = `${cssHeight}px`;

  const textContent = await page.getTextContent();
  if (generation !== state.generation) return;
  bridge({ type: 'diagnostic', stage: 'text-content-loaded', frame: { ...frame } });
  await new pdfjs.TextLayer({ textContentSource: textContent, container: layer, viewport }).render();
  if (generation !== state.generation) return;
  state.page = page;
  state.pageIndex = frame.pageIndex;
  state.viewport = viewport;
  state.frame = { ...frame };
  state.spans = [...layer.querySelectorAll('span')];
  bridge({ type: 'ready', pageIndex: state.pageIndex, pageBox: [...page.view], rotationDegrees: page.rotate,
    viewportTransform: [...viewport.transform], cssWidth: viewport.width, cssHeight: viewport.height, frame: { ...frame } });
}

window.hybridFrame = frame => {
  const generation = ++state.generation;
  clearHost();
  state.page = null;
  state.viewport = null;
  state.frame = null;
  state.spans = [];
  state.queue = state.queue.then(() => renderFrame(frame, generation)).catch(error => {
    if (generation === state.generation) reportError(error);
  });
  return state.queue;
};

function spanAndOffset(node, offset) {
  const span = node.nodeType === Node.ELEMENT_NODE ? node.closest('span') : node.parentElement && node.parentElement.closest('span');
  if (!span || !host.contains(span)) throw new Error('selection endpoint is outside the active text layer');
  const spanIndex = state.spans.indexOf(span);
  if (spanIndex < 0) throw new Error('selection endpoint is not in a text-layer span');
  const range = document.createRange();
  range.selectNodeContents(span);
  try { range.setEnd(node, offset); } catch { throw new Error('selection endpoint is not a valid span offset'); }
  const textOffset = range.toString().length;
  if (textOffset > span.textContent.length) throw new Error('selection endpoint offset is invalid');
  return { spanIndex, offset: textOffset };
}

function nodeAtSpanOffset(span, offset) {
  if (!Number.isInteger(offset) || offset < 0 || offset > span.textContent.length) throw new Error('saved text offset is invalid');
  const walker = document.createTreeWalker(span, NodeFilter.SHOW_TEXT);
  let node, remaining = offset, last = null;
  while ((node = walker.nextNode())) {
    last = node;
    if (remaining <= node.textContent.length) return [node, remaining];
    remaining -= node.textContent.length;
  }
  if (last && remaining === 0) return [last, last.textContent.length];
  if (!span.textContent.length && span.firstChild) return [span.firstChild, 0];
  throw new Error('saved span has no matching text offset');
}

function rangeData(ranges, quotation, endpoints) {
  const box = host.getBoundingClientRect();
  const clientRects = [];
  const quads = [];
  const inverse = invertViewportTransform([...state.viewport.transform]);
  for (const range of ranges) {
    for (const rect of range.getClientRects()) {
      const local = { x: rect.left - box.left, y: rect.top - box.top, width: rect.width, height: rect.height };
      clientRects.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
      quads.push(rectToPdfQuad(local, inverse));
    }
  }
  const payload = { pageIndex: state.pageIndex, pageBox: [...state.page.view], rotationDegrees: state.page.rotate,
    coordinateSpace: 'unrotated-pdf-points', quotation, quads,
    native: endpoints ? { start: endpoints.start, end: endpoints.end } : undefined };
  return { type: 'selection', quotation, payload, clientRects,
    devicePixelRatio: globalThis.devicePixelRatio,
    viewportTransform: [...state.viewport.transform], frame: { ...state.frame } };
}

window.hybridCapture = () => {
  try {
    const selection = window.getSelection();
    if (!selection || !selection.rangeCount || !state.page || !state.spans.length) throw new Error('there is no active text-layer selection');
    const ranges = [];
    for (let i = 0; i < selection.rangeCount; i++) {
      const range = selection.getRangeAt(i);
      if (!host.contains(range.startContainer) || !host.contains(range.endContainer)) throw new Error('selection must be within the active page');
      ranges.push(range);
    }
    const first = ranges[0], last = ranges[ranges.length - 1];
    const result = rangeData(ranges, selection.toString(), {
      start: spanAndOffset(first.startContainer, first.startOffset),
      end: spanAndOffset(last.endContainer, last.endOffset)
    });
    result.payload.native = { start: result.payload.native.start, end: result.payload.native.end };
    return bridge(result);
  } catch (error) { return bridge({ type: 'captureError', message: String(error) }); }
};

window.hybridRestore = saved => {
  try {
    if (!saved || !state.page || !state.viewport || !state.frame) throw new Error('no active page is ready for restoration');
    const payload = saved.payload && typeof saved.payload === 'object' ? saved.payload : saved;
    const quotation = saved.quotation === null || saved.quotation === undefined ? payload.quotation : saved.quotation;
    if (payload.pageIndex !== state.pageIndex || payload.rotationDegrees !== state.page.rotate ||
        payload.coordinateSpace !== 'unrotated-pdf-points' || !Array.isArray(payload.pageBox) ||
        payload.pageBox.length !== state.page.view.length || payload.pageBox.some((value, i) => value !== state.page.view[i])) {
      throw new Error('saved selection page metadata does not match the active page');
    }
    const { start, end } = payload.native || {};
    if (!start || !end || !Number.isInteger(start.spanIndex) || !Number.isInteger(end.spanIndex) ||
        start.spanIndex < 0 || end.spanIndex < start.spanIndex || end.spanIndex >= state.spans.length ||
        typeof quotation !== 'string') throw new Error('saved span indices or quotation are invalid');
    const [startNode, startOffset] = nodeAtSpanOffset(state.spans[start.spanIndex], start.offset);
    const [endNode, endOffset] = nodeAtSpanOffset(state.spans[end.spanIndex], end.offset);
    const range = document.createRange();
    range.setStart(startNode, startOffset);
    range.setEnd(endNode, endOffset);
    if (range.toString() !== quotation) throw new Error('saved span offsets do not reproduce the exact quotation');
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    const result = rangeData([range], quotation, { start, end });
    assertRestoredQuads(payload.quads, result.payload.quads);
    result.observedQuads = result.payload.quads;
    result.payload = payload;
    result.type = 'restored';
    return bridge(result);
  } catch (error) { return reportError(error); }
};

// Module scripts run after the page DOM is parsed; announce only installed handlers.
bridge({ type: 'scriptReady' });
loadDocument().catch(reportError);
