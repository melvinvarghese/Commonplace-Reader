# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.


The earlier [shell validation](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535925337) at `ef778c6` passed genuine launch/navigation instrumentation for both flavors on API 28 and API 37.0 (four executed tests). Task 2 adds file-backed repository instrumentation and host tests; its exact verification outcomes are recorded in [development notes](docs/DEVELOPMENT.md). A successful shell launch or durable record test is not proof that a reader works.

Task 3 adds bounded app-private PDF/EPUB imports, checksum duplicate handling, missing-asset reconciliation and original shared fixtures. [Verified CI run 39556891012](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39556891012) at `5260993` passes both flavors on API 28/API 37: 172 functional instrumentation checks and four separate real process-kill/reopen verifications, plus 100 host tests, 27 Python regressions, five fixture checks, lint (zero errors; warnings retained) and app/test-APK builds. Device tests cover unknown-length provider pipes, spoofed metadata, explicit malformed/encrypted rejection and reopening app-owned bytes after simulated original-provider revocation. These are bounded import proofs, not reader compatibility claims. See [TESTING](docs/TESTING.md) for exact report counts, guardrails, reproducible fixture generation and limits.

Task 4's EPUB reader implementation and proof are in progress. The test-first CI run [39560371312](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39560371312) recorded expected native-result failures on API 28 and API 37 plus an ancillary empty-shell isolation failure; the reviewed host now passes 132 JVM checks, 36 Python regressions, both-flavor lint and app/test-APK builds with strict dependency verification, but final native runtime verification is pending. Do not treat the reader as usable or proven. Current intended proof scope is limited to reflowable, UTF-8, non-encrypted EPUBs without standalone SVG resources in the synthetic corpus; fixed-layout and DRM/encrypted EPUBs, all PDF reading (Task 5), full editing, collection search, Markdown export, backup/restore, and Obsidian integration remain outside this proof. See the pending [reader-proof ledger](docs/READER_PROOF.md) and [testing notes](docs/TESTING.md).

Not implemented: PDF reading, a full capture/editor UI, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

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

[CI](https://github.com/melvinvarghese/Commonplace-Reader/actions) builds both flavors and runs shell, file-backed repository, importer/provider and separate process-kill checks on API 28 and API 37. Its result guard requires each named durability/import/provider/native-reader case in both flavors. Workflow creation does not mean a run has passed. See [DEVELOPMENT](docs/DEVELOPMENT.md) for exact dependencies, prerequisites, evidence and limitations.

## Reader comparison

EPUB uses the Readium Kotlin 3.4.0 navigator in the in-progress Task 4 implementation; local build verification passes but final device evidence is pending, so no EPUB reader capability is yet claimed as passed. PDF remains undecided: the approved comparison is AndroidX PDF versus [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader). AndroidX's full viewer requires Android 12/API 31 and SDK extension 13; this limitation will be tested without raising the app's API 28 baseline.

## Privacy and license

The foundation requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Runtime offline behavior and later publication-resource policies still require testing.

Commonplace is licensed under [Apache-2.0](LICENSE), as authorized by the owner; earlier Git history is preserved. Incorporated components retain their own terms: see [third-party notices](THIRD_PARTY_NOTICES.md). The project license does not relicense dependencies.

The [architecture](docs/ARCHITECTURE.md) and [approved implementation plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) describe the intended milestone, not already-shipped features.
