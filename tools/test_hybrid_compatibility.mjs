import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
let acorn;
try {
  acorn = require('acorn');
} catch {
  throw new Error('Acorn is required; install/use it ephemerally and expose it with NODE_PATH');
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const hybrid = resolve(root, 'app/src/hybrid/assets/hybrid');
const read = name => readFile(resolve(hybrid, name), 'utf8');
const walk = (node, visit) => {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) return node.forEach(child => walk(child, visit));
  if (typeof node.type === 'string') visit(node);
  for (const [key, value] of Object.entries(node)) {
    if (key !== 'loc' && key !== 'start' && key !== 'end') walk(value, visit);
  }
};
const parse = (source, ecmaVersion) => acorn.parse(source, {
  ecmaVersion,
  sourceType: 'module',
  locations: true,
});
const Chrome69Parser = acorn.Parser.extend(Base => class extends Base {
  parseStatement(context, topLevel, exports) {
    if (this.type === acorn.tokTypes._import) {
      const remaining = this.input.slice(this.pos);
      const skipped = remaining.match(/^(?:\s|\/\/[^\n\r]*|\/\*[\s\S]*?\*\/)*/)?.[0]?.length;
      if (['(', '.'].includes(remaining[skipped ?? 0])) {
        return this.parseExpressionStatement(this.startNode(), this.parseExpression());
      }
    }
    return super.parseStatement(context, topLevel, exports);
  }
  parseExprAtom(refDestructuringErrors, forInit, forNew) {
    if (this.type === acorn.tokTypes._import) return this.parseExprImport(forNew);
    return super.parseExprAtom(refDestructuringErrors, forInit, forNew);
  }
});
const parseChrome69 = source => Chrome69Parser.parse(source, {
  ecmaVersion: 2019,
  sourceType: 'module',
  locations: true,
});

// Chrome 69 / Android WebView 69 supports import.meta in modules (since Chrome 64),
// although Acorn gates that grammar behind its ES2020 parser setting. Parse using that
// grammar, then explicitly reject only the ES2020 optional-chain/nullish operators
// that the target runtime does not implement.
for (const name of ['selection.mjs', 'selection-geometry.mjs']) {
  const ast = parse(await read(name), 2020);
  const unsupported = [];
  walk(ast, node => {
    if (node.type === 'ChainExpression' ||
        (node.type === 'LogicalExpression' && node.operator === '??')) {
      unsupported.push(`${node.type === 'ChainExpression' ? 'optional chaining' : 'nullish coalescing'} at line ${node.loc.start.line}`);
    }
  });
  assert.deepEqual(unsupported, [], `${name} uses syntax unsupported by WebView 69: ${unsupported.join(', ')}`);
}

// Bundled PDF.js is third-party and outside this fix: parse it at modern grammar
// level and report (rather than fail on) unsupported operators as diagnostic evidence.
for (const name of ['pdf.mjs', 'pdf.worker.mjs']) {
  const ast = parse(await read(name), 'latest');
  let optionalChains = 0;
  let nullishCoalescing = 0;
  walk(ast, node => {
    if (node.type === 'ChainExpression') optionalChains++;
    if (node.type === 'LogicalExpression' && node.operator === '??') nullishCoalescing++;
  });
  console.log(`third-party ${name}: parsed with latest grammar; optional chains=${optionalChains}, nullish operators=${nullishCoalescing} (diagnostic only)`);
}

// PDF.js assets shipped to Android WebView 69 must use syntax its JS parser accepts.
for (const name of ['pdf.mjs', 'pdf.worker.mjs']) {
  parseChrome69(await read(name));
}

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const vendorDir = resolve(root, 'third_party/vendor/pdfjs-4.10.38');
const sourceNames = ['pdf.mjs', 'pdf.worker.mjs'];
const sourceHashes = Object.fromEntries(await Promise.all(sourceNames.map(async name => [
  name,
  sha256(await readFile(resolve(vendorDir, name))),
])));
const recordedChecksums = Object.fromEntries((await readFile(resolve(root, 'third_party/licenses/hybrid/pdfjs-4.10.38.sha256'), 'utf8'))
  .trim().split('\n').map(line => {
    const [, hash, path] = line.match(/^([0-9a-f]{64})\s+(.+)$/) ?? [];
    assert.ok(hash && path, `invalid PDF.js checksum line: ${line}`);
    return [path, hash];
  }));
for (const name of sourceNames) {
  const path = `third_party/vendor/pdfjs-4.10.38/${name}`;
  assert.equal(sourceHashes[name], recordedChecksums[path], `${name} vendor bytes differ from the original checksum`);
}
const buildInfo = JSON.parse(await read(resolve(hybrid, 'pdfjs-build.json')));
assert.equal(buildInfo.pdfjsVersion, '4.10.38');
assert.equal(buildInfo.esbuildVersion, '0.25.12');
assert.deepEqual(buildInfo.target, ['chrome69']);
assert.deepEqual(buildInfo.sources, sourceHashes);
for (const name of sourceNames) {
  const output = await read(name);
  assert.equal(sha256(Buffer.from(output)), buildInfo.outputs[name], `${name} output checksum mismatch`);
  assert.notEqual(buildInfo.outputs[name], sourceHashes[name], `${name} was not transpiled`);
  const original = await readFile(resolve(vendorDir, name), 'utf8');
  const licenseNotice = original.match(/^\/\*\*[\s\S]*?@licend[\s\S]*?\*\//)?.[0];
  assert.ok(licenseNotice && output.startsWith(licenseNotice), `${name} must preserve its original license notice`);
  assert.match(output, /Modified by Commonplace Reader: transpiled with esbuild 0\.25\.12 for Chrome 69/);
}
const license = await readFile(resolve(root, 'app/src/hybrid/assets/third-party/PDFjs-4.10.38-LICENSE'), 'utf8');
assert.match(license, /Apache License\s+Version 2\.0, January 2004/);

console.log('hybrid WebView 69 syntax, PDF.js provenance, pinned build, and license checks passed');
