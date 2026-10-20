# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.

Both temporary PDF comparison flavors pass local JVM tests and Android lint, and produce debug app and instrumentation APKs. Each flavor currently has one passing JVM test. Both shells also pass launch/navigation instrumentation on a CI API 28 emulator (one test per flavor). API 37.0 runtime is blocked: after correcting emulator RAM/userdata defaults, current-image storage-service and activity-resolution failures remain. CI rejects missing, skipped or failed shell tests, including a previously observed false-green Gradle exit with zero tests. The latest [CI run](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39462999877) passes checks/API 28 but fails API 37. Details are tracked in [development notes](docs/DEVELOPMENT.md). A successful build is not proof that a reader works.

Not implemented yet: document import, EPUB/PDF reading, durable notes/highlights/bookmarks/progress, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

The next CI diagnostic captures live package/storage-service health and direct-launch evidence before emulator shutdown, without changing SDK levels, dependencies or test assertions. These probes are not substitutes for passing instrumentation; Task 2 remains paused pending the API 37 result.

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

[CI](https://github.com/melvinvarghese/Commonplace-Reader/actions) builds both flavors and schedules shell smoke tests on API 28 and API 37. Workflow creation does not mean a run has passed. See [DEVELOPMENT](docs/DEVELOPMENT.md) for exact dependencies, prerequisites, evidence and limitations.

## Reader comparison

EPUB will use Readium Kotlin. PDF remains undecided: the approved comparison is AndroidX PDF versus [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader). Their pinned dependencies resolve in separate flavors, but no reader capability has been proved yet. AndroidX's full viewer requires Android 12/API 31 and SDK extension 13; this limitation will be tested without raising the app's API 28 baseline.

## Privacy and license

The foundation requests no `INTERNET` permission and excludes private stores from Android cloud backup and device transfer. Runtime offline behavior and later publication-resource policies still require testing.

Commonplace is licensed under [Apache-2.0](LICENSE), as authorized by the owner; earlier Git history is preserved. Incorporated components retain their own terms: see [third-party notices](THIRD_PARTY_NOTICES.md). The project license does not relicense dependencies.

The [architecture](docs/ARCHITECTURE.md) and [approved implementation plan](docs/superpowers/plans/2026-10-20-foundation-reader-proof.md) describe the intended milestone, not already-shipped features.
