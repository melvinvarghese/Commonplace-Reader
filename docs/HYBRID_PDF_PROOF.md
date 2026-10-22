# Disposable hybrid PDF proof

**PDF decision: NO SELECTION.** This owner-approved spike is disposable. Even an entirely passing focused run would require a separate owner decision and the remaining release gates before choosing an engine. Neither rejected comparison flavor is replaced.

**The single approved CI attempt failed on both API 28 and API 37.** The reader never reached its combined native-renderer/text-layer ready state, so the run produced no accepted real-selection, alignment or saved-passage evidence. A separate fixture-setup error also blocked changed-file and recovery checks. The separately authorized local follow-up below does not rerun CI, relax thresholds, or change those outcomes.

## What the prototype tries

The `hybrid` flavor (`im.commonplace.reader.hybrid`) places a transparent, selectable PDF.js text layer over AndroidPdfViewer's actual native rendering and gestures. Browse mode forwards touch to the native view; Select text mode lets Android WebView produce a real selection. Keep selected passage reads that existing DOM range; it never searches for matching words to manufacture a location.

The native draw callback supplies the observed page origin, dimensions and zoom. PDF.js supplies its actual page view box, rotation, viewport transform, selected range rectangles and stable text-span endpoints. Every rectangle corner is inverse-mapped into original unrotated PDF points. The existing reader ViewModel and Room repository save the quotation, reflection and full location envelope under the prototype-only engine identity `disposable-pdfjs-androidpdfviewer`. No Room schema or notebook routing changes are made.

Saved passages reopen by their persisted page and span offsets, with exact quotation verification—not by string search. The stored payload is preserved exactly; independently observed restored rectangles must remain within **1 PDF point**, and native overlays must remain within **2 dp** at 1× and 2×. Restored DOM ranges are distinguished from touch-origin selections in the evidence. The prototype does not advertise selection or highlight-display capabilities as accepted.

Both parsers receive the same checksum-verified app-owned file. Verification precedes initial opening and selection/reopen operations. The WebView serves only an explicit local asset allowlist and that document through an intercepted virtual HTTPS origin; file/content access, external navigation and network loads are blocked. The app has no INTERNET permission. Embedded document JavaScript, forms and links are not activated; there is no OCR.

## Exact inputs and dependencies

