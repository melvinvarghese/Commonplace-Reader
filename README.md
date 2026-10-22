# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&size=14&duration=2500&pause=1000&color=8B949E&center=true&vCenter=true&width=350&lines=%3E+work+in+progress;%3E+under+active+development;%3E+coming+soon..." alt="Work in progress" />
</p>

## Commonplace notebook

The Commonplace tab now browses saved writing, filters by type, creates thoughts/annotations/source notes, and edits all four entry types. Highlight quotations, original source attribution and full native locations remain read-only. Separate on-device drafts can be resumed or discarded without deleting saved entries; failed or conflicting saves retain the input.

Supported EPUB highlights offer **Open passage** using the saved entry ID, not caller-supplied coordinates. Missing/replaced documents and unresolved locations retain readable writing instead of claiming a different passage succeeded. PDF remains experimental and unaccepted.

Notebook host/build checks and actual draft-recovery runs pass. **Milestone acceptance is blocked:** same-book passage switching fails on both tested Android versions/flavors, and the existing saved-capture reopening case fails on API 28. The [notebook evidence](docs/NOTEBOOK_PROOF.md) records exact results, review limits and the debug trial APK. This is not a release or a promise of dependable passage navigation.

## First reader milestone: proof complete, no PDF selection

**NO SELECTION:** both approved PDF candidates have been evaluated and rejected for milestone acceptance. Neither integration meets the required exact selection, original-space geometry, durable highlights and recovery gate. Both experimental comparison flavors and their source remain reproducible; minSdk stays **28**. No third engine has been added and no required PDF feature has been dropped. PDF engine work awaits the owner's decision; the separately approved notebook milestone does not select an engine.

