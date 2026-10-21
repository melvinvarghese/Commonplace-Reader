# EPUB reader proof ledger — Task 4

**Status: local build passed; native gate failed and is under diagnosis.** This ledger is not a completed runtime proof report. The reviewed Task 4 host compiles, but no reader capability or device result below is marked passed. Update this file with measured outcomes and report references after the final run. Do not change Task 4 plan checkboxes here.

## Known evidence checkpoint

- The pre-Task4 Task 3 CI run [39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012) passed API 28/API 37 for import, storage and recovery only; it does not prove reading.
- Test-first Task4 CI run [39560371312](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39560371312) recorded native expected-result failures on **both API 28 and API 37**, plus an ancillary empty-shell isolation failure. This is not a final implementation result and is not a pass.
- The reviewed host passed strict-dependency-verification unit/lint/app/test-APK assembly for both flavors. JVM XML records **66 tests per flavor (132 total), zero failures/errors/skips**, including 10 ReaderViewModel cases, locator/selection contracts, DataStore restart persistence and parsed-content/SVG policy. Python CI guards: **36 passed**. This is host/build evidence, not native rendering evidence.
- Independent review identified standalone SVG active content bypassing XHTML sanitization. The boundary now rejects declared SVG or decoded `.svg` resources before attachment and at resource lookup. The unit regression was observed red first; narrow follow-up review accepted the fix. Scripted SVG non-execution remains part of the pending controlled-endpoint device proof.

## Proof boundary

The target is Readium Kotlin 3.4.0 using its supported `EpubNavigatorFragment` API (not the next navigator). The proof is limited to the original synthetic corpus: **reflowable, UTF-8, non-encrypted EPUBs without standalone SVG resources**. It is not general EPUB conformance. Fixed-layout, standalone SVG resources and DRM/encrypted EPUBs are unsupported in this proof. PDF is Task 5 and is not covered here.

The planned assertions cover one real navigator/session; exact native locator preservation; native selection, search and Decoration API highlights; durable Room captures/bookmarks/progress and return-to-capture; explicitly saved source annotations; dark-reading preference observation; source-switch cleanup; and retaining writing while marking locations unresolved after a missing/checksum-mismatched source. `href`/CFI and other locator fields must remain native and untouched: no quote reconstruction or pixel-offset location approximation. Capture drafts are acknowledged only after durable persistence; a capture is reported saved only after its Room transaction; source annotations require explicit save and are not saved through draft autosave.

The security test target is app-private local reading with no `INTERNET` permission, an offline deny-all HTTP client, parsed-XHTML sanitization before the trusted Readium HTML injector, and CSP. Publication-origin scripts are removed; trusted Readium scripts remain. External HTTP(S) links require explicit confirmation. The app installs no additional native JavaScript bridge. Do **not** claim WebView JavaScript is disabled or that Readium itself has no bridge. These runtime properties remain unverified until the final device tests pass.

The UI is a proof/debug surface with a simple saved-items panel, not a full editor. Planned accessibility checks include 200% system text, dark reading, landscape and 48 dp controls. TalkBack auditory and tablet checks are unrun unless the final evidence explicitly says otherwise.

## Final evidence to record

| Gate | API 28 / AndroidX flavor | API 28 / ComposePdf flavor | API 37 / AndroidX flavor | API 37 / ComposePdf flavor |
| --- | --- | --- | --- | --- |
| Current commit, CI run URL, complete host/unit/lint/assemble result | **Pending** | **Pending** | **Pending** | **Pending** |
| `ReadiumProofTest`: executed/passed/failed/errored/skipped counts and actual assertions | **Pending** | **Pending** | **Pending** | **Pending** |
| Offline hostile publication: local render, sanitized content, zero controlled-endpoint requests, confirmed external-link prompt | **Pending** | **Pending** | **Pending** | **Pending** |
| Accessibility test: 200% text, dark, landscape, measured 48 dp controls | **Pending** | **Pending** | **Pending** | **Pending** |
| `tools/run_reader_recovery.py`: confirmed save/flush, live PID, force-stop, PID disappearance, `monkey` fresh process, restored capture/bookmark/progress/locator and acknowledged pending draft; log path | **Pending** | **Pending** | **Pending** | **Pending** |

Record device/emulator identity, API and SDK extension level, ABI, flavor, build commit, fixture identity/hash, exact executed/failed/errored/skipped counts, and CI XML/log paths. Do not count intentionally gated/no-op probe methods as process-death evidence. Report any failures, skips or unrun cases explicitly; do not infer success from workflow creation or an earlier task's run.

TalkBack auditory checks: **Not run / pending owner verification**. Tablet checks: **Not run / pending owner verification**. Physical-device testing: **Not established**.  
Final Task4 gate and proof status: **Pending**.

The process-death protocol relaunches the app through `monkey`, then explicitly reopens the persisted source in the verification instrumentation. It does not claim automatic last-book launch. Ordinary-suite early-return probe methods are not counted as process-death proof; the separate runner requires exactly one real passing stage and one real passing verify case per flavor.

## First implemented native run

[Run 39563450127](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39563450127) at `5b4e13217b5bd6e97616debf6940540ec9f1a25d` passed checks but failed both emulator jobs. Each API 28/API 37 AndroidX report records **53 executed, 5 failures, 0 errors, 0 skips**. All five readable-navigator cases timed out before observing a native fragment; the explicit malformed/DRM/fixed-layout rejection case passed. ComposePdf instrumentation did not execute because the first connected variant failed; both process-kill protocols were not reached. These outcomes are not reading, offline, accessibility or process-death passes. Artifact reports are under `shell-smoke-api-{28,37.0}/app/build/outputs/androidTest-results/connected/debug/flavors/androidx/`.

