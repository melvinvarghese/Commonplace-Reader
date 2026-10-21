# Third-Party Notices

This file records the license materials identified for the current Commonplace Android foundation and reader-engine proof. It is an inventory of the declared dependencies and inspected artifacts, **not a representation that every transitive dependency has received complete legal clearance**. Dependency versions and graphs can change; repeat the audit before distributing a build.

## Project license

Commonplace's project license is Apache License 2.0. The unmodified standard text is in [`LICENSE`](LICENSE), obtained from the Apache Software Foundation's canonical text at <https://www.apache.org/licenses/LICENSE-2.0.txt>.

The Git repository's initial commit contained only `README.md` and `LICENSE`; it contained no application implementation or copied assets. The original GPL-3.0 text was the only GPL-marked material found in the initial tree. The history inspected contains no independently attributed GPL source. The project license was changed with owner authorization; history was not rewritten.

## Declared dependency inventory

Versions and coordinates below are the current Gradle version catalog/build declarations, with resolved runtime dependencies checked using Gradle's `androidxDebugRuntimeClasspath` and `composepdfDebugRuntimeClasspath` reports.

| Component | Declared coordinate/version | License identified | Scope / accompanying material |
| --- | --- | --- | --- |
| Readium Kotlin Toolkit | `org.readium.kotlin-toolkit:readium-navigator:3.4.0`, `readium-streamer:3.4.0` and resolved `readium-shared:3.4.0` | BSD 3-Clause | [`third_party/licenses/readium/BSD-3-Clause.txt`](third_party/licenses/readium/BSD-3-Clause.txt); retain BSD notice in source and binary distributions. Task 4 enables the pinned EPUB parser/streamer, not a different navigator. |
| AndroidX PDF viewer | `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01` | Apache-2.0 | License text copied from the artifact's embedded `META-INF/androidx/pdf/pdf-viewer-fragment/LICENSE.txt` to [`third_party/licenses/androidx/Apache-2.0.txt`](third_party/licenses/androidx/Apache-2.0.txt). |
| Compose PDF reader / PDFium wrapper | `dev.nucleusframework:pdfium:158.0.8111.0`, selecting Android artifact `dev.nucleusframework:pdfium-android:158.0.8111.0` | MIT per the Maven Central POM | The POM identifies <https://github.com/kdroidFilter/pdf> as its source repository. The wrapper MIT text for the architecture's `NucleusFramework/ComposePdfReader` candidate is preserved at [`third_party/licenses/compose-pdf-reader/MIT.txt`](third_party/licenses/compose-pdf-reader/MIT.txt). The separately supplied native package's MIT notice is [`third_party/licenses/pdfium/Native-Package-MIT.txt`](third_party/licenses/pdfium/Native-Package-MIT.txt). |
| PDFium native engine and bundled native dependencies | Included in the PDFium Android AAR and matching arm64 package described below | Multiple upstream notices/licenses | All 21 files from the supplied archive's `licenses/` directory are copied verbatim under [`third_party/licenses/pdfium/`](third_party/licenses/pdfium/), including PDFium, Abseil, FreeType, HarfBuzz, ICU, libpng, zlib, and other component notices. |
| AndroidX / Compose / AndroidX Test artifacts | Versions pinned in `gradle/libs.versions.toml` and `app/build.gradle.kts`, including Compose BOM `2026.09.00`, DataStore Preferences `1.2.1` and Fragment KTX `1.9.0` | Apache-2.0 as stated in the inspected artifacts' embedded license metadata | Includes the current Compose, AndroidX Core, Activity, Lifecycle, test runner, Espresso, and related AndroidX family. Task 4 uses DataStore for one reading-color preference and Fragment for the supported navigator. AndroidX artifacts embed their own `META-INF/.../LICENSE.txt`; the AndroidX PDF license is separately retained above. |
| Kotlin, Kotlin Coroutines, Kotlin Serialization and related JetBrains artifacts | Versions resolved by Gradle (Kotlin `2.4.20`; Coroutines `1.11.0`; Serialization `1.11.0`) | Apache-2.0 per upstream licensing metadata | Dependency families resolved in the current Gradle graphs. |
| Timber | `com.jakewharton.timber:timber:5.0.1` (Readium runtime graph) | Apache-2.0 | Transitive dependency observed in the Readium runtime graph. |
| jsoup | `org.jsoup:jsoup:1.23.2` | MIT | Already in the Readium runtime graph; Task 4 declares the same pinned version directly to parse publication XHTML at the content-policy boundary. |
| JUnit / AndroidX instrumentation test libraries | JUnit `4.13.2`; AndroidX Test `1.3.0` / runner `1.7.0`; Espresso `3.7.0` | JUnit 4: EPL-1.0; AndroidX Test and Espresso: Apache-2.0 | Test-only dependencies; not intended as application runtime libraries. |
| Gradle wrapper distribution | Gradle `9.7.0` | Apache-2.0, with bundled distribution attributions | Wrapper distribution `LICENSE` and `NOTICE` are retained at [`third_party/licenses/gradle/`](third_party/licenses/gradle/). Its configured distribution SHA-256 is `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae`. |

The published POM contains a stale SCM/source URL (`kdroidFilter/pdf`, currently 404). This was reconciled against the approved candidate's release tag [`v158.0.8111.0`, commit `590acd9355d74c1094e892c82e0597eed47fb9a4`](https://github.com/NucleusFramework/ComposePdfReader/tree/590acd9355d74c1094e892c82e0597eed47fb9a4): its `pdfium/build.gradle.kts` declares these exact coordinates and the same stale POM URL/developer values, and its LICENSE supplies the retained MIT attribution. No replacement candidate was introduced. This corroborates declared release provenance; it is not a reproducible-build or publisher-signature verification.

