# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.


The earlier [shell validation](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535925337) at `ef778c6` passed genuine launch/navigation instrumentation for both flavors on API 28 and API 37.0 (four executed tests). Task 2 added file-backed repository instrumentation and host tests; its exact outcomes are in [development notes](docs/DEVELOPMENT.md). These historical checks alone did not prove a reader worked.

Task 3 added bounded app-private PDF/EPUB imports, checksum duplicate handling, missing-asset reconciliation and original shared fixtures. Its historical [CI run 39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012) at `5260993` passed both flavors on API 28/API 37: 172 functional instrumentation checks and four separate real process-kill/reopen verifications, plus 100 host tests, 27 Python regressions, five fixture checks, lint (zero errors; warnings retained) and app/test-APK builds. Device tests covered unknown-length provider pipes, spoofed metadata, explicit malformed/encrypted rejection and reopening app-owned bytes after simulated original-provider revocation. These were bounded import proofs, not reader compatibility claims. See [TESTING](docs/TESTING.md) for exact report counts, guardrails, reproducible fixture generation and limits.

Task 4's bounded EPUB reader proof is complete in [CI run 39599700257](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39599700257) at `d467a0880227eb0ee45d96128eb6d20609ce6233` (three jobs successful). Across API 28 and API 37.0, both flavors report 216 instrumentation results: 200 functional passes and 16 guarded no-op probes; all four real reader process-death/reopen verifications pass. The 28 native-reader cases include same-container resize/panel/teardown coverage. The strict host gate also passes 152 JVM checks, 41 Python regressions, fixture checks, both-flavor lint and app/test-APK builds. The evidence is bounded to the synthetic, reflowable, UTF-8, non-encrypted EPUB corpus without standalone SVG resources; it is not universal EPUB/device certification. Task 5 has not started: PDF reading, full editing, collection search, Markdown export, backup/restore and Obsidian integration remain unimplemented. See the [reader-proof ledger](docs/READER_PROOF.md) and [testing notes](docs/TESTING.md) for evidence and limits.

Not implemented: PDF reading (Task 5 has not started), a full capture/editor UI, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

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

EPUB uses the Readium Kotlin 3.4.0 `EpubNavigatorFragment`; the bounded Task 4 device and recovery evidence is complete for the synthetic corpus described above. This does not establish compatibility with arbitrary publications or devices. PDF remains undecided: the approved comparison is AndroidX PDF versus [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader). AndroidX's full viewer requires Android 12/API 31 and SDK extension 13; Task 5 has not started, and this limitation must be tested without raising the app's API 28 baseline.

## Privacy and license

The app requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Bounded Task 4 runtime checks confirmed local rendering, authored-script removal, unsafe-URL handling, unknown-host denial, and explicit external-link confirmation. These checks do not disable trusted Readium JavaScript or establish universal offline behavior; see [TESTING](docs/TESTING.md).

Commonplace is licensed under [Apache-2.0](LICENSE), as authorized by the owner; earlier Git history is preserved. Incorporated components retain their own terms: see [third-party notices](THIRD_PARTY_NOTICES.md). The project license does not relicense dependencies.

The [architecture](docs/ARCHITECTURE.md) and [approved implementation plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) describe the intended milestone, not already-shipped features.
