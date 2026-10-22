# Disposable hybrid PDF proof

**PDF decision: NO SELECTION.** This owner-approved spike is disposable. Even an entirely passing focused run would require a separate owner decision and the remaining release gates before choosing an engine. Neither rejected comparison flavor is replaced.

**The single approved CI attempt failed on both API 28 and API 37.** The reader never reached its combined native-renderer/text-layer ready state, so the run produced no accepted real-selection, alignment or saved-passage evidence. A separate fixture-setup error also blocked changed-file and recovery checks. No retry, threshold relaxation or post-run prototype fix was made.

## What the prototype tries

The `hybrid` flavor (`im.commonplace.reader.hybrid`) places a transparent, selectable PDF.js text layer over AndroidPdfViewer's actual native rendering and gestures. Browse mode forwards touch to the native view; Select text mode lets Android WebView produce a real selection. Keep selected passage reads that existing DOM range; it never searches for matching words to manufacture a location.

The native draw callback supplies the observed page origin, dimensions and zoom. PDF.js supplies its actual page view box, rotation, viewport transform, selected range rectangles and stable text-span endpoints. Every rectangle corner is inverse-mapped into original unrotated PDF points. The existing reader ViewModel and Room repository save the quotation, reflection and full location envelope under the prototype-only engine identity `disposable-pdfjs-androidpdfviewer`. No Room schema or notebook routing changes are made.

Saved passages reopen by their persisted page and span offsets, with exact quotation verification—not by string search. The stored payload is preserved exactly; independently observed restored rectangles must remain within **1 PDF point**, and native overlays must remain within **2 dp** at 1× and 2×. Restored DOM ranges are distinguished from touch-origin selections in the evidence. The prototype does not advertise selection or highlight-display capabilities as accepted.

Both parsers receive the same checksum-verified app-owned file. Verification precedes initial opening and selection/reopen operations. The WebView serves only an explicit local asset allowlist and that document through an intercepted virtual HTTPS origin; file/content access, external navigation and network loads are blocked. The app has no INTERNET permission. Embedded document JavaScript, forms and links are not activated; there is no OCR.

## Exact inputs and dependencies

- PDF.js **4.10.38**, the unmodified `legacy/build/pdf.mjs`, `legacy/build/pdf.worker.mjs` and `web/pdf_viewer.css` from [the versioned npm package](https://registry.npmjs.org/pdfjs-dist/-/pdfjs-dist-4.10.38.tgz). The tarball SHA-256 is `1011b38553532d7078c59f26b15a471f8dae00f101b60e2add9b8511737a1ce0`. [Asset hashes](../third_party/licenses/hybrid/pdfjs-4.10.38.sha256) and [license texts](../third_party/licenses/hybrid/) are retained; no runtime CDN is used.
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

This approval is exhausted. Investigating/fixing readiness or harness isolation and running another device experiment would need a new authorization; neither is silently folded into this stop. There is no release-engine decision to make from this evidence.

Known same-book notebook passage-switch failures on API 28/37 and saved-capture reopening failure on API 28 are unchanged and are not attributed to this PDF spike. Tablet, spoken TalkBack, real ARM/16-KiB operation, native-source reproducibility, parser hardening, complete transitive legal clearance, performance and release acceptance are outside this focused run and remain unproven. The legacy native payload and installed WebView version are additional compatibility risks, not reasons to relax the gates.