The Gradle runtime graphs also contain additional transitive AndroidX, Kotlin, Google, and third-party modules. The list above is intentionally a focused licensing inventory rather than a claim that every resolved component's license and all notices have been individually reconciled. The build cache contains artifact-embedded license files, but this task did not audit every artifact, plugin, SDK component, source JAR, or redistribution condition.

## PDFium native artifact record

The current Compose-PDF dependency resolves from Maven Central. The coordinates are `dev.nucleusframework:pdfium-android:158.0.8111.0`; its POM declares MIT and identifies <https://github.com/kdroidFilter/pdf> as the source repository. The Maven Central POM is <https://repo.maven.apache.org/maven2/dev/nucleusframework/pdfium-android/158.0.8111.0/pdfium-android-158.0.8111.0.pom>, and the AAR is <https://repo.maven.apache.org/maven2/dev/nucleusframework/pdfium-android/158.0.8111.0/pdfium-android-158.0.8111.0.aar>. The remotely fetched AAR was byte-identical to both the supplied AAR and Gradle-cached artifact. The audited TGZ is byte-identical by SHA-256 to the [documented upstream release asset](https://github.com/bblanchon/pdfium-binaries/releases/download/chromium%2F8111/pdfium-android-arm64.tgz), fetched separately for comparison. The release-tagged wrapper build script selects this bblanchon release and Android triplet.

| Inspected material | Local path / coordinate | SHA-256 | Observation |
| --- | --- | --- | --- |
| Android AAR | `/tmp/pdfium-android-158.0.8111.0.aar`; resolved as `dev.nucleusframework:pdfium-android:158.0.8111.0` | `21cbf3a27e77bdc8b520ec68dc33a4f1c770d58b01d9cecdfaa9d065958b668e` | Same hash as the Gradle-cached artifact. The AAR itself has no embedded license/NOTICE files. |
| Arm64 PDFium package | `/tmp/pdfium-android-arm64-chromium-8111.tgz` | `727cb2645ece75d28d0b72f2ec05b82858ca3bfb6712cd333ffc248807016e2c` | Archive metadata identifies PDFium `158.0.8111.0`, target `arm64` / `android`; includes MIT `LICENSE` and component notices in `licenses/`. A separate fetch of the upstream release URL produced the same SHA-256; no publisher signature was verified. |
| Arm64 engine binary inside AAR | `jni/arm64-v8a/libpdfium.so` | `6cb23729fd58916729bfb7cebe9d7e44b8090b6c242be67261023865dc21f8e2` | Byte-identical to `lib/libpdfium.so` in the arm64 TGZ, confirming binary identity for this slice. |

The AAR contains both `libpdfium.so` and `libpdfiumjni.so` for **arm64-v8a, armeabi-v7a, x86, and x86_64**. The supplied TGZ is arm64-only and contains `libpdfium.so`; its arm64 binary matches the AAR as noted above. The AAR package manifest declares minSdk 24; this project currently configures minSdk 28.

ELF `PT_LOAD` alignment was checked with `readelf` for all eight shared libraries in the AAR. All `libpdfium.so` and `libpdfiumjni.so` objects in arm64-v8a and x86_64, and the `libpdfiumjni.so` objects in armeabi-v7a and x86, have `p_align=0x4000` on each load segment. However, armeabi-v7a `libpdfium.so` and x86 `libpdfium.so` have `p_align=0x1000` on each load segment; those two engine binaries do not meet a 16-KiB ELF-segment-alignment check. The arm64 TGZ's `libpdfium.so` is byte-identical to the AAR arm64 binary and therefore has the same measured `0x4000` load-segment alignment.

The audit compared artifact hashes, archive version/target metadata, ABI directory presence, arm64 binary identity, and ELF load-segment alignment. It did not verify a publisher signature for the TGZ, reproduce the native build, verify every binary against a signed upstream release, evaluate the full transitive license chain for every ABI, or test APK ZIP alignment, installation, or runtime compatibility on 16-KiB devices. No broader compatibility or complete-clearance claim is made here.

## Notices and redistribution

When redistributing third-party binaries, retain the applicable copyright/license notices above and the full accompanying materials in `third_party/licenses/`. In particular, preserve the Readium BSD-3-Clause terms, MIT notices for the PDF wrapper/native package, and **all** PDFium component license files copied from the supplied native package. The project's Apache-2.0 license does not replace or relicense those third-party components.

## Synthetic test fixtures

Task 3's original document text, generated scan graphics and EPUB packages are Apache-2.0 project material. The committed fixture generator uses ReportLab 5.0.1 (BSD), Pillow 12.3.0 (MIT-CMU) and pypdf 6.18.1 (BSD-3-Clause); these Python tools are not Android runtime dependencies. The bundled `tools/fixtures/assets/DejaVuSans.ttf` retains the full Bitstream Vera/DejaVu redistribution notice at [`tools/fixtures/assets/DejaVu-LICENSE.txt`](tools/fixtures/assets/DejaVu-LICENSE.txt). Its hash and generation recipes are in the [fixture manifest](app/src/androidTest/assets/fixtures/manifest.json). No third-party books or external scan images are included. Reference geometry is authored test data, not evidence of a candidate engine's selection accuracy.
