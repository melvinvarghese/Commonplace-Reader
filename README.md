# Commonplace

An offline-first Android reader and personal commonplace book, currently in development.

## Project status

The app includes a local library, an EPUB reading proof, and a notebook for thoughts, highlights, annotations, and source notes. EPUB support is limited to the documented test fixtures and formats. PDF support remains experimental; no PDF engine has been selected for a supported release. There is no public release APK.

## Build

The project uses Kotlin, Jetpack Compose, Room, and Gradle. See [Development](docs/DEVELOPMENT.md) for the required toolchain and build commands.

## Privacy

The app is designed for offline use and requests no Android internet permission. It excludes private app data from Android cloud backup and device transfer. See [Testing](docs/TESTING.md) for the scope and limits of the security checks.

## License

Licensed under [Apache-2.0](LICENSE). Third-party dependencies retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
