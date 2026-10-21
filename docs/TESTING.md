# Testing Commonplace

## Safe import proof

Both PDF-candidate flavors use the same importer and original synthetic documents. Imports use Android's `ACTION_OPEN_DOCUMENT` picker, copy to a random app-private staging file, hash while copying, validate the copy, then place the complete asset before registering its Room source. The original provider file is never opened for writing. Exact duplicates retain one source and offer **Open existing book**: EPUB opens in the native reader; PDF enters the experimental comparison only in the `androidx` flavor. The other flavor retains explicit source details until Task 6.

The proof limits are 256 MiB per import, 10,000 ZIP members, 64 MiB per expanded member and 512 MiB total declared/actual ZIP expansion. Remaining free space is rechecked before every copied block, with a 1 MiB reserve; real write/sync failures still abort. ZIP paths, duplicate names, lengths and CRCs are checked without extraction. EPUB structural XML has an additional 2 MiB/document, 128-depth and 100,000-token cap. DTDs/entities are refused. Only local XHTML reading-order resources are accepted by this proof. These intentionally conservative checks are not a complete EPUB conformance certification.

PDF validation checks the header and opens the staged file with the platform `PdfRenderer`, requiring a nonempty page tree. This is not either candidate's reader integration and does not claim rendering, selection, search or highlighting. Password/restricted PDFs fail explicitly; no passwords are requested or DRM bypassed. Platform-native parsing has no application-configurable CPU/memory budget or isolated-process sandbox in this proof; the byte limit is not a proof of safety against every hostile PDF. EPUB remote references are never fetched during import; reader runtime isolation is tested separately and within the bounded EPUB proof below.

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

The result guard rejects missing named shell, repository, importer, real-provider and EPUB cases, plus all seven named PDF safety/proof cases in the AndroidX flavor, and any reported failure/error/skip. It does not require second-candidate PDF cases before Task 6. CI retains actual XML and failure diagnostics. An executed assertion of Unsupported or an asserted geometric limitation is **not** a native feature pass. API 28/API 37.0 remain the approved proof matrix, not a public supported-device promise. Physical devices, SAF picker automation, accessibility/TalkBack and actual cloud/document-provider grant UI flows are not established by host tests.

## Task 5 AndroidX PDF comparison

`PdfProofTest` exists only in `androidTestAndroidx`; the shared assertions are engine-neutral and reusable for the later independent candidate. `PdfLocationsTest` checks the flavor-local, engine-scoped original-space storage envelope and opaque native JSON; `ReaderViewModelTest` rejects EPUB/foreign-candidate PDF events. `VerifiedSourceTest` exercises streamed byte verification, canonical app-owned paths, absence/replacement and cancellation. The existing pinned `pdf-viewer-fragment:1.0.0-beta01` dependency is reused; no PDF parser, OCR provider or `pdf-ink` is added.

For a focused run on **each** disposable API 28 and API 37 emulator:

```sh
adb shell getprop ro.build.version.sdk
adb shell getprop | grep -i extension
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedAndroidxDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=im.commonplace.reader.reader.pdf.PdfProofTest
```

The result guard is intended for the **full** suite, not just this focused class. PDF tests log the actual API, S-extension and source checksum. The full-viewer route requires API 31 and extension 13, while minSdk stays 28; API 28 must execute and assert Unsupported with its reason, never silently skip. Beta01's metadata/backported rendering is not confused with the guide's full-viewer requirement. API 37 is not assumed to have a particular extension until measured.

Cases cover real native document rendering/page navigation/zoom, recreation with old native-view detachment, native document search/selection for multilingual/repeated strings, zero-based pages, explicit geometry refusal at 1×/2×, exact `No selectable text available` without diagnosing a scan, malformed/encrypted handling, and changed-byte association safety. The replacement test deliberately seeds a synthetic saved location plus quotation/reflection/source annotation/bookmark/progress; it proves storage retention and no native attachment after byte mismatch, **not** native capture success. Numeric PDF/overlay assertions remain 1 point/2 dp; no geometric pass is inferred from the deliberate refusal case.