The [final decision table](docs/READER_PROOF.md#final-milestone-decision--task-7) separates PASS, FAIL and NOT RUN by reader and platform. This is a development foundation/proof, **not a beta, a release-ready APK or a public device-support promise**.

### Implemented and verified within the proof

You can build and install a debug flavor, use Library and Settings & data in light/dark mode, import local EPUB/PDF copies with bounded validation and checksum duplicate handling, and read the supported reflowable EPUB fixtures offline. The EPUB reader's small proof panel supports native passage captures with reflection/color, source annotations, independent bookmarks, progress and reopening saved passages. The separately approved notebook adds writing/editing outside that proof panel. Originals remain untouched; missing/replaced source bytes do not silently delete writing or apply incompatible coordinates.

[Task 4 run 39599700257](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39599700257), at `d467a0880227eb0ee45d96128eb6d20609ce6233`, passed both flavors on API 28 and API 37.0: **28 native EPUB cases**, **four real reader kill/relaunch recoveries**, **four interrupted-import recoveries**, **152 JVM tests**, **41 Python regressions**, **five fixture checks**, strict lint and app/test-APK builds. Ordinary XML contains 216 results: 200 functional passes and 16 guarded no-op probes; the latter are not recovery evidence.

This proof is bounded to synthetic reflowable, UTF-8, non-encrypted EPUBs without standalone SVG resources. It is not arbitrary-publication compatibility. [Testing notes](docs/TESTING.md) record import/provider safety, recovery, exact counts and limitations.

### Experimental comparisons — rejected, source retained

| PDF flavor | Exact completed source run | Result |
| --- | --- | --- |
| `androidx` — AndroidX PDF `1.0.0-beta01` | [39618958587](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39618958587), `604ae0069285e5f0062c1740bb0b0795a46c5b44` | API 28 asserts explicit full-viewer unavailability; API 37 renders/searches/extracts exact text but fails navigation/zoom. Later recreation assertions were not reached. Original crop/rotation geometry and durable highlights remain unavailable. |
| `composepdf` — NucleusFramework ComposePdfReader `158.0.8111.0` | [39637129857](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39637129857), `193105c528549431bade033045b0e8237885d095` | Both emulator jobs fail; focused PDF results are 6/9 API 28 and 7/9 API 37, zero skips. Ordinary native rendering/search and both real source-annotation restart probes pass; exact app capture, durable highlights and saved navigation remain unavailable. |

ComposePdf's failure screenshots contain glyphs: its small-viewport pixel predicate is inconclusive, not proof of blank rendering. The 100 MiB API 28 fixture was truncated in transport; API 37 exposed roughly 4 GiB despite a requested 2 GiB, so matched-profile performance remains unproven. Its native byte-array copy concern, packaging/provenance audit and raw memory readings are in the [proof ledger](docs/READER_PROOF.md#final-task-6-outcome--evaluated-not-accepted). Source-annotation recovery does not prove native highlight recovery.

### Untested or planned

Spoken TalkBack, tablets, physical devices, ARM and actual 16-KiB runtime, complete PDF cleanup/performance, and full transitive redistribution clearance remain unproven. Automated EPUB 200% system-text, dark/landscape and 48-dp control checks are not a manual accessibility audit.

Not implemented: an accepted durable PDF reader, collection Search, tags/collections, collection indexing, Markdown export, backup/restore, removal/reattachment UI, or Obsidian/sync. The notebook does not include finished-entry deletion. In-document reader search is not collection search. No account, OCR, DRM bypass or release publication is included.

## Build and install a proof flavor

Use a full JDK 17, Android SDK `platforms;android-37.0`, and Build Tools `36.0.0`; set `JAVA_HOME` and `ANDROID_HOME` (or an untracked `local.properties`). The Gradle 9.7.0 wrapper checks its distribution checksum. Exact pins and setup are in [DEVELOPMENT](docs/DEVELOPMENT.md).

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:testAndroidxDebugUnitTest :app:testComposepdfDebugUnitTest \
  :app:lintAndroidxDebug :app:lintComposepdfDebug \
  :app:assembleAndroidxDebug :app:assembleComposepdfDebug
```

| Flavor | Provisional package ID | Locally built debug APK |
| --- | --- | --- |
| AndroidX | `im.commonplace.reader.androidx` | `app/build/outputs/apk/androidx/debug/app-androidx-debug.apk` |
| ComposePdf | `im.commonplace.reader.composepdf` | `app/build/outputs/apk/composepdf/debug/app-composepdf-debug.apk` |

Install the chosen comparison APK with `adb install -r <apk-path>` on a disposable device. There is no published release download. API 28 and API 37.0 x86_64 Google APIs emulators are the tested matrix, not all supported hardware. EPUB is shared across flavors; PDF behavior remains experimental in both.

Existing instrumentation and original fixture commands:

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedAndroidxDebugAndroidTest :app:connectedComposepdfDebugAndroidTest
python3 -m pip install -r tools/fixtures/requirements.txt
python3 -m unittest tools/fixtures/test_generate_fixtures.py -v
python3 tools/fixtures/generate_fixtures.py --output build/local-fixtures --large
```

The retained full device matrix has known PDF failures; these commands are not a promise of green results. The large fixture stays outside APK assets. [TESTING](docs/TESTING.md) gives separate focused/recovery commands and guard requirements. [CI](https://github.com/melvinvarghese/Commonplace-Reader/actions) retains exact-source evidence; docs-only status commits use `[skip ci]` to avoid duplicate runs, not to conceal code changes.

## Privacy and license

The app requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Bounded EPUB runtime checks passed local rendering, authored-script removal, unsafe-URL handling, unknown-host denial and external-link confirmation/cancellation. Trusted Readium JavaScript and its bridge remain; universal hostile-document safety or affirmative browser handoff was not established.

The project is [Apache-2.0](LICENSE); earlier history is preserved. Dependencies retain their own licenses. [Third-party notices](THIRD_PARTY_NOTICES.md) document artifact identities, bundled notices and remaining provenance/runtime limits, not blanket legal clearance.

## Project documents

[Approved architecture and milestone status](docs/ARCHITECTURE.md) · [Approved seven-task plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) · [Development](docs/DEVELOPMENT.md) · [Tests and limits](docs/TESTING.md) · [Data model](docs/DATA_MODEL.md) · [Reader decision and historical proof](docs/READER_PROOF.md)

[Notebook specification](docs/superpowers/specs/2026-10-22-commonplace-notebook-design.md) · [Notebook implementation plan](docs/superpowers/plans/2026-10-22-commonplace-notebook.md) · [Notebook evidence and limits](docs/NOTEBOOK_PROOF.md)
