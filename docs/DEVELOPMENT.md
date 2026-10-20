# Developing Commonplace

## Build the foundation

Use a full **JDK 17** (not just a runtime), the Android command-line tools and the following SDK packages:

```sh
sdkmanager 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testAndroidxDebugUnitTest :app:testComposepdfDebugUnitTest \
  :app:lintAndroidxDebug :app:lintComposepdfDebug \
  :app:assembleAndroidxDebug :app:assembleComposepdfDebug \
  :app:assembleAndroidxDebugAndroidTest :app:assembleComposepdfDebugAndroidTest
```

Do not commit `local.properties`, SDK paths, private documents or build outputs. One application module has two temporary `pdfEngine` flavors: `androidx` and `composepdf`. Their dependencies are isolated; an engine decision has not been made. The artifact coordinates for the NucleusFramework/ComposePdfReader project are `dev.nucleusframework:pdfium`, not a similarly named third-party viewer.

## Pinned build and library versions

| Component | Pin / configuration |
| --- | --- |
| Gradle wrapper | 9.7.0; distribution SHA-256 `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae` |
| Android Gradle Plugin | 9.4.0 |
| Kotlin / Compose compiler plugin | 2.4.20 |
| KSP | 2.3.12 |
| Java compiler / bytecode | JDK 17 / JVM 17 |
| Android compile / target | API 37, compile SDK extension 22 |
| Android baseline / Build Tools | API 28 / 36.0.0 |
| Compose BOM | 2026.09.00 (UI 1.12.1, Material3 1.4.0) |
| Activity / Lifecycle / Core | 1.13.0 / 2.11.0 / 1.19.0 |
| Coroutines / desugaring | 1.11.0 / 2.1.5 |
| Readium navigator | `org.readium.kotlin-toolkit:readium-navigator:3.4.0` |
| AndroidX PDF | `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` |
| ComposePdfReader PDFium | `dev.nucleusframework:pdfium:158.0.8111.0` |
| Room / DataStore (reserved catalog pins, not integrated yet) | 2.8.5 / 1.2.1 |
| JUnit / AndroidX test JUnit / runner / Espresso | 4.13.2 / 1.3.0 / 1.7.0 / 3.7.0 |

The checked-in version catalog is the executable source of these pins. AGP's built-in Kotlin and new DSL are disabled for the selected explicit Kotlin/KSP plugin wiring; these compatibility flags are recorded rather than silently treated as AGP defaults. Both variants resolve and compile with this configuration. KSP is wired in preparation for Task 2; no database exists yet.

Selection evidence: [AGP 9.4 requirements](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Kotlin 2.4.20](https://kotlinlang.org/docs/whatsnew2420.html), [Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html), [KSP releases](https://github.com/google/ksp/releases), [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Readium docs](https://readium.org/kotlin-toolkit/), [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore). Published [Readium POM](https://repo.maven.apache.org/maven2/org/readium/kotlin-toolkit/readium-navigator/3.4.0/readium-navigator-3.4.0.pom) and [Nucleus metadata](https://repo.maven.apache.org/maven2/dev/nucleusframework/pdfium/158.0.8111.0/pdfium-158.0.8111.0.module) were inspected before resolution.

## Verified locally

The approved linked worktree builds with Ubuntu OpenJDK `17.0.20.1+1-1~24.04`, compiler `17.0.20.1`. The original default Java runtime was 21; set `JAVA_HOME` explicitly to avoid mixing runtimes. Installed SDK packages: command-line tools 23.0.0, platform-tools 37.0.1, Build Tools 36.0.0, platform API 28 revision 6 and API 37.0 revision 2.

Command-line tools archive `commandlinetools-linux-16111833_latest.zip` was checked against Google's repository SHA-1 `e025545c62a8e64c7559119566a569fb1dec5f60` before extraction. JDK was installed through Ubuntu's package manager. The official Gradle distribution was verified against its published SHA-256. Wrapper JAR SHA-256 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` matches the [official wrapper checksum](https://downloads.gradle.org/distributions/gradle-9.7.0-wrapper.jar.sha256).

Local checks on the foundation: both flavors' unit tests (one test each, zero failures), lint, app assembly and instrumentation APK assembly pass. Smoke-test compilation initially exposed a missing `performClick` extension import; it was corrected and both test APKs then compiled. The Compose test-rule API emits a deprecation warning; it does not prevent compilation. Lint reports zero errors and six warnings per flavor: newer Gradle/AGP/Core/Kotlin/Compose-plugin versions, and a missing launcher icon. Pins are intentional; icon polish remains outstanding. No device/runtime success is inferred from these checks.

`gradle/verification-metadata.xml` records SHA-256 values for resolved build, app and test dependencies. Initial generation is trust-on-first-use, not independent cryptographic proof of upstream provenance. Subsequent builds use strict verification. When intentionally changing dependencies, inspect artifacts and regenerate only the needed metadata; do not disable verification to bypass a mismatch.

## Device and CI gates

This sandbox has no `/dev/kvm`, so no hardware-accelerated emulator run has occurred locally. Physical-device testing, TalkBack, 200% text, rotation/tablet layout, offline behavior and process-death restoration have **not run**. No reader/persistence feature exists yet to evaluate.

The workflow pins reviewed action revisions and the Temurin JDK 17 major (patch updates are not frozen). It validates the wrapper via Gradle setup, installs the compile SDK and runs both unit/lint/build variants. Separate API 28/API 37.0 x86_64 Google APIs emulator jobs run both `ShellTest.launchAndNavigate` variants. Test results and debug APKs are retained for seven days; only synthetic/empty-shell data is allowed in artifacts. The first CI run failed during Android setup because the action default requested the removed legacy `tools` package. Setup now explicitly pins command-line tools archive `16111833` and requests only `platform-tools`. The second run reached Gradle but strict verification found four missing parent/BOM metadata checksums. An empty-cache local run reproduced this; clean resolution exposed that Gradle's generator omits some intermediate parent/BOM metadata after conflict resolution. The four exact files were fetched from Maven Central, checked against the clean-cache bytes and pinned explicitly. Both variants' unit/lint/app/test-APK tasks pass with `--refresh-dependencies --dependency-verification=strict`. CI remains unverified until a complete run passes.

```sh
./gradlew :app:connectedAndroidxDebugAndroidTest :app:connectedComposepdfDebugAndroidTest
```

[Android Emulator Runner](https://github.com/ReactiveCircus/android-emulator-runner) documents the KVM-enabled Ubuntu setup used here. A passing shell smoke test will establish launch/navigation only, not reader support.

## PDF comparison limits

The [AndroidX viewer guide](https://developer.android.com/media/grow/implement-pdf-viewer) requires API 31 plus SDK extension 13 for the full viewer. API 28 stays the app baseline and must produce an explicit unavailable capability during that candidate proof, rather than an attempted unsupported viewer launch. [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/pdf) describing backported lower-level rendering do not establish full viewer compatibility.

Readium is scoped to EPUB. The Nucleus PDFium artifact includes native code and the project documents four Android ABIs; packaged ABIs, native notices/provenance and 16 KB checks are tracked in [third-party notices](../THIRD_PARTY_NOTICES.md). ELF segment alignment alone does not prove loading or rendering on a 16 KB device. Neither PDF candidate has passed selection, search, highlight geometry, memory or lifecycle gates.
