# Durable reader records

Commonplace uses Room as the canonical store for reader writing and locations. This is a repository foundation, not a user-facing notebook or editor. Importing books and connecting reader engines belong to later tasks.

## Versions and storage

The Room schema is **1**. Its exported definition is checked in at `app/schemas/im.commonplace.reader.data.CommonplaceDatabase/1.json`; both PDF comparison flavors use the same schema. The application container lazily creates the app-private `commonplace.db`. It does not enable main-thread queries or destructive migration fallback. There is no earlier schema to migrate; the first schema change must add and test a real migration from v1.

The location envelope version is independently **1**. Room stores it as text containing `version`, `format`, `engineId` and `payloadJson`. `payloadJson` is itself a string so native JSON, whitespace, Unicode and unknown fields survive exactly. Reader-engine and Android lifecycle objects are never database fields. Export/backup formats have not been implemented and do not share this schema version.

## What each table owns

| Table | Identity and contents | Relationships |
| --- | --- | --- |
| `sources` | UUID, title, optional authors, EPUB/PDF format, original URI, SHA-256 checksum, relative app-owned asset path, availability, UTC epoch-millisecond timestamps | Unique checksum index prevents duplicate document versions. Source creation/import is not yet exposed by the repository. |
| `entries` | UUID, HIGHLIGHT/ANNOTATION kind, optional source/location/title/body/quotation, attribution title/authors, optional signed 32-bit ARGB color, creation/modification timestamps | Indexed nullable source FK uses `ON DELETE SET NULL`. Removing a source row preserves writing and its attribution snapshot. No removal UI is implemented. |
| `bookmarks` | UUID, source, location, creation timestamp | Indexed source FK cascades on source deletion. Multiple bookmarks never overwrite progress or one another. |
| `reading_state` | Source primary key, location, modification timestamp | One current position per source; source FK cascades on deletion. |
| `reader_drafts` | Source primary key, capture (quotation plus location), optional reflection/color, modification timestamp | One active capture draft per source; source FK cascades on deletion. Drafts are not entries. |

`AVAILABLE` and `MISSING` express source availability without deleting writing. An original URI is provenance, not source identity or a promise of continued access. Document copying, availability reconciliation and source registration are not part of Task 2.

## Saving and recovering writing

`ReaderRepository` exposes source/entry flows and suspend operations for capture, source annotation, bookmarks, progress and drafts. Operations use an injected I/O dispatcher; Room runs observable queries on its own executor. The test constructor also accepts a clock and UUID supplier. Production defaults use UTC epoch milliseconds and random UUIDs.

A highlight requires a nonblank quotation and a usable, matching-format location. A source annotation requires a nonblank body and has no invented passage location or quotation. Quotation, reflection and annotation text are preserved verbatim, including line breaks and Unicode. Both entry types obtain attribution from the durable source row inside their transaction; callers cannot supply a replacement attribution snapshot. A missing source, blank source title, invalid location or incompatible format rejects the write.

Saving a capture inserts its entry and deletes that source's draft in **one Room transaction**. A failed insert or failed draft deletion rolls back the whole operation. The UUID is returned only after the transaction succeeds. An exception, including cancellation, propagates rather than becoming a success acknowledgment or empty result. As with any durable write, cancellation near completion can leave a committed write without an acknowledgment; callers must not infer that every unacknowledged operation did nothing.

A draft save validates its quotation and source/location, then upserts only the draft. Its supplied modification timestamp is retained. Progress upserts only the current position; bookmarks are separate inserts with separate IDs. Reads do not promote drafts into entries or silently reset a location. Draft saving at lifecycle boundaries and progress debounce/flush are responsibilities of the later reader/ViewModel implementation, not claims made by this repository.

## EPUB and PDF locations

An EPUB location keeps the complete Readium-native locator JSON as an opaque object. Task 2 checks the version, engine identity and JSON object shape, not publication-specific relocation: that requires the Readium integration.

A PDF location retains its zero-based physical `pageIndex`, optional genuine Unicode `pageLabel`, four-bound PDF-point `pageBox`, `rotationDegrees`, coordinate-space marker `unrotated-pdf-points` and optional selection `quads` (eight coordinates per quad). Physical display numbers are one-based; printed labels are not inferred from them. Null or absent quads remain missing, rather than fabricated precision. Geometry must be finite and structurally valid; this is not evidence that either PDF engine maps it correctly.

Decoding a stored envelope deliberately does **not** validate its payload or reject an unknown location version. This keeps future or malformed native data recoverable. `validate()` rejects unsupported/invalid values before writes, display helpers or engine use, without changing or deleting their stored payload. A future reader must surface an unresolved/unsupported location rather than silently navigating to a guessed position.

## Verification boundaries

`SavedLocationTest` exercises native-payload round trips and PDF/version validation. `ReaderRepositoryTest` uses a named, file-backed Room database, closes/reopens it, and verifies exact Unicode values, IDs and attribution; independent progress/bookmarks; draft recovery; successful commit and triggered rollback; cancellation; unique checksums; and foreign-key retention/cascades. The same repository class runs in host Robolectric SDK 28 tests and Android instrumentation. Host results are not device evidence.

CI's executed-test guard requires the shell case and every named repository contract case in both flavors, rejecting missing, failed, errored or skipped instrumentation. Actual run outcomes are recorded in development notes, not inferred from the existence of a workflow. These tests establish durable records, not reader relocation, process-death UI restoration, import safety, exports, backups or a complete editor.
