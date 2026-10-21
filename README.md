# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.


The earlier [shell validation](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535925337) at `ef778c6` passed genuine launch/navigation instrumentation for both flavors on API 28 and API 37.0 (four executed tests). Task 2 added file-backed repository instrumentation and host tests; its exact outcomes are in [development notes](docs/DEVELOPMENT.md). These historical checks alone did not prove a reader worked.

Task 3 added bounded app-private PDF/EPUB imports, checksum duplicate handling, missing-asset reconciliation and original shared fixtures. Its historical [CI run 39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012) at `5260993` passed both flavors on API 28/API 37: 172 functional instrumentation checks and four separate real process-kill/reopen verifications, plus 100 host tests, 27 Python regressions, five fixture checks, lint (zero errors; warnings retained) and app/test-APK builds. Device tests covered unknown-length provider pipes, spoofed metadata, explicit malformed/encrypted rejection and reopening app-owned bytes after simulated original-provider revocation. These were bounded import proofs, not reader compatibility claims. See [TESTING](docs/TESTING.md) for exact report counts, guardrails, reproducible fixture generation and limits.

Task 4's bounded EPUB reader proof is complete in [CI run 39599700257](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39599700257) at `d467a0880227eb0ee45d96128eb6d20609ce6233` (three jobs successful). Across API 28 and API 37.0, both flavors report 216 instrumentation results: 200 functional passes and 16 guarded no-op probes; all four real reader process-death/reopen verifications pass. The 28 native-reader cases include same-container resize/panel/teardown coverage. The strict host gate also passes 152 JVM checks, 41 Python regressions, fixture checks, both-flavor lint and app/test-APK builds. The evidence is bounded to the synthetic, reflowable, UTF-8, non-encrypted EPUB corpus without standalone SVG resources; it is not universal EPUB/device certification. See the [reader-proof ledger](docs/READER_PROOF.md) and [testing notes](docs/TESTING.md) for subsequent PDF evidence and limits.

Task 5's **experimental AndroidX PDF comparison is evaluated but rejected for milestone acceptance**. [Exact-source run 39618958587](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39618958587), at `604ae0069285e5f0062c1740bb0b0795a46c5b44`, passes checks and API 28 but fails the API 37 page-navigation/zoom scenario. Native API 37 rendering, multilingual document search/selection, repeated-text search and no-text handling have bounded passing evidence; recreation assertions after the failed navigation wait were not reached. The full-viewer path remains explicitly unavailable below API 31 / SDK extension 13, without raising app minSdk 28. Original crop/rotation mapping, durable highlight recovery and required performance evidence remain unproven; no capture geometry or PDF bookmark/progress restoration is fabricated. Changed-byte retention passes on both APIs. The [ledger](docs/READER_PROOF.md) separates safety passes, feature failures and tests not run. No PDF engine is selected, and Task 6 has not started.

Not implemented: an accepted durable PDF reader, a full capture/editor UI, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

## Build

Use JDK 17, Android SDK platform `platforms;android-37.0`, and Build Tools `36.0.0`. Set `ANDROID_HOME` to your SDK location (or use an untracked `local.properties`). The checked-in Gradle 9.7.0 wrapper verifies its distribution checksum.

```sh
./gradlew :app:testAndroidxDebugUnitTest :app:testComposepdfDebugUnitTest \
  :app:lintAndroidxDebug :app:lintComposepdfDebug \
  :app:assembleAndroidxDebug :app:assembleComposepdfDebug
```

Debug APKs are written under `app/build/outputs/apk/{androidx,composepdf}/debug/`. Package IDs are `im.commonplace.reader.androidx` and `im.commonplace.reader.composepdf`; the ID is provisional, not a domain-ownership claim. API 28 is the proof baseline, **not yet a verified public support promise**.

With an emulator/device connected:

```sh
./gradlew :app:connectedAndroidxDebugAndroidTest :app:connectedComposepdfDebugAndroidTest
```

[CI](https://github.com/melvinvarghese/Commonplace-Reader/actions) builds both flavors and runs shell, file-backed repository, importer/provider, native-reader and separate process-kill checks on API 28 and API 37. The verified Task 4 run is linked above; its result guard requires each named case in both flavors. See [DEVELOPMENT](docs/DEVELOPMENT.md) and [TESTING](docs/TESTING.md) for prerequisites, evidence and limits.

## Reader comparison

EPUB uses the Readium Kotlin 3.4.0 `EpubNavigatorFragment`; the bounded Task 4 device and recovery evidence is complete for the synthetic corpus described above. This does not establish compatibility with arbitrary publications or devices. PDF remains undecided: the approved comparison is AndroidX PDF versus [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader). The pinned AndroidX beta01 metadata permits API 28 backported rendering, while its full-viewer guide requires API 31 and SDK extension 13. The Task 5 comparison conservatively gates the full-viewer path and retains the API 28 app baseline. Its observed navigation/zoom scenario failure and missing original-space geometry, durable recovery and performance evidence block acceptance. The independent second candidate remains Task 6; none of the seven approved milestone tasks is removed.

## Privacy and license

The app requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Bounded Task 4 runtime checks confirmed local rendering, authored-script removal, unsafe-URL handling, unknown-host denial, and explicit external-link confirmation. These checks do not disable trusted Readium JavaScript or establish universal offline behavior; see [TESTING](docs/TESTING.md).

Commonplace is licensed under [Apache-2.0](LICENSE), as authorized by the owner; earlier Git history is preserved. Incorporated components retain their own terms: see [third-party notices](THIRD_PARTY_NOTICES.md). The project license does not relicense dependencies.

The [architecture](docs/ARCHITECTURE.md) and [approved implementation plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) describe the intended milestone, not already-shipped features.

## Task 6 comparison checkpoint

Task 6 has now started, superseding only the earlier checkpoint's “not started” status above. The `composepdf` flavor evaluates the pinned NucleusFramework ComposePdfReader candidate, with engine identity `nucleus-composepdf`; it is an **experimental second comparison build only**, not the selected PDF reader. The test-first native run failed 8 of 9 focused PDF cases on each API before the host existed. The adapter now provides native page rendering/text search and temporary page/zoom controls, awaiting exact-source runtime validation. Precise capture, saved highlights, bookmarks, durable progress and jump-back remain unavailable; source annotation retention is tested separately. Native payload identities and notices are audited, but ARM/real 16 KiB runtime and full lifecycle/performance clearance remain unproven. [READER_PROOF](docs/READER_PROOF.md#composepdfreader-comparison--task-6) records the actual results and gaps. Task 5's failed evidence and every acceptance threshold remain unchanged. Task 7 has not started.

The implemented-host [run 39632235542](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39632235542) at `3c1c60a` passed static checks but failed both native jobs: Composepdf focused PDF results were 6/9 passing on API 28 and 7/9 on API 37, with no skips. Crop/paint and large-fixture/rendering evidence failed; both separate real source-annotation death/reopen probes passed, not durable highlight recovery. A fresh-checkout/fresh-Gradle-home app rebuild and all-ABI payload/notice inspection passed; this is not native-source reproducibility or ARM/16 KiB runtime certification. Follow-up changes collect missing evidence only. The candidate remains unaccepted; Task 7 is still pending.
