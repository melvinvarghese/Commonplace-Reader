# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.

Task 2 adds a Room-backed repository for sources, Unicode captures/reflections, source annotations, independent bookmarks/progress and recoverable capture drafts. Capture insertion and draft clearing share a transaction; attribution comes from the stored source. The schema and location contracts are documented in [DATA_MODEL](docs/DATA_MODEL.md). Both flavors pass 24 host tests each (including file-backed repository recovery/rollback), lint and app/test-APK builds; 15 Python CI-guard tests pass. Device repository verification is pending the implementation CI run. This is tested storage infrastructure, not a user-facing notebook or editor.

The earlier [shell validation](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535925337) at `ef778c6` passed genuine launch/navigation instrumentation for both flavors on API 28 and API 37.0 (four executed tests). Task 2 adds file-backed repository instrumentation and host tests; its exact verification outcomes are recorded in [development notes](docs/DEVELOPMENT.md). A successful shell launch or durable record test is not proof that a reader works.

Not implemented yet: document import, EPUB/PDF reading, capture/editor UI, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

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

[CI](https://github.com/melvinvarghese/Commonplace-Reader/actions) builds both flavors and runs shell and file-backed repository instrumentation on API 28 and API 37. Its result guard requires each named durability case in both flavors. Workflow creation does not mean a run has passed. See [DEVELOPMENT](docs/DEVELOPMENT.md) for exact dependencies, prerequisites, evidence and limitations.

## Reader comparison

EPUB will use Readium Kotlin. PDF remains undecided: the approved comparison is AndroidX PDF versus [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader). Their pinned dependencies resolve in separate flavors, but no reader capability has been proved yet. AndroidX's full viewer requires Android 12/API 31 and SDK extension 13; this limitation will be tested without raising the app's API 28 baseline.

## Privacy and license

The foundation requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Runtime offline behavior and later publication-resource policies still require testing.

Commonplace is licensed under [Apache-2.0](LICENSE), as authorized by the owner; earlier Git history is preserved. Incorporated components retain their own terms: see [third-party notices](THIRD_PARTY_NOTICES.md). The project license does not relicense dependencies.

The [architecture](docs/ARCHITECTURE.md) and [approved implementation plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) describe the intended milestone, not already-shipped features.
