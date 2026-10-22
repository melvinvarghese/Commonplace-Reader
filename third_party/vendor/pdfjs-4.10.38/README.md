# PDF.js 4.10.38 hybrid WebView inputs

`pdf.mjs` and `pdf.worker.mjs` are the untouched vendored PDF.js 4.10.38 browser modules. Their SHA-256 values are recorded in `third_party/licenses/hybrid/pdfjs-4.10.38.sha256`; the Apache-2.0 license is retained in `third_party/licenses/hybrid/PDFjs-Apache-2.0.txt` and the hybrid APK asset license directory.

The shipped `app/src/hybrid/assets/hybrid/pdf.mjs` and `pdf.worker.mjs` are deterministic esbuild 0.25.12 transforms targeting Chrome 69. Rebuild with `npm ci` once to install the exact lockfile dependencies, then run `npm run build:hybrid-pdfjs`. The generator verifies original input hashes and writes `pdfjs-build.json` with pinned tool versions and source/output hashes. Builds use local installed packages only; generation does not fetch code or contact a network.
