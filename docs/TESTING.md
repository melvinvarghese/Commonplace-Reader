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
