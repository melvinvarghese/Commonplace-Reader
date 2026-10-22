import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { transform, version } from 'esbuild';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const vendorDir = resolve(root, 'third_party/vendor/pdfjs-4.10.38');
const outputDir = resolve(root, 'app/src/hybrid/assets/hybrid');
const checksumPath = resolve(root, 'third_party/licenses/hybrid/pdfjs-4.10.38.sha256');
const pdfjsVersion = '4.10.38';
const esbuildVersion = '0.25.12';
const target = ['chrome69'];
const names = ['pdf.mjs', 'pdf.worker.mjs'];

if (version !== esbuildVersion) {
  throw new Error(`Expected esbuild ${esbuildVersion}, found ${version}; run npm ci from the pinned lockfile`);
}

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const checksums = Object.fromEntries((await readFile(checksumPath, 'utf8')).trim().split('\n').map(line => {
  const match = line.match(/^([0-9a-f]{64})\s+(.+)$/);
  if (!match) throw new Error(`Invalid checksum entry: ${line}`);
  return [match[2], match[1]];
}));
const sources = {};
const outputs = {};
await mkdir(outputDir, { recursive: true });

for (const name of names) {
  const sourcePath = resolve(vendorDir, name);
  const sourceBytes = await readFile(sourcePath);
  const sourceHash = sha256(sourceBytes);
  const recordedHash = checksums[`third_party/vendor/pdfjs-${pdfjsVersion}/${name}`];
  if (sourceHash !== recordedHash) {
    throw new Error(`Original PDF.js ${name} checksum mismatch: expected ${recordedHash ?? 'no manifest entry'}, got ${sourceHash}`);
  }
  sources[name] = sourceHash;

  const source = sourceBytes.toString('utf8');
  const licenseNotice = source.match(/^\/\*\*[\s\S]*?@licend[\s\S]*?\*\//)?.[0];
  if (!licenseNotice) throw new Error(`Original PDF.js ${name} license notice is missing`);
  const result = await transform(source, {
    banner: `${licenseNotice}\n// Modified by Commonplace Reader: transpiled with esbuild ${esbuildVersion} for Chrome 69; original inputs retained in third_party/vendor/pdfjs-${pdfjsVersion}/.`,
    format: 'esm',
    loader: 'js',
    legalComments: 'inline',
    sourcefile: name,
    target: 'chrome69',
  });
  const outputBytes = Buffer.from(result.code, 'utf8');
  await writeFile(resolve(outputDir, name), outputBytes);
  outputs[name] = sha256(outputBytes);
}

const buildInfo = {
  pdfjsVersion,
  esbuildVersion,
  target,
  sources,
  outputs,
};
await writeFile(resolve(outputDir, 'pdfjs-build.json'), `${JSON.stringify(buildInfo, null, 2)}\n`);
console.log(`Generated PDF.js ${pdfjsVersion} modules for Chrome 69 with esbuild ${version}`);
