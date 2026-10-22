import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

async function boot() {
  const messages = [], listeners = {};
  const globals = {
    HybridBridge: { message: text => messages.push(JSON.parse(text)) },
    addEventListener: (type, handler) => { listeners[type] = handler; },
  };
  globals.window = globals;
  const source = await readFile(new URL('../app/src/hybrid/assets/hybrid/bootstrap.js', import.meta.url), 'utf8')
    .catch(error => { if (error.code === 'ENOENT') return ''; throw error; });
  vm.runInNewContext(source, globals);
  return { messages, listeners };
}
test('reports classic boot before loading the module graph', async () => {
  const { messages } = await boot();
  assert.deepEqual(messages, [{ type: 'diagnostic', stage: 'bootstrap-ready' }]);
});
test('reports a module load/parse resource failure even if the module never ran', async () => {
  const { messages, listeners } = await boot();
  listeners.error({ target: { src: 'https://commonplace.invalid/hybrid/selection.mjs' } });
  assert.equal(messages[1].type, 'error');
  assert.equal(messages[1].stage, 'global-error');
  assert.match(messages[1].message, /selection.mjs/);
});
test('reports runtime location and unhandled promise rejection', async () => {
  const { messages, listeners } = await boot();
  listeners.error({ message: 'runtime failure', filename: 'selection.mjs', lineno: 42 });
  listeners.unhandledrejection({ reason: new Error('worker unavailable') });
  assert.match(messages[1].message, /runtime failure.*selection.mjs:42/);
  assert.equal(messages[2].stage, 'unhandled-rejection');
  assert.match(messages[2].message, /worker unavailable/);
});
