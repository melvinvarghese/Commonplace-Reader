# Hybrid app-proof preparation implementation plan

> **For agentic workers:** Use the executing-plans skill task by task. The owner has already authorized this implementation; no additional plan approval is required. Do not commit or push.

**Goal:** Prepare the disposable hybrid prototype for a separately authorized API 28/37 app-level proof, with reproducible local assets and trustworthy diagnostics.

**Architecture:** Retain native PDF rendering and the offline PDF.js DOM text layer. Preserve original vendor inputs and licenses; package pinned Chrome 69-transpiled outputs. Keep frame-generation checks and production checksum uniqueness intact.

**Tech Stack:** Kotlin/Android, PDF.js 4.10.38, esbuild 0.25.12, Node tests, Python harness tests, Gradle JVM/lint/APK tasks.

**Spec:** Owner's approved app-level test-plan implementation scope; existing `docs/HYBRID_PDF_PROOF.md` and failed API 28/37 evidence.

## Global constraints

- Local only: no new CI, device, emulator, commit or push.
- Preserve all existing uncommitted work; initial diff and untracked files snapshotted outside the worktree at `/tmp/hybrid-before/`.
- PDF remains **NO SELECTION**; minSdk 28, architecture decision and rejected flavor history unchanged.
- Keep exactness thresholds **≤1 PDF point** and **≤2 dp** unchanged.
- Device-only gates are not passed by local tests or builds.

## Review focus

- Modules that cannot parse must report boot failures without depending on successful module evaluation.
- Failed or superseded page/zoom/frame work must not restore readiness or selection.
- Duplicate test fixture checksums must not bypass the production unique constraint or alter pinned bytes.
- Generated main/worker bundles must be the exact assets packaged in the APK, with untouched provenance and licenses.
- Diagnostics must be collected before the future emulator terminates, on failure as well as success.

## Tasks

### 1. Reproducible offline vendor assets

- [ ] Add failing local verification for target grammar, provenance and generated assets.
- [ ] Pin PDF.js inputs and esbuild toolchain; produce deterministic Chrome 69 main/worker outputs.
- [ ] Wire asset generation into hybrid APK packaging and verify final APK asset bytes/licenses.
- [ ] Run compatibility and reproducibility checks without a browser/device.

### 2. Test fixture isolation and future live-device evidence

- [ ] Reproduce duplicate-checksum setup failure locally with a real database regression where feasible.
- [ ] Isolate fixtures without altering production uniqueness or original fixture bytes.
- [ ] Test live-emulator log collection via fake adb, including failure paths.
- [ ] Prepare real gates for worker parsing, native visibility, DOM touch, exact mapping/overlay, true restart restoration, changed bytes and offline resources; do not execute them.

### 3. Startup/runtime observability and stale work rejection

- [ ] Add failing executable tests for boot/module/global errors and readiness transitions.
- [ ] Install a classic pre-module error listener and expose frame-correlated diagnostic snapshots from native state.
- [ ] Preserve frame handshake and reject stale responses, including errors from superseded frames.
- [ ] Run Node and Kotlin regression tests.

### 4. Integrate, verify, and report

- [ ] Run all local Python/Node tests, strict hybrid JVM/lint/APK builds and APK verification.
- [ ] Obtain an independent local diff review; fix important findings with regression tests.
- [ ] Update proof notes with current local evidence and separately list each still-unrun device gate.
- [ ] Leave all changes uncommitted for owner review.

## Execution ledger

- Initial inspection: HEAD and origin/main `e2f8db1`; three modified tracked files and four untracked files preserved. Existing module/handshake follow-up is retained, not reset.
- Pre-flight: independent vendor and fixture/harness tasks delegated with disjoint file ownership; parent owns runtime diagnostics, integration, proof documentation and final verification. No interface changes to production saved locations or checksums planned.
