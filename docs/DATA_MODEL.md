# Durable reader records

Room is Commonplace's canonical store for writing and saved locations. The first milestone implements bounded imports and an EPUB reader/capture proof over these records. Both PDF comparisons remain experimental and rejected: **NO SELECTION**. Shared storage tests do not establish native PDF highlights. See the [final proof decision](READER_PROOF.md#final-milestone-decision--task-7), [tests](TESTING.md) and [approved architecture](ARCHITECTURE.md).

## Versions and storage

Room schema **1** is exported at `app/schemas/im.commonplace.reader.data.CommonplaceDatabase/1.json`; both flavors use it. The application container lazily opens app-private `commonplace.db`, without main-thread queries or destructive migration fallback. No earlier schema exists to migrate; the first schema change needs a real v1 migration and tests.

The independently versioned location envelope is **1**, stored as text with `version`, `format`, `engineId` and `payloadJson`. The latter is itself a string, retaining native JSON whitespace, Unicode and unknown fields exactly. Reader-engine/lifecycle objects are never database fields. Export/backup formats are not implemented. DataStore holds one EPUB reading-color preference, not writing.

## Tables

| Table | Identity and contents | Relationships |
| --- | --- | --- |
| `sources` | UUID, title/optional authors, format, original URI, SHA-256, relative app-owned asset path, availability, UTC timestamps | Unique checksum index prevents duplicate versions. The importer registers only complete copied assets. |
| `entries` | UUID, HIGHLIGHT/ANNOTATION, optional source/location/title/body/quotation, attribution snapshot, optional signed 32-bit ARGB color, timestamps | Nullable indexed source FK uses `ON DELETE SET NULL`, preserving writing/attribution if a source row is removed. No removal UI exists. |
| `bookmarks` | UUID, source, location, creation time | Source FK cascades; separate IDs, never reused as progress. |
| `reading_state` | Source primary key, location, modification time | One progress record per source; source FK cascades. |
| `reader_drafts` | Source primary key, capture (quotation/location), optional reflection/color, modification time | One capture draft per source; source FK cascades. Drafts are not entries. |

`AVAILABLE` and `MISSING` express source availability without deleting writing. The original URI is provenance, not identity or continued-access assurance. App-private copying, staging cleanup and missing-asset reconciliation are implemented by Task 3; the historical Task 2 repository proof did not include them. Exact duplicates offer the existing source. Before native opening, canonical path/checksum verification rejects absent/replaced bytes and prevents stale coordinate application. There is no book removal/reattachment UI or multi-process import promise.

## Saving and recovery

`ReaderRepository` supplies observable source/entry flows and suspend operations for captures, annotations, bookmarks, progress and drafts. It uses an injected I/O dispatcher; Room schedules observable queries. Production clocks use UTC epoch milliseconds and UUIDs; tests inject them.

A highlight needs a nonblank quotation and matching-format usable location. A source annotation needs a nonblank body with no invented passage or quotation. Quotation/reflection/body retain exact Unicode and line breaks. Attribution comes from the durable source within the transaction, never a caller-provided replacement. Missing sources, blank source titles, invalid locations and incompatible formats reject writes.

Saving a capture inserts the entry and clears its draft in **one Room transaction**. Failure rolls both back. Success is acknowledged only after the operation completes; exceptions/cancellation propagate. Cancellation near completion may leave a committed write without acknowledgment, so unacknowledged does not mean nothing happened.

Draft saves upsert only the draft; progress upserts only reading state; bookmarks are independent inserts. ReaderViewModel now owns the tested 300-ms draft debounce, 500-ms progress coalescing and boundary flushes. The EPUB proof verifies acknowledged pending drafts, full native locators, committed quotation/reflection, bookmarks, progress and restored decorations across actual process death and explicit source reopening. Unacknowledged last keystrokes and automatic last-book launch are not promised.

## Engine-specific locations

**EPUB:** `readium` identity and the complete Readium locator JSON are preserved. Flavor-specific PDF identities are `androidx-pdf` and `nucleus-composepdf`; adapters/ViewModel reject foreign candidates and incompatible formats/versions. Task 4 proves real EPUB selection/relocation/recovery, beyond the historical Task 2 codec-only round-trips.

**PDF:** the shared valid-write contract requires zero-based physical `pageIndex`, four-bound PDF-point `pageBox`, `rotationDegrees` and `coordinateSpace = "unrotated-pdf-points"`, with optional genuine Unicode `pageLabel` and optional eight-coordinate selection quads. Physical display numbers are one-based; printed labels and missing quads are not fabricated. Bounds/numbers must be finite and structurally valid.

Both flavor-local codecs call `SavedLocation.validate()` on encoding and decoding: page-box, rotation and coordinate-space fields are mandatory; quads, printed labels and opaque native JSON may be absent. The codecs do not establish that supplied geometry is engine-observed or accurate. Neither PDF host emits precise captures/progress/bookmarks or applies restored highlights because original crop/rotation mapping is unverified. Temporary PDF page/zoom controls are not durable locations. Tests seeding synthetic PDF records prove retention/refusal, not native capture. ComposePdf's actual restart probe proves only source-annotation durability, with no passage location.

Envelope decoding preserves unknown/future/malformed native payloads for recovery; validation rejects unsupported values before new writes or use without deleting stored writing. Unknown locations must remain unresolved rather than guessed.

## Verified and future boundaries

`SavedLocationTest` covers opaque round-trips and validation. `ReaderRepositoryTest` uses a named file-backed Room database on host Robolectric API 28 and Android instrumentation: exact text/IDs/attribution, close/reopen, independent records, draft recovery, atomic commit/triggered rollback, cancellation, unique checksums and FK behavior. Task 4 adds actual native EPUB recovery; Task 6 adds actual PDF source-annotation recovery only. Host tests are not device evidence; guarded no-op probes are not kill/relaunch tests.

Only HIGHLIGHT and ANNOTATION are implemented entry kinds. Thoughts/source notes, tags/collections/relationships, collection search/indexing, complete editors, Markdown/export mappings, backup/restore and schema migration beyond v1 need later plans. See [DEVELOPMENT](DEVELOPMENT.md), [TESTING](TESTING.md) and the [seven-task milestone](superpowers/plans/2026-10-20-foundation-reader-proof.md) for exact evidence and limits.