Original crop/rotation mapping, saved-selection/highlight force-stop restoration, physical/TalkBack, StrictMode/profiler and the three-cycle 100 MiB performance budget remain unproved/not run. The one-page large fixture cannot measure 20-page navigation. These are candidate-acceptance gaps, recorded as rejection rather than relaxed thresholds. Existing EPUB process-death probes remain separate and cannot establish PDF recovery. See [the live evidence ledger](READER_PROOF.md) for actual commit/run results; a build or an Unsupported assertion cannot select the candidate.

### Recorded Task 5 outcome

[Run 39618958587](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39618958587) tests code/test commit `604ae0069285e5f0062c1740bb0b0795a46c5b44`: checks and API 28 pass; API 37 fails. Downloaded API 28 XML has 61 AndroidX / 54 other-flavor reports with zero failures/errors/skips; all seven PDF cases execute, with the native-viewer branches explicitly Unsupported. API 37 (`sdk_gphone64_x86_64`, x86_64, S-extension 22, recorded 4096-byte page size) has 61 AndroidX reports, **one failure**, zero errors/skips. All seven PDF cases execute. Native multilingual document search/selection, repeated text, no-text behavior, changed-byte retention, invalid/encrypted rejection and geometry-refusal safety assertions pass; the refusal is not a geometric feature pass.

The failed combined case first finds a rendered native view and asserts two pages, then calls `scrollToPage(1)` and sets zoom `2f`. Its 20-second wait for `firstVisiblePage == 1` times out. The following numeric zoom and recreation/old-view-detachment assertions are not reached. This result does not establish whether sequencing/viewport semantics or native behavior caused that page-state expectation to fail. It is retained as **FAIL**, without relaxing the test or claiming recreation passed. The other-flavor API 37 suite and later API 37 recovery phases are not run after that Gradle failure. The full report guard passes on API 28 and correctly fails on API 37. Required geometry, durable PDF recovery and performance gates remain unmet; AndroidX is not accepted.

