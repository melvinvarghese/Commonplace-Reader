# EPUB reader proof ledger — Task 4

**Status: local build passed; final device gate pending.** This ledger is not a completed runtime proof report. The reviewed Task 4 host compiles, but no reader capability or device result below is marked passed. Update this file with measured outcomes and report references after the final run. Do not change Task 4 plan checkboxes here.

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
