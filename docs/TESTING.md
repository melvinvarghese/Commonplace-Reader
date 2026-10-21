# Testing Commonplace

## Safe import proof

Both PDF-candidate flavors use the same importer and original synthetic documents. Imports use Android's `ACTION_OPEN_DOCUMENT` picker, copy to a random app-private staging file, hash while copying, validate the copy, then place the complete asset before registering its Room source. The original provider file is never opened for writing. Exact duplicates retain one source and offer **Open existing book**; until the reader proof, this opens source details rather than pretending to read the book.

The proof limits are 256 MiB per import, 10,000 ZIP members, 64 MiB per expanded member and 512 MiB total declared/actual ZIP expansion. Remaining free space is rechecked before every copied block, with a 1 MiB reserve; real write/sync failures still abort. ZIP paths, duplicate names, lengths and CRCs are checked without extraction. EPUB structural XML has an additional 2 MiB/document, 128-depth and 100,000-token cap. DTDs/entities are refused. Only local XHTML reading-order resources are accepted by this proof. These intentionally conservative checks are not a complete EPUB conformance certification.

PDF validation checks the header and opens the staged file with the platform `PdfRenderer`, requiring a nonempty page tree. This is not either candidate's reader integration and does not claim rendering, selection, search or highlighting. Password/restricted PDFs fail explicitly; no passwords are requested or DRM bypassed. Platform-native parsing has no application-configurable CPU/memory budget or isolated-process sandbox in this proof; the byte limit is not a proof of safety against every hostile PDF. EPUB remote references are never fetched during import; future reader network isolation is a separate gate.

Cancellation propagates, staging is cleaned, and database failures remove only the new unreferenced copy. A noncancellable placement/registration section prevents cancellation from unlinking an asset after its row commits. Cancellation before that boundary leaves no new source; cancellation during/after it can propagate without a success dialog while the completed book remains in the observed library/relaunch. A cancelled or destroyed UI is not promised a dialog, and durable imports are never rolled back just to imitate one. A dispatcher-controlled test observes the database insert at either edge of a repository dispatch, cancels the owning job and verifies the referenced bytes remain complete. This does not assume Room always schedules a separate continuation after INSERT; synchronous completion is valid too. Import and startup reconciliation share an in-process mutex. Startup removes abandoned files only from importer-owned staging/assets, retains referenced assets, and marks missing source files unavailable without deleting writing. This does not promise multi-process import support or power-loss durability beyond filesystem/SQLite guarantees.

## Run the checks

Use the JDK/SDK setup in [DEVELOPMENT](DEVELOPMENT.md).

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:testAndroidxDebugUnitTest :app:testComposepdfDebugUnitTest \
  :app:lintAndroidxDebug :app:lintComposepdfDebug \
  :app:assembleAndroidxDebug :app:assembleComposepdfDebug \
  :app:assembleAndroidxDebugAndroidTest :app:assembleComposepdfDebugAndroidTest
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

The shared `DocumentImporterTest` runs against real file-backed Room/files on Robolectric API 28 and Android instrumentation. It covers spoofed formats, unknown-length/unseekable streams, provider failure/revocation, space exhaustion, cancellation, failed registration, exact duplicate races, interrupted staging, missing files and hostile archive limits. Host results are not Android-device evidence. `ProviderImportTest` is device-only: real `ContentResolver` pipes with unknown lengths/spoofed metadata, a failing provider, simulated provider revocation after copying, offline parsing of owned bytes, and the shared PDF/EPUB corpus. The platform-only Java synthetic provider (`TestDocumentProvider.java`) is independently started by Android without requiring the target app’s shared Kotlin runtime. It is declared only in the test APK and exposes only allowlisted original fixtures, not private documents. It simulates revoked access; it does not claim a cloud provider integration or a system settings permission-revocation UI test.

On a connected disposable emulator:

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedAndroidxDebugAndroidTest :app:connectedComposepdfDebugAndroidTest
python3 tools/verify_android_test_results.py \
  app/build/outputs/androidTest-results/connected/debug/flavors
