# Notebook checkpoint: writing verified, passage switching blocked

The notebook can browse, filter, create and edit writing, and recover acknowledged drafts after the app process is killed. Those checks passed on both tested Android versions. **The notebook milestone is not accepted:** switching to another saved passage in the same open book fails its native test. An existing saved-capture reopening test also fails on API 28. Neither failure is reclassified as a PDF problem or hidden by a green host build.

## What is implemented

The Commonplace tab has separate drafts and finished entries, type filters, full-entry reading, and a writing editor. Thoughts need no book. Annotations and source notes explicitly select a book without inventing a passage. Highlights preserve their quotation, source attribution and complete original locator while allowing title, reflection and color edits.

Room schema 2 migrates existing records without destructive fallback. Draft sessions, revisions and stable commit tokens guard late writers, conflicts, rollback and retries. Saving a finished entry atomically removes only its matching draft; discarding a draft does not delete saved writing. A saved-status message follows database acknowledgment, not a timer. Unacknowledged last keystrokes remain outside the recovery promise.

Entry-to-reader requests use stable internal IDs and verify the stored source bytes. Unresolved locations retain readable writing. The adapter checks the pinned Readium engine's own decoration range for the exact quotation start and uses an observed native pagination locator, not requested coordinates or a first-visible paragraph anchor. The failing same-source test means this path is **not yet established as dependable**, despite the additional guards.

## Exact code and checks

