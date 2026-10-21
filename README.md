# Commonplace

**A home for the ideas you keep.** An offline-first Android reader and personal commonplace book, in development.

## Current state: development foundation

The native Kotlin/Compose shell has Library and Settings & data destinations, an honest empty library, and light/dark colors. It contains no sample books or nonfunctional export/sync controls.

Both temporary PDF comparison flavors pass unit/lint/build checks and genuine shell launch/navigation instrumentation on API 28 and API 37.0 in the single [tool-selection validation](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535925337) at `ef778c6`. Downloaded instrumentation XML confirms one executed passing `ShellTest.launchAndNavigate` per flavor per API: four tests total, zero failures/errors/skips. Thirteen Python guard/runner regression tests also pass. JVM reports contain one passing case per flavor restored from older cached outputs, not newly executed JVM cases. A successful shell launch is not proof that a reader works.

Not implemented yet: document import, EPUB/PDF reading, durable notes/highlights/bookmarks/progress, Commonplace and Search destinations, Markdown export, backup/restore, or Obsidian integration. This is not a beta or a release-ready APK.

The API 37 tool-selection fix uses the newer command-line tools already installed by the unchanged setup action, rather than the pinned emulator runner's older `latest` directory. Pre-boot evidence records `/usr/local/lib/android/sdk/cmdline-tools/16111833/bin/avdmanager`, revision **23.0**, and outer `test.ini` **`target=android-37.0`**. A hard assertion stops before boot if that target is wrong. Image, runner, toolchain and dependency pins, requested RAM/userdata/GPU settings, API 28 control, app assertions and executed-test guards remain unchanged. No second workflow or rerun was started.

The earlier [setup-only diagnostic](https://github.com/melvinvarghese/Commonplace-Reader/actions/runs/39535057134) established that the runner had selected revision 12.0 and generated `target=android-0`. With the selection corrected, the prior graphics assertion is absent from retained boot/live captures and package/activity/mount services remain present through the test/probe snapshots. This single successful comparison clears the observed API 37 shell gate; it does not establish long-term emulator stability or reader compatibility. Task 2 has not started and is outside this bounded fix. See [development notes](docs/DEVELOPMENT.md) for evidence and earlier failed experiments.

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