```

CI additionally runs `python3 tools/run_import_recovery.py` on each disposable emulator after the normal suite. A guarded instrumentation probe writes a nonempty staging file and blocks; the runner requires its private readiness marker and live PID before force-stopping the real target process, confirms the PID disappears, then runs a fresh-process verification. The new partial source must be absent, staging/orphans removed, and a preexisting source annotation retained. Both flavors must report exactly one passing recovery verification; logs are under `build/reports/import-recovery`. The guarded stage methods return without staging during the normal suite; those two normal-suite results are not the recovery evidence. The probe refuses preexisting unrelated staging/orphan files and is not a tool to run on personal data.

The result guard rejects missing named shell, repository, importer and real-provider cases, and any reported failure/error/skip. CI retains actual XML and failure diagnostics. API 28/API 37.0 remain the approved proof matrix, not a public supported-device promise. Physical devices, SAF picker automation, accessibility/TalkBack and actual cloud/document-provider grant UI flows are not established by host tests.

## Task 4 EPUB reader proof — pending final gate

Task 4 is blocked, not complete. The full strict local gate passes 152 JVM cases, 40 Python guards, both-flavor lint and app/test-APK builds. Latest [run 39595102330](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39595102330) exposes a real API 28 Compose slot-table teardown crash; only 52 AndroidX reports are emitted, with one failure, and later stages are not reached. Both API 37 ordinary suites pass all 53 reports, but both real reader-recovery stages time out at the acknowledged-draft condition before force-stop. Earlier native passes are recorded historically, not substituted for the failed final gate. See READER_PROOF for the independent stable-container hypothesis, its uncertainty and the required targeted regression before another architectural change.

The intended device proof uses the pinned Readium 3.4.0 `EpubNavigatorFragment` (the supported older navigator API), Room-backed records, and the synthetic EPUB fixture only. Its scope is reflowable, UTF-8, non-encrypted EPUB; it does not establish general EPUB conformance, fixed-layout or DRM support, PDF reading (Task 5), or a full editor. The test targets include native full-locator persistence without href/CFI alteration or quote reconstruction/pixel offsets; native search and Decoration API display; durable captures, bookmarks, progress and explicitly saved source annotations; checksum-mismatch unresolved locations with writing retained; reader preference observation; offline/hostile-resource handling and explicit external-link confirmation. A draft is only acknowledged after persistence; a capture is “Saved” only after the Room transaction; source annotations are written only after explicit save, not draft autosave. These are intended assertions, not results until the device suite passes.

`python3 tools/run_reader_recovery.py` is intended only for a disposable emulator. It stages acknowledged capture/bookmark/progress state, confirms a real live PID, force-stops the target, verifies PID disappearance, relaunches with `monkey`, and checks restoration in a fresh process. Run and record both flavors on each matrix image (API 28 and API 37); none of those Task 4 process-recovery results is currently established. Keep per-flavor/per-API executed, failed, errored, and skipped counts, recovery log paths, CI URL/commit, and final build/lint/unit outcomes in [READER_PROOF](READER_PROOF.md).

The automated 200% text-size, dark-reading, landscape and 48 dp control checks have passing native case reports, but do not close the failed overall lifecycle/recovery gate. TalkBack auditory checks and tablet testing have not been run unless explicitly recorded in the proof ledger. The app removes publication-origin scripts and applies parsed-XHTML sanitization and a CSP before the trusted Readium HTML injector; do not describe this as WebView JavaScript being disabled. There is no app-added toolkit bridge; do not claim the Readium toolkit has no bridge. Runtime offline behavior remains pending its final device test.

## Original shared fixtures

[Fixture generation and licenses](../tools/fixtures/README.md) describe the pinned generator, bundled font notice and corpus. [The manifest](../app/src/androidTest/assets/fixtures/manifest.json) records exact bytes, hashes, authored strings, zero-based physical pages, absent genuine page labels, and reference geometry. The expected quads are conservative generator-authored font-box estimates, not engine-observed selection evidence. Multilingual coverage is Latin accents/Greek/Cyrillic, not a broad language/shaping guarantee.

```sh
python3 -m pip install -r tools/fixtures/requirements.txt
python3 -m unittest tools/fixtures/test_generate_fixtures.py -v
python3 tools/fixtures/generate_fixtures.py --output build/local-fixtures --large
```

The optional local large PDF is exactly 104,857,600 bytes, one page, SHA-256 `73c4e34e97311ce559c8755ee9c0e42f27f7fd6bc733db06a61a56fae0f69c6e`. It uses whitespace padding after EOF; engine parser tolerance and resource behavior must be measured later. Never commit it or generate it in regular Android assets: a Git ignore does not exclude a file from an APK. CI regenerates it in `build/fixture-regeneration`, validates exact size/hash/pages and compares the committed small corpus/manifest against the pinned generator. It is not bundled in the standard test APK.

## Evidence status

Task 3 is verified at `5260993` by [run 39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012): checks, API 28 and API 37.0 all pass. Downloaded XML and recovery logs were inspected, and the named-case guard was rerun against both downloaded API report trees.

| Evidence | Verified result |
| --- | --- |
| Both-flavor host tests | 50 per flavor, 100 total; zero failures/errors/skips |
| Normal device suite | 45 reported cases per flavor/API, 180 total; zero failures/errors/skips |
| Functional device cases | 172 total: shell, 13 repository, 25 importer and four provider cases per flavor/API |
| Guarded probe methods in normal suite | Eight intentional no-op reports; not counted as process-kill evidence |
| Separate real mid-copy kill/reopen | Four: one per flavor/API, each with a nonempty staging readiness marker, live PID, force-stop, confirmed PID disappearance and exactly one fresh-process passing recovery assertion |
| Python CI/runner regressions | 27 passed |
| Fixture-generator tests | Five passed; small corpus/manifest regenerated byte-identically; exact 100 MiB fixture generated outside APK assets |
| Lint and APK compilation | Zero lint errors, nine retained warnings per flavor; both app/test APKs built |

The final native app/test code is unchanged from `5d47ada`; `5260993` only fixes the API 28 readiness command and adds its Python regressions/documentation. A missing standalone `test` executable had prevented the runner from confirming an already-staged import. The fixed runner reads the exact `ready` contents with `/system/bin/cat`; it does not turn a timeout into a pass or relax the kill/reopen requirements. Earlier provider-runtime and cancellation-injector failures are retained in [DEVELOPMENT](DEVELOPMENT.md), not hidden by test skips.

Task 2's passing device evidence remains at [run 39549310739](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39549310739). No reader-engine behavior, physical-device support promise, SAF picker UI automation, real cloud-provider grant UI or universal hostile-document safety is proved by import success.

### Historical Task 4 local checkpoints

The current Readium host passed both-flavor unit tests (**66 each, 132 total; zero failures/errors/skips**), lint, app assembly and instrumentation-APK assembly with `--dependency-verification=strict`; CI orchestration/result/recovery guards passed **36 Python regressions**. These are not on-device renderer results. A security-review finding concerning standalone SVG documents is fixed by rejecting SVG resources before navigator attachment and again at resource lookup; its MIME/decoded-href policy regression was observed red then green, and a narrow follow-up review accepted the fix. The hostile-publication device case includes a real authored-script SVG spine and must assert explicit unsupported/no navigator/zero controlled-endpoint requests. Standalone SVG resources are outside this bounded proof. Final native/API/recovery evidence remains pending in [READER_PROOF](READER_PROOF.md). The recovery probe also checks an acknowledged pending native draft across actual process death, not only committed captures. Launcher relaunch precedes explicit source reopening; automatic last-book launch is not claimed.

The startup-diagnostic follow-up adds two local bootstrap cases using the exact shared instrumentation fixture and a message-free failure-code privacy test. Both-flavor local JVM totals are now **69 each (138 total)** with zero failures/errors/skips; strict both-flavor lint/app/test-APK assembly passes. The original 36 Python guards are unchanged. These host checks do not turn the failed native run into a pass. The complete matrix and required named native/recovery assertions remain unchanged.

The native navigator wait must advance the Compose test-owned clock (`waitForIdle()`), not only sleep on the instrumentation thread. A local exact-fixture bootstrap under the same rule reproduced zero fragments before this fix and passed after it. The direct-container control remained passing. Both-flavor complete strict gates pass at 69 JVM cases each after the correction; the test helper retains real-fragment/view checks and all required native cases. The standalone recovery probe also declares a Compose rule; the later mandatory-clock correction below supersedes the earlier assumption that it used only Android frames. Native CI outcome is still reported separately in READER_PROOF.

After the CSP regression: 70 JVM tests per flavor (140 total), 36 Python guards and the complete strict both-flavor lint/app/test-APK gate pass locally. Native readiness requires chapter content and initialized toolkit JS; source-switch waits advance Compose. The proof checks painted highlight geometry, saved bookmark reopening, both importer/host SVG rejection paths and unknown-host script denial. These are strengthened assertions, not runtime passes; consult the exact CI evidence in READER_PROOF.

The launcher PID regression adds four deterministic guards (40 Python checks total): delayed startup is observed, absence is bounded, ADB errors fail immediately and a stale PID is rejected. A confirmed live fresh PID remains mandatory before restoration verification. Compose wait predicates permit a temporary missing semantics root, never a missing final match or longer timeout. Both-flavor JVM totals remain 70 each (140 total), and the complete strict local gate passes.

Resource-retirement regression: six new cases per flavor reproduce and guard closed ZIP/InputStream access, late reads, cached transforms, range/metadata, idempotent close and cancellation. JVM total is now 76 per flavor (152 total); 40 Python guards and the complete strict lint/app/test-APK gate pass locally. Actual native recreation and real process-death proof still require the full CI matrix.

Native harness synchronization preserves exact-locator assertions: wait for the requested chapter’s computed native position as well as trusted-script/DOM readiness. The 200% landscape check waits for real configuration recreation and restored configuration rather than forcing a second recreation during the first; its font/orientation and 48 dp assertions remain mandatory. Strict local gate remains 152 JVM/40 Python plus both-flavor lint and APKs; complete native/recovery matrix remains required.

Run 39593165874 passes all 24 actual native EPUB cases (six per API/flavor) but not separate recovery. Recovery stage and verify now pass their ComposeTestRule to a mandatory clock-driving navigator helper, replacing the unsafe optional no-op callback. This applies the already reproduced bootstrap clock fix to both real-death phases without dropping assertions or extending deadlines. Complete strict local checks remain green at 152 JVM/40 Python and both-flavor lint/APKs. Full recovery evidence remains pending.