A targeted Robolectric direct-container host bootstrap passes, which is host-only evidence and does not resolve the real Compose/device failure. Diagnosis continues without changing permissions, dependencies, required assertions, SDK baseline or approved scope.

### Startup diagnostics checkpoint

The exact legal two-chapter fixture is now shared between instrumentation and two local bootstrap cases (direct Fragment container and the actual Compose ReaderActivity path). Both local cases pass; this is not native WebView rendering evidence. Startup failures now retain the toolkit typed cause and log **stage plus exception/code-symbol call sites only**—never exception messages, filenames, document URLs or writing. A privacy test was observed red before implementing this formatter, then passed. The native helper now reports a real Error/Unsupported state immediately while preserving the real-fragment assertion and all required cases. The same full API 28/API 37 matrix is rerun; no gate, SDK, dependency pin, permission or offline restriction was relaxed.

### Controlled test-clock stall reproduced and corrected

[Diagnostic run 39566765167](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39566765167) again recorded 53 AndroidX cases per API, five native-wait failures and no startup-failure codes. The wait never drove Compose’s test-owned clock. Adding the same Compose test rule to the local exact-fixture bootstrap reproduced **expected 1 native fragment, actual 0**, while the direct-container case passed. Advancing composition with `waitForIdle()` inside the native wait fixed that local regression. The ordinary recovery probe still uses Android’s real frame clock; no production workaround, timeout extension or assertion removal was added. Local Compose-test dependencies use the same already-pinned runner/Espresso versions as instrumentation; dependency verification metadata is unchanged.

The corrected full local gate completed successfully in 78 seconds: **69 JVM tests per flavor (138 total), zero failures/errors/skips; both-flavor lint, app and instrumentation-APK assembly passed with strict dependency verification**. The two controlled-clock bootstrap cases pass in each flavor. A delayed background completion signal was separately terminated; the completed Gradle log and timestamped XML were inspected, with no Gradle/test processes remaining. This is local evidence only. Native rendering and process-death proof still require the next full matrix to pass.

### Valid CSP and remaining native proof checkpoint

[Clock-corrected run 39584190197](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39584190197) at `cea5fe7e78d4deda8725522102280eefc4cc125b` records **53 executed, 3 failures, 0 errors, 0 skips** per API in AndroidX. The real-navigator recreation case, explicit rejection case and 200% text/dark-landscape semantics case pass on both APIs. Source-switch waiting still used a non-clock-aware helper; the script/readiness assertions and SVG import expectation also required correction. ComposePdf and real process-kill stages were not reached. This is not a completed reader proof.

Chromium console evidence shows CSP rejects the SDK’s underscore virtual host source expressions; its trusted script was blocked (`readium is not defined`). A new policy regression was observed red, then passed with a valid wildcard-host **exact reflowable-script path**. Package resources use `'self'`; SDK styles/fonts are limited to `/readium/`. This is not an origin-only CSP allowlist: independent review accepted the combined defense because author scripts/handlers are removed, the pinned SDK intercepts all resource hosts, the app container rejects absolute-URL resources, OfflineHttpClient denies HTTP and the app still has no INTERNET permission. No inline-script/eval allowance, duplicate SDK injection, private SDK API or new native bridge was added. The native proof now explicitly attempts an unknown-host script at the otherwise allowed SDK path and requires safe failure plus zero endpoint requests.

Native waits now drive Compose during source switching and require actual chapter text plus initialized toolkit JS, with JSON string decoding for platform-independent Unicode assertions. The SVG spine is explicitly rejected by the existing importer without a new source; a separately imported XHTML-spine book containing a scripted SVG resource verifies host-side rejection. The final capture proof requires a visibly painted, in-viewport highlight—not merely a DOM node—and opens a saved bookmark without altering its locator JSON. All required cases and both recovery protocols remain.

Fresh complete local verification: **70 JVM cases per flavor (140 total), zero failures/errors/skips; 36 Python guards; both-flavor strict lint, app and test-APK assembly pass**. Native outcome for this correction is still pending.

### Native functional success and bounded runner-race correction

[Run 39586345947](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39586345947) at `b80b29058debfbf602b4b499b91d13aabb3cec2b` passes all **53 ordinary-suite cases (zero failures/errors/skips)** for both API 28 flavors and API 37 AndroidX. API 37 ComposePdf records 53 cases, one source-switch failure: a transient missing Compose hierarchy threw inside the wait condition. All six real EPUB cases pass in the other three combinations, including native selection/search, exact captures and painted decorations, saved bookmarks, source integrity, zero controlled-endpoint requests, unknown-host denial and accessibility semantics. No app fatal exception was found in retained logs.

API 28 reached both real reader restart stages. Both staged tests passed and wrote the confirmed-save/flush marker. ComposePdf then confirmed live PID → force-stop → PID absence → monkey → different PID and passed `verifyRestoredReader` (one real case). AndroidX checked `pidof` immediately after monkey submitted its launch and found no process yet; verification did not run. These are not two recovery passes. API 37 recovery was not reached.

A deterministic delayed-start runner regression was observed red against the immediate PID check, then passed with bounded PID polling. Missing PID is never accepted, stale PID/ADB errors fail, and verification still requires a fresh live process after confirmed death. Compose conditional polling now allows temporary absence of a semantics root but retains the original deadline and required final nonempty match. Fresh strict local gate passes: **140 JVM cases, 40 Python regressions, both-flavor lint/app/test-APK assembly**. The complete matrix, not a selected rerun, remains required.