Product/test source: [`600ff3037604f747bd184312e51fb158ce8f61f9`](https://github.com/melvinvarghese/Commonplace-Reader/commit/600ff3037604f747bd184312e51fb158ce8f61f9). Subsequent status changes are documentation only. [CI run 39713208135](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39713208135) has a successful host/build job and **failed** API 28 and API 37.0 smoke jobs. The full/PDF guard is unchanged; independent notebook checks still ran after earlier failures.

Local verification passed 155 Androidx and 154 Composepdf host cases, with no failures, errors or skips; 85 Python runner/guard regressions; five fixture tests; both-flavor strict lint; and both app/test APK builds. Lint retains 16 warnings per flavor and zero errors. The pinned generator also reproduced all 11 committed corpus files. Host results do not establish native navigation.

| Native x86_64 Google APIs cell | Focused notebook cases | Actual notebook kill/relaunch |
| --- | --- | --- |
| API 28, Androidx | 39/40 pass; 1 failure; no errors/skips | PASS: four recovery cases, one verified restart |
| API 28, Composepdf | 39/40 pass; 1 failure; no errors/skips | PASS: four recovery cases, one verified restart |
| API 37.0, Androidx | 39/40 pass; 1 failure; no errors/skips | PASS: four recovery cases, one verified restart |
| API 37.0, Composepdf | 39/40 pass; 1 failure; no errors/skips | PASS: four recovery cases, one verified restart |

Each notebook restart proves an unfinished Thought, a saved-highlight edit draft, independent target drafts and a committed exact Unicode entry with no lingering draft. The driver requires acknowledgment, a live old PID, force-stop, PID disappearance, a new launcher PID, and real repository/UI verification. This is **four process restarts**, not sixteen; ordinary-suite probe no-ops are not additional recovery evidence. The retained import and reader recovery lanes also pass in all four cells.

Focused XML identity files bind the source SHA, platform, flavor, unique run and XML hashes. All four notebook identities and recovery proofs were checked against the exact source above. The failing notebook result guard remains failed.

## Every final native failure

These are the actual JUnit method names. A case appearing in both full and focused XML is not counted as a different defect.

| Test | Failing cells and observation |
| --- | --- |
| `NotebookPassageTest.sameSourceNewEntryRequestIsNotIgnored` | Both flavors on both APIs, full and notebook-focused runs. Times out at the second-passage visibility/consumption assertion, before the later recreation assertion. Blocks notebook acceptance. |
| `ReadiumProofTest.exactNativeSelectionCaptureBookmarkReopenDecorationAndSearch` | Both flavors on API 28, full runs. Times out reopening the saved capture into `one.xhtml` after recreation. API 37 passes. Current-source EPUB regression acceptance is incomplete. |
| `PdfProofTest.renderingNavigationZoomAndRecreationOrExplicitUnsupported` | Androidx, API 37, full run. Required PDF proof still fails. |
| `PdfProofTest.croppedRotatedGeometryIsNotInventedOrExplicitUnsupported` | Composepdf, both APIs, full and PDF-focused runs. |
| `PdfProofTest.largeDocumentDoesNotDuplicateFullFileBuffers` | Composepdf, both APIs, full and PDF-focused runs. |
| `PdfProofTest.repeatedOpenCloseAndCancellationReleaseResources` | Composepdf, API 28, full and PDF-focused runs. |

The same-source failure is reproducible in all four cells; its root cause is **not confirmed**. The proposed diagnosis that pagination callbacks only cover resource changes was rejected after inspecting the pinned engine: its location-update path also invokes that callback for viewport pagination. Its `go(Locator)` Boolean is dispatch acceptance, not proof of arrival. No speculative signal replacement or relaxed assertion was committed. Next: instrument the synthetic failing request's acceptance, exact range visibility and native locator transitions, fix the demonstrated cause, and repeat the current-source matrix.

Independent review previously identified an early-consumption race and a saved-version navigation issue. Regression-driven changes address the saved-version action and add command-acceptance, exact-range, stale-position and immediate/post-arrival recreation coverage. Review and host tests do not override the runtime failure. This is not a final independent-review sign-off.

## Debug trial APK

Prepared artifact: `/workspace/Commonplace-Reader/deliverables/commonplace-notebook-androidx-debug.apk`.

- Source: `600ff3037604f747bd184312e51fb158ce8f61f9`.
- SHA-256: `e68ef72782c216392afcc3081809e3f172d73e29a2bd888bfa691dab188a5009`.
- Size: 68,246,953 bytes. Signed, debuggable package `im.commonplace.reader.androidx`, version `0.1.0-androidx`; minimum SDK 28 and target SDK 37 verified from the APK.

This is a **notebook/EPUB development trial**, not a release or a promise of reliable passage switching. API 28 saved-capture reopening is also unresolved. Its Androidx PDF comparison remains experimental and unaccepted. Use disposable/noncritical writing until the blockers are fixed. Install as an update only when the signing identity matches the existing installation; if Android reports a signature conflict, stop rather than uninstall, because uninstalling removes app-private writing. The minimum SDK is a build floor, not a hardware-support claim. No installation on the owner's device was performed.

The APK has no `INTERNET` permission. Private database/draft stores remain excluded from cloud backup/device transfer; originals are unchanged. New notebook paths do not log private writing or locations. Source-integrity failures retain writing rather than applying coordinates to changed bytes.

## Boundaries unchanged

**PDF: NO SELECTION.** No third engine, raised Android baseline, fabricated selection/geometry, or weakened durable-highlight/recovery requirement is included. PDF source-note retention is not native PDF highlight recovery.

Spoken TalkBack, a manual on-screen-keyboard audit, tablets, physical devices, ARM and actual 16-KiB runtime remain NOT RUN. Automated large-text/light/dark/conflict layouts are not a full accessibility audit. Reflowable EPUB compatibility stays bounded to original synthetic, UTF-8, non-encrypted fixtures. Collection search/indexing, tags/collections, finished-entry deletion, Markdown export, backup/restore, removal/reattachment UI and sync remain outside this increment.

See the [approved specification](superpowers/specs/2026-10-22-commonplace-notebook-design.md), [implementation plan](superpowers/plans/2026-10-22-commonplace-notebook.md), [data model](DATA_MODEL.md) and [test commands](TESTING.md). CI artifacts `foundation-checks`, `shell-smoke-api-28` and `shell-smoke-api-37.0` retain source-bound XML, logs and actual recovery evidence. Notebook XML/identity lives under `build/reports/emulator/test-results/notebook-focused/<flavor>/`; real restart JSON/logs under `build/reports/notebook-recovery/<flavor>/`. The failed run must not be described as a passing notebook or whole-app milestone.