- PDF.js **4.10.38**, with untouched `legacy/build/pdf.mjs` and `legacy/build/pdf.worker.mjs` inputs retained under [third_party/vendor](../third_party/vendor/pdfjs-4.10.38/), and unmodified `web/pdf_viewer.css`, from [the versioned npm package](https://registry.npmjs.org/pdfjs-dist/-/pdfjs-dist-4.10.38.tgz). The tarball SHA-256 is `1011b38553532d7078c59f26b15a471f8dae00f101b60e2add9b8511737a1ce0`. The shipped JavaScript modules are deterministic **esbuild 0.25.12** transforms targeting **Chrome 69**, not the original runtime bytes. [Input hashes](../third_party/licenses/hybrid/pdfjs-4.10.38.sha256), [source/output build hashes](../app/src/hybrid/assets/hybrid/pdfjs-build.json), [pinned package inputs](../package-lock.json), [build script](../tools/build_hybrid_pdfjs.mjs) and [license texts](../third_party/licenses/hybrid/) are retained; no runtime CDN is used.
- AndroidPdfViewer **3.1.0-beta.1**, `com.github.barteksc:android-pdf-viewer`, AAR SHA-256 `8c43d41d4f64f6bdd0077833d3b8bdddefa95bcfbd51923bfe0f033c1adbcaca`. The exact [tagged API](https://github.com/barteksc/AndroidPdfViewer/tree/3.1.0-beta.1) is used.
- Its pinned `com.github.barteksc:pdfium-android:1.9.0` AAR SHA-256 is `2992e86cce9ad5576e9c47a99cc2ba8cb75777016944ad30fbed3d7b8f6caf3f`. These retired-JCenter coordinates are fetched through a group-restricted mirror and checked with strict Gradle SHA-256 verification. Standard Jetifier compatibility migration is enabled because original support-library classes collide with the app's AndroidX dependencies. The native engine is not substituted or upgraded.
- The unchanged `rotated-cropped.pdf` and `repeated-two-page.pdf` are checked against the committed [fixture manifest](../app/src/androidTest/assets/fixtures/manifest.json). Generator font-box estimates are **not** reader-observed glyph geometry; acceptance compares actual DOM selection rectangles against independent public PDFium coordinate mapping and native drawn overlays. The repeated phrase must resolve to physical page index **1**, not its identical page-zero occurrence.

## One-attempt verification boundary

The dispatch-only [workflow](../.github/workflows/pdf-hybrid-prototype.yml) is limited to APIs **28** and **37** with minSdk **28** unchanged. There are no automatic push/PR triggers on this focused workflow and no retry step. Code pushes use `[skip ci]` so the foundation workflow does not consume a second run. The one focused run and its exact source SHA will be recorded below; a failed attempt must not be rerun under this approval.

Four independent focused instrumentation cases cover native rendering/offline/gestures, cropped/rotated geometry and overlays, actual repeated-page selection/save/reopen, and changed-byte retention/refusal. Separate one-case stage/verify invocations require a run-bound, fsynced ready marker, actual force-stop, confirmed PID disappearance, launcher relaunch and a fresh PID before checking stored writing and reopening the real passage. Failed or missing prerequisites cannot become a recovery pass.

Local unit tests establish coordinate plumbing and refusal behavior only. Source inspection, codec tests, synthetic safety seeds and successful APK assembly are not native selection, alignment or recovery evidence. No test changes the existing **≤1 PDF-point** or **≤2 dp** acceptance assertions.

## Evidence and stop

The **one approved attempt** is [run 39727581127](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39727581127), attempt **1**, dispatched once against source commit `33f38b13ddb194cc06747d32d74171493fc22ae2`. Both jobs completed with **FAILURE**. Each ran all four focused tests and recorded **4 failures**, followed by a failed one-case staging probe and a failed one-case verification probe. No retry is authorized or was performed.

Local checks on that source passed **154 JVM tests** with zero failures/errors/skips, **96 Python tool tests**, the Node geometry/restore checks, the unmodified PDF.js asset checksums, strict Gradle dependency verification, hybrid lint and both hybrid APK assemblies. The harness's mocked adb responses are not device evidence.

### What the actual run establishes

Both CI lanes passed fixture regeneration/byte verification, Python/Node checks and the strict hybrid build/lint/APK step. Both installed the app and instrumentation APKs. Recorded SDKs were **28** and **37**, with WebView versions **69.0.3497.100** and **145.0.7632.218**, respectively.

The ordinary document test timed out waiting to leave Opening; the repeated-page and rotated/cropped tests timed out waiting for both engines to be ready. No successful gesture-selection callback, independently measured PDF quad, native overlay comparison or persisted real capture was reached. These are observed integration readiness failures, not evidence that either standalone engine's geometry is incorrect. The precise readiness root cause is unresolved.

The changed-bytes test and recovery staging probe failed during test setup with `SQLiteConstraintException: UNIQUE constraint failed: sources.checksum`. The helper gave identical fixture bytes new source UUIDs and attempted another insert into the app's checksum-unique source table. This is a prototype-harness isolation error, **not** a PDF engine defect, a changed-file rejection verdict, or one of the pre-existing notebook failures.

The runner did confirm actual process death and a new launcher PID: **5766 → absent → 5836** on API 28 and **4407 → absent → 4462** on API 37. However, staging had never created the run-bound capture marker; verification failed on that missing marker. **Process mechanics passed; exact passage persistence/reopening after restart did not run.**

| Required evidence | API 28 | API 37 |
| --- | --- | --- |
| App/instrumentation installation and exact SDK | PASS | PASS |
| Combined PDF opening/readiness scenario | FAIL: timeout | FAIL: timeout |
| Real selected quotation and page-two capture/save | NOT RUN: readiness blocked | NOT RUN: readiness blocked |
| CropBox/rotation geometry ≤1 PDF point | NOT RUN | NOT RUN |
| Native overlays ≤2 dp at 1×/2× | NOT RUN | NOT RUN |
| Changed-file refusal with retained writing | NOT RUN: fixture setup failed | NOT RUN: fixture setup failed |
| Force-stop/PID disappearance/fresh launcher PID | PASS: mechanics only | PASS: mechanics only |
| Exact capture reopen after real process restart | NOT RUN: staging failed | NOT RUN: staging failed |

Raw [API 28 proof/metadata](evidence/hybrid-pdf-api-28/) and [API 37 proof/metadata](evidence/hybrid-pdf-api-37/) are retained with the run's [job outcomes](evidence/hybrid-pdf-run-39727581127.json). The full CI logs and original artifacts remain attached to the linked run. A diagnostic limitation matters: the workflow's post-action logcat collection ran after the emulator action had stopped its device and captured only `- waiting for device -`. It therefore cannot identify a JavaScript, font, native-loading or callback root cause. WebView 69 is a compatibility risk, but the evidence does **not** establish it as the cause, and API 37 also failed readiness. The app-files archives contained no staged capture marker or successful per-gate screenshots. Failure-state screenshots were retained separately; they are not selection proof.

The one-run approval is exhausted. The owner subsequently authorized local compatibility fixes, startup-handshake instrumentation/hardening and fixture isolation, and then authorized committing and pushing that work to `main` only after local checks pass and the remote is confirmed unchanged. **No further device/emulator/CI experiment is authorized.** There is no release-engine decision to make from this evidence.

Known same-book notebook passage-switch failures on API 28/37 and saved-capture reopening failure on API 28 are unchanged and are not attributed to this PDF spike. Tablet, spoken TalkBack, real ARM/16-KiB operation, native-source reproducibility, parser hardening, complete transitive legal clearance, performance and release acceptance are outside this focused run and remain unproven. The legacy native payload and installed WebView version are additional compatibility risks, not reasons to relax the gates.

## Local follow-up: not a device pass

The two first-party syntax defects in `selection.mjs`—optional chaining at the text-node endpoint and nullish coalescing in quotation restoration—were replaced with equivalent guarded access and explicit null/undefined checks. The local AST check now rejects those operators in both first-party selection modules.

The original vendor modules contain **297 optional chains and 28 nullish operators in `pdf.mjs`**, and **194 and 4 respectively in `pdf.worker.mjs`**. First-party cleanup alone could not make those static imports parse on WebView 69. The shipped modules are now transpiled from the checksum-verified originals with the pinned toolchain; the local compatibility check requires Chrome 69-compatible grammar and records zero optional-chain/nullish operators in both outputs. This addresses the static syntax incompatibility only: browser APIs, fonts, native loading and actual WebView operation remain unvalidated. PDF.js, minSdk, the engine and exactness thresholds are not upgraded or replaced.

The startup flow now waits for the JavaScript module to announce that its handlers are installed, and marks a frame delivered only after `evaluateJavascript` confirms actual receiver invocation. A monotonically increasing request ID accompanies the full observed native frame and is echoed in readiness/selection/restoration messages. Old delivery callbacks cannot consume a newer request, and old readiness cannot be accepted after page/zoom invalidation or a return to identical geometry. Navigation and programmatic zoom invalidate readiness immediately; incoming messages also check the live native page and zoom. If the text layer becomes ready before native loading completes, native `onLoad` rechecks that current metadata rather than silently losing combined readiness.

`HybridProof` diagnostics now identify source verification, WebView page/resource failures, native loading failures, installed module handlers, document fetch/parse, worker mode, native frame requests, receiver invocation and text-layer completion. A classic pre-module bootstrap captures module/global errors even when module evaluation fails. The runner now collects logcat and a readiness summary while the app process is alive, before returning control to the emulator owner. Fixture setup removes only matching test-owned synthetic rows and their dependent data before reusing a checksum; production checksum uniqueness remains intact. These are preparations for a future separately approved experiment, **not an explanation or demonstrated fix for API 37's first-open failure**. Historical failed-run evidence is unchanged.

The local verification boundary includes hybrid JVM tests, mocked Python tool tests, executable bootstrap/startup tests, Node geometry/restore assertions, runtime grammar/provenance/license checks, original vendor checksums, byte-for-byte regeneration, strict Gradle dependency verification, hybrid lint and both hybrid APK assemblies. Build completion is not a warning-free or release-readiness claim. The startup tests use the real first-party JavaScript module with substituted DOM/PDF-parser boundaries; JVM tests exercise the production frame-handshake state. They do not test a real WebView/native renderer, and native `onLoad`/JavaScript callback ordering is inspected rather than end-to-end runtime-proven. No emulator, device or CI run is part of this follow-up.

Local commands (from the worktree root):

```sh
npm ci
sha256sum --check third_party/licenses/hybrid/pdfjs-4.10.38.sha256
sha256sum app/src/hybrid/assets/hybrid/{pdf.mjs,pdf.worker.mjs,pdfjs-build.json} > /tmp/hybrid-pdfjs-before.sha256
npm run build:hybrid-pdfjs
sha256sum --check /tmp/hybrid-pdfjs-before.sha256
npm run test:hybrid-compatibility
node tools/test_hybrid_bootstrap.mjs
node --experimental-vm-modules tools/test_hybrid_startup.mjs
node tools/test_hybrid_selection.mjs
python3 -m unittest discover -s tools -p 'test_*.py' -v
ANDROID_HOME=/home/ubuntu/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
  ./gradlew --dependency-verification=strict :app:testHybridDebugUnitTest :app:lintHybridDebug \
  :app:assembleHybridDebug :app:assembleHybridDebugAndroidTest
```

Hybrid asset-merge, lint-model and lint-analysis tasks explicitly depend on PDF.js generation. The missing lint-analysis dependency was reproduced locally before correction; the complete strict lint/build invocation then passed, including app, unit-test and instrumentation lint analysis. The dispatch-only workflow now installs the pinned JavaScript tooling with `npm ci` before building; it was not dispatched. Timeout diagnostics tolerate an already-closed activity scenario so an old diagnostic callback cannot mask the original timeout. This test-helper guard is compiled locally, not device-validated.

The final local checks passed: freshly executed JVM suites for AndroidX (**156** tests), ComposePDF (**155**) and hybrid (**162**), with zero failures/errors/skips; **96** Python tool tests; **5** fixture-generator tests; **3** bootstrap and **9** startup tests; Node selection geometry assertions; compatibility/provenance/license checks; strict hybrid lint and both APK assemblies. Lint reported **0 errors and 53 warnings**, so this is not a warning-free build. All ten packaged fixtures and their manifest records regenerated byte-for-byte; the large local-only PDF remained excluded. Original vendor checksums and generated PDF.js modules/build metadata were byte-reproducible. Archive-level inspection confirmed that the app APK contains the exact generated modules, build manifest, first-party scripts, original license notices and hybrid license files, and that the instrumentation APK contains the exact ten fixture payloads. Independent diff review found no additional source blocker; APK provenance was verified after that review. These are local checks only, not new selection, alignment, notebook or recovery evidence.

The **≤1 PDF-point** and **≤2 dp** assertions, original fixture bytes, PDF/native engine versions, workflow triggers/API matrix and failed API 28/37 evidence remain unchanged. **PDF: NO SELECTION.**