The source-local `PdfThemeTest` was observed red before the Material-derived native theme correction and green afterward; it checks resources only. Final CI host XML has 86 AndroidX / 81 other-flavor JVM cases, zero failures/errors/skips, and both-flavor lint has zero errors / 16 retained warnings. Before publishing the documentation-only checkpoint, both JVM tasks are rerun explicitly (task option `--rerun`), followed by the same strict lint/app/test-APK gate above. Documentation-only publication preserves the tested app/test source and does not turn the failed device run green. Actual results, immutable fixture identities and artifact paths are in [READER_PROOF](READER_PROOF.md#completed-exact-source-device-result).

## Task 4 EPUB reader proof — verified, bounded scope

Task 4 is complete for its specified synthetic EPUB proof. [CI run 39599700257](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39599700257), at `d467a0880227eb0ee45d96128eb6d20609ce6233`, has three successful jobs. Downloaded Android XML for API 28 and API 37.0, both flavors, contains 54 reports per combination (216 total): 50 ordinary functional passes and four guarded no-op probes per combination. The 200 functional results comprise four shell, 52 repository, 100 importer, 16 provider, and 28 native-reader passes; each combination has seven native cases. The no-op probes are not death-test evidence. The named-case guard was rerun successfully against both downloaded report trees.

The native cases use Readium Kotlin 3.4.0's supported `EpubNavigatorFragment`. They include `nativeContainerSurvivesResizePanelTransitionsAndTeardown`, which passes three cycles with the same container/native view, exactly one navigator, controlled destruction, and closed resources; the preceding exact-native-selection/teardown case also passes. The corpus and proof scope are reflowable, UTF-8, non-encrypted synthetic EPUBs without standalone SVG resources. This is runtime evidence for those fixtures and matrix images, not general EPUB conformance or universal book/device compatibility. Fixed-layout and DRM remain outside scope.

Four separate reader recovery runs (one per flavor/API combination) each completed both one-test phases. Logs confirm readiness after capture/progress/draft acknowledgment, a live PID, kill and confirmed PID disappearance, `monkey` relaunch with a new PID, then fresh-process verification of quote/reflection, the complete stored locator, bookmark, progress, pending draft and native Decoration. This proves explicit source reopening and restoration in that test flow; it does not promise automatic launch of the last book. Four separate import-kill/reopen runs also each report one passing fresh-process verification. These are distinct from the four no-op probes in normal test XML.

The CI host XML has 76 tests per flavor (152 total), with zero failures, errors or skips. The 41 Python regressions pass, along with five fixture checks; the small corpus is byte-identical to its generated counterpart and the large fixture is generated outside APK assets. Strict both-flavor lint reports zero errors and 16 retained warnings per flavor; app and test APK assembly succeeds. These CI facts are recorded in the [reader-proof ledger](READER_PROOF.md).

The bounded runtime offline checks passed: local rendering, authored-publication-script removal, unsafe-URL handling and unknown-host denial at a controlled endpoint. The external-anchor case displays a confirmation prompt, then cancels and confirms zero endpoint requests; browser handoff after consent was not exercised. The app requests no `INTERNET` permission and adds no toolkit bridge, but the Readium JavaScript bridge exists and trusted Readium JavaScript is not disabled. Do not generalize these checks to universal offline behavior or claim JavaScript is disabled.

Automated controls verified system text scale 2.0, dark theme, landscape, and 48 dp control width/height. These do not establish broad WebView body typography behavior. Spoken TalkBack checks, tablet layout and physical-device testing, and actual SAF picker/cloud-provider grant UI flows were not run or established. API 28/API 37.0 are the proof matrix, not a public device-support promise. The AndroidX PDF comparison above is a separate task and is not accepted on the strength of this EPUB evidence.

## Original shared fixtures

[Fixture generation and licenses](../tools/fixtures/README.md) describe the pinned generator, bundled font notice and corpus. [The manifest](../app/src/androidTest/assets/fixtures/manifest.json) records exact bytes, hashes, authored strings, zero-based physical pages, absent genuine page labels, and reference geometry. The expected quads are conservative generator-authored font-box estimates, not engine-observed selection evidence. Multilingual coverage is Latin accents/Greek/Cyrillic, not a broad language/shaping guarantee.

```sh
python3 -m pip install -r tools/fixtures/requirements.txt
python3 -m unittest tools/fixtures/test_generate_fixtures.py -v
python3 tools/fixtures/generate_fixtures.py --output build/local-fixtures --large
```

The optional local large PDF is exactly 104,857,600 bytes, one page, SHA-256 `73c4e34e97311ce559c8755ee9c0e42f27f7fd6bc733db06a61a56fae0f69c6e`. It uses whitespace padding after EOF; engine parser tolerance and resource behavior must be measured later. Never commit it or generate it in regular Android assets: a Git ignore does not exclude a file from an APK. CI regenerates it in `build/fixture-regeneration`, validates exact size/hash/pages and compares the committed small corpus/manifest against the pinned generator. It is not bundled in the standard test APK.

## Historical Task 3 evidence

Task 3 was verified at `5260993` by [run 39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012): checks, API 28 and API 37.0 all pass. Downloaded XML and recovery logs were inspected, and the named-case guard was rerun against both downloaded API report trees.

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

Historical Task 2 device evidence is at [run 39549310739](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39549310739). No reader-engine behavior, physical-device support promise, SAF picker UI automation, real cloud-provider grant UI or universal hostile-document safety is proved by import success.

### Historical Task 4 local checkpoints

The notes below preserve intermediate checkpoint results only. Any statement that the final native or recovery matrix was pending describes that historical checkpoint and is superseded by the verified Task 4 evidence above.

The current Readium host passed both-flavor unit tests (**66 each, 132 total; zero failures/errors/skips**), lint, app assembly and instrumentation-APK assembly with `--dependency-verification=strict`; CI orchestration/result/recovery guards passed **36 Python regressions**. These are not on-device renderer results. A security-review finding concerning standalone SVG documents is fixed by rejecting SVG resources before navigator attachment and again at resource lookup; its MIME/decoded-href policy regression was observed red then green, and a narrow follow-up review accepted the fix. The hostile-publication device case includes a real authored-script SVG spine and must assert explicit unsupported/no navigator/zero controlled-endpoint requests. Standalone SVG resources are outside this bounded proof. Final native/API/recovery evidence remains pending in [READER_PROOF](READER_PROOF.md). The recovery probe also checks an acknowledged pending native draft across actual process death, not only committed captures. Launcher relaunch precedes explicit source reopening; automatic last-book launch is not claimed.

The startup-diagnostic follow-up adds two local bootstrap cases using the exact shared instrumentation fixture and a message-free failure-code privacy test. Both-flavor local JVM totals are now **69 each (138 total)** with zero failures/errors/skips; strict both-flavor lint/app/test-APK assembly passes. The original 36 Python guards are unchanged. These host checks do not turn the failed native run into a pass. The complete matrix and required named native/recovery assertions remain unchanged.

The native navigator wait must advance the Compose test-owned clock (`waitForIdle()`), not only sleep on the instrumentation thread. A local exact-fixture bootstrap under the same rule reproduced zero fragments before this fix and passed after it. The direct-container control remained passing. Both-flavor complete strict gates pass at 69 JVM cases each after the correction; the test helper retains real-fragment/view checks and all required native cases. The standalone recovery probe also declares a Compose rule; the later mandatory-clock correction below supersedes the earlier assumption that it used only Android frames. Native CI outcome is still reported separately in READER_PROOF.

After the CSP regression: 70 JVM tests per flavor (140 total), 36 Python guards and the complete strict both-flavor lint/app/test-APK gate pass locally. Native readiness requires chapter content and initialized toolkit JS; source-switch waits advance Compose. The proof checks painted highlight geometry, saved bookmark reopening, both importer/host SVG rejection paths and unknown-host script denial. These are strengthened assertions, not runtime passes; consult the exact CI evidence in READER_PROOF.

The launcher PID regression adds four deterministic guards (40 Python checks total): delayed startup is observed, absence is bounded, ADB errors fail immediately and a stale PID is rejected. A confirmed live fresh PID remains mandatory before restoration verification. Compose wait predicates permit a temporary missing semantics root, never a missing final match or longer timeout. Both-flavor JVM totals remain 70 each (140 total), and the complete strict local gate passes.

Resource-retirement regression: six new cases per flavor reproduce and guard closed ZIP/InputStream access, late reads, cached transforms, range/metadata, idempotent close and cancellation. JVM total is now 76 per flavor (152 total); 40 Python guards and the complete strict lint/app/test-APK gate pass locally. Actual native recreation and real process-death proof still require the full CI matrix.

Native harness synchronization preserves exact-locator assertions: wait for the requested chapter’s computed native position as well as trusted-script/DOM readiness. The 200% landscape check waits for real configuration recreation and restored configuration rather than forcing a second recreation during the first; its font/orientation and 48 dp assertions remain mandatory. Strict local gate remains 152 JVM/40 Python plus both-flavor lint and APKs; complete native/recovery matrix remains required.

Run 39593165874 passes all 24 actual native EPUB cases (six per API/flavor) but not separate recovery. Recovery stage and verify now pass their ComposeTestRule to a mandatory clock-driving navigator helper, replacing the unsafe optional no-op callback. This applies the already reproduced bootstrap clock fix to both real-death phases without dropping assertions or extending deadlines. Complete strict local checks remain green at 152 JVM/40 Python and both-flavor lint/APKs. Full recovery evidence remains pending.

## Task 6 ComposePdfReader comparison

Task 6 now adds the independent `androidTestComposepdf` PDF proof suite; the earlier Task 5 text describes its historical checkpoint. This is an experimental comparison, not accepted PDF support. Both APIs must execute the candidate's tests; AndroidX's known failure must not suppress this second candidate. Missing named PDF cases, failures, errors and skips remain guard failures.

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedComposepdfDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=im.commonplace.reader.reader.pdf.PdfProofTest
```

Generate the identical `large-100mib.local.pdf` outside APK assets with the existing fixture generator. Transport its bytes into the installed disposable Composepdf app's private `files/proof-large-100mib.pdf` before running the large test; the complete SHA-256 is in the unchanged fixture manifest. No missing fixture is treated as a skip or a memory pass. Painted page pixels, not the mere existence of a composable, are required by the renderer checks. Exact native extraction/search is not a UI-selection callback. Geometric refusal does not prove 1-point transforms or 2-dp restored overlays. Actual device outcomes, sampled memory and unrun phases will be recorded in [READER_PROOF](READER_PROOF.md#composepdfreader-comparison--task-6).

The verified native RED checkpoint is `7c51bf13e7e566c813a8fb2410dba8c9e6bfe8e6`, [run 39628912246](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39628912246): each API's focused Composepdf suite executed 9 cases with 8 failures and no skips; static checks passed. Its full Composepdf suite had an APK-signature installation failure and no valid XML, correctly rejected by the guard. The adapter checkpoint fixes local-signing/staging order without weakening any assertion. Use the locally assembled app for staging, not a checks-job APK signed by another machine's debug key:

```sh
./gradlew --no-daemon --dependency-verification=strict :app:assembleComposepdfDebug
adb install -r app/build/outputs/apk/composepdf/debug/app-composepdf-debug.apk
adb shell run-as im.commonplace.reader.composepdf mkdir -p files
adb exec-in run-as im.commonplace.reader.composepdf dd of=files/proof-large-100mib.pdf \
  < build/fixture-transport/large-100mib.local.pdf
# exec-in returning is not remote completion. Read back and verify before testing:
adb exec-out run-as im.commonplace.reader.composepdf cat files/proof-large-100mib.pdf | sha256sum
# Must equal 73c4e34e97311ce559c8755ee9c0e42f27f7fd6bc733db06a61a56fae0f69c6e.
# tools/run_emulator_smoke.py bounds/retries/verifies the streamed readback itself.
# Only then run the targeted connected test command above.
```

CI prepares the identical fixture, checks its SHA-256 before ADB and after a complete device readback, and independently runs AndroidX full, Composepdf full and Composepdf focused suites. The bounded readback streams to temporary disk, not a 100 MiB RAM buffer; a partial or absent device copy fails staging. It retains separate XML/log reports and preserves every failure even when another suite succeeds. `tools/run_pdf_recovery.py` independently requires acknowledged PDF source-annotation save, a live PID, force-stop, PID disappearance, launcher relaunch, a fresh PID and native reopen/retained-annotation assertions. Its two guarded probe methods are not annotation-recovery passes when returned as no-ops in normal suites. This is annotation-only safety, never durable PDF selection/highlight/bookmark/progress acceptance.

The large test attempts three warm-process open/close cycles, first painted-page latency, sampled Java-plus-native allocation/PSS peaks and raw `dumpsys meminfo` before/open/close snapshots. A failed cycle remains a failure and can prevent later cycles; do not fill missing readings with earlier or ordinary-file samples. Because UTP removes the app after connected tests, `files/pdf-performance.txt` may no longer be available to the post-suite pull. Raw snapshots are therefore also logged line-by-line with `PdfMemory`; final sampled peaks and `firstPaintMs=NOT_REACHED` survive in `PdfProof` logcat even on assertion failure. Inspect retained `test-results/{full-suite,pdf-focused}/composepdf` logs. Real failed crop/large screenshots are pulled from shell-owned temporary paths into `build/reports/emulator/screenshots`; the runner clears only its two known screenshot paths before each suite to prevent stale reuse. Screenshots are diagnostics, not replacements for native assertions. Cold-process timing and 20-page navigation are NOT RUN: the unchanged shared 100 MiB fixture has one physical page. Sampling cannot prove an absent transient copy; the inspected JNI explicitly makes a full copy of the required `ByteArray`. Async upstream disposal has no completion acknowledgment. `getconf PAGE_SIZE`, `/proc/meminfo`, AVD config and build properties are retained separately; a 4 KiB emulator is not a real 16 KiB/ARM runtime pass.

For checksum-enforced external native retrieval, run `python3 tools/verify_composepdf_native.py --retrieve-dir /tmp/pdfium-native`. It verifies all four versioned TGZs before opening them and rejects archive/path/symlink escapes. To repeat the downstream app clean-cache build, use a fresh checkout/output directory and fresh `GRADLE_USER_HOME`, then `./gradlew --no-daemon --no-build-cache --rerun-tasks --dependency-verification=strict :app:assembleComposepdfDebug`. Compare all packaged `.so` payloads and license assets to the verified AAR/notices, inspect each ELF with `readelf -lW`/`-dW`, and run `$ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 -v 4 <actual-apk>`. This is an app rebuild from pinned Maven artifacts plus an independently verified external retrieval path; no independently reproducible PDFium/JNI source rebuild is claimed. Final actual build, APK and device results are recorded in READER_PROOF rather than inferred from these commands.
