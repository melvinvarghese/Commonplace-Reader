# Commonplace: approved architecture and milestone status

**A home for the ideas you keep.**

**Current status: notebook writing/recovery is implemented and verified; native passage switching blocks milestone acceptance. PDF remains NO SELECTION.** The owner approved the notebook specification, seven-task plan, native execution and normal incremental pushes to `main`. Readium EPUB has a bounded historical passing proof; current-source API 28 saved-capture reopening also fails. Both PDF candidates were evaluated and rejected for milestone acceptance. See [notebook evidence](NOTEBOOK_PROOF.md) rather than reusing historical passes. The app remains a debug development trial, not a beta. No Android baseline increase or third engine is authorized.

The design below retains the intended beta requirements. Statements in the historical review section describe the original checkpoint, not today’s implementation. The [approved plan](superpowers/plans/2026-10-20-foundation-reader-proof.md), [final reader decision](READER_PROOF.md#final-milestone-decision--task-7), [data model](DATA_MODEL.md), [development](DEVELOPMENT.md) and [testing](TESTING.md) separate implemented features from unrun/rejected/planned work. The original proposed-design wording was: “Status: proposed design, awaiting the owner's review”; it did not authorize implementation at that earlier checkpoint.

Commonplace will be a dependable offline Android reader and personal commonplace book. A reader can import a book, keep a passage, add a reflection, find it later, and export their writing without needing Obsidian or an account. Reliability and ownership of notes matter more than the number of features.

## What the first beta includes

The first beta is intended to combine PDF and reflowable EPUB reading with four saved entry types: thoughts, highlights, annotations, and source notes. Reading progress, bookmarks, tags, collections, search, Markdown export, and validated backup/restore are beta requirements; at the original design checkpoint none was implemented or tested. Current work adds the notebook's writing/editing and independent drafts to bounded imports, EPUB capture/annotations, progress and bookmarks, not the full beta. A highlight stores the selected quotation and any reflection separately, and remains readable when its book is missing.

PDF text search, selection, and precise highlights are requirements for supported text documents, not claims about every PDF. Scanned documents may only render; no OCR is included. Password-protected PDFs, fixed-layout EPUBs, and malformed publications need explicit supported/unsupported results rather than blanket compatibility claims. DRM support and circumvention are outside this beta.

Direct Obsidian-folder integration, bidirectional sync, web capture, rediscovery, and a rich idea-connections interface come later. Portable Markdown is a beta requirement, not dependent on those later features. Settings must label unavailable capabilities honestly instead of exposing nonfunctional buttons.

## One app, with clear internal boundaries

Use Kotlin, Jetpack Compose, Material 3, ViewModels, coroutines/Flow, Room/SQLite, DataStore, and Android's Storage Access Framework (SAF). Begin with one application module, organized by responsibility, not a collection of framework modules:

| Boundary | Responsibility |
| --- | --- |
| Screens and ViewModels | Display state, accept actions, and report saving/errors. No direct database or reader-engine calls from screen composables. |
| Application operations | Import sources, save entries, navigate to passages, search, export, and restore. Coordinate transactions and validation. |
| Local data | Room repositories own structured records; DataStore holds preferences, not notes. Files hold document binaries and covers. |
| Readers | Separate EPUB and PDF integrations translate engine events into saved locations, selections, and progress. |
| Portability | Markdown serialization, export manifests, backup validation, and SAF output. Independent of reader widgets. |

The normal flow is **screen → ViewModel → operation/repository → Room or file storage → Flow → screen**. Add small operation classes where coordination warrants them; do not wrap every database call in an empty abstraction. A small application-owned dependency container is sufficient initially.

Reader widgets may use Android View/Fragment interoperability within the Compose app. “One native Compose app” does not mean rewriting a library's navigator. Engine objects and Android lifecycle objects never become database fields. No plugin framework, server, AI API, or cloud infrastructure is required.

## Implemented notebook boundaries

The [approved notebook specification](superpowers/specs/2026-10-22-commonplace-notebook-design.md) separates finished entries from `notebook_drafts`. One Activity-scoped `NotebookViewModel` serializes editor actions, debounces draft writes and flushes at leave/background boundaries. Room acknowledgment controls status; input not yet acknowledged is not promised durable after termination. The 300-ms debounce is not a durability deadline.

`NotebookRepository` owns transactional session/draft-revision checks, entry-revision conflicts, stable commit tokens, atomic entry-save/draft-delete, and guarded discard. Title/body/color are the editable fields; highlight quotation, full location envelope, attribution, identity and creation time are not supplied by the editor. New source-linked writing snapshots the explicitly selected source, while existing orphaned entries retain their writing and remain editable. Thoughts have no invented attribution. An annotation is a distinct kind from a source note; neither claims a passage.

The notebook list has type filters, separate drafts, full-entry detail and an editor with explicit Save, failure/conflict and resume/discard states. There is no finished-entry deletion, tagging, collection indexing, export or sync in this increment. The existing paper/forest/dark theme and 48-dp minimum controls are retained; automated layout coverage is not spoken TalkBack or physical-device evidence.

Notebook-to-reader requests contain only an internal stable entry ID. `EntryReaderTargetResolver` re-reads the record and source, applies canonical-path/SHA-256 verification and rejects PDF/unknown/mismatched locations. Source-level notes offer Open book, not an invented passage. A token gates each EPUB jump on rendered/observed native readiness. Completion additionally checks the engine-resolved exact quotation range and an observed native position, rather than consuming the request on Boolean command acceptance. Native-session guards reject old hosts; only real navigator positions update progress. Recreation and initial-entry cases pass, but the explicit same-source replacement test still fails in all four native cells, so this handoff is not accepted as dependable. Failed resolution leaves quotation, reflection and attribution readable with Back to entry; it does not label last progress as successful passage opening.

Room schema 2 has a tested v1 migration without destructive fallback. Migration preserves raw location/text values and existing identities/timestamps. [Data model](DATA_MODEL.md) and [notebook proof](NOTEBOOK_PROOF.md) document exact constraints and verification limits. PDF's required selection, original-space geometry, durable highlights and recovery gate are unchanged.

## Readers: one choice made, one evidence gate

**EPUB: Readium Kotlin.** Its [official feature table](https://readium.org/kotlin-toolkit/) lists EPUB 2/3, reflowable search, and highlighting through its Decoration API. Use its supported navigator and preserve complete native locator JSON; map persisted highlights back to decorations when reopening. Commonplace, not Readium's transient UI state, owns saved notes and progress. The [BSD-3-Clause license](https://raw.githubusercontent.com/readium/kotlin-toolkit/main/LICENSE) requires notices to be retained. Reopening and relocating selections still need integration tests.

**PDF: no final engine selected.** Compare these exact candidates, rather than similarly named libraries:

- [AndroidX PDF](https://developer.android.com/jetpack/androidx/releases/pdf): official release notes list `1.0.0-beta01`; its Compose viewer and some editing APIs remain experimental. Notes describe read/render support backported to API 28, but that does not establish identical selection or annotation support on every device. Its Compose artifact also introduces compile-SDK/Android Gradle Plugin constraints.
- [NucleusFramework/ComposePdfReader](https://github.com/NucleusFramework/ComposePdfReader): documents PDFium-based rendering, text extraction, selectable text, and custom overlays. The wrapper has an [MIT license](https://raw.githubusercontent.com/NucleusFramework/ComposePdfReader/master/LICENSE). Its documented byte-array opening path creates a large-file memory question. Native binaries, supported ABIs, Android 16 KB page-size compatibility, update provenance, and bundled third-party notices must be checked separately; the wrapper license alone is not clearance for the complete dependency tree.

Proposed baseline is Android 9/API 28 or newer, subject to the comparison. Do not publish a supported-version promise until the chosen artifacts and features work on the minimum version and a current Android version. Select and pin a compatible released dependency/toolchain set after review; upstream development-branch requirements are not automatically the app's requirements.

The small proof must use the same legally distributable fixtures for both PDF candidates: ordinary text, rotated/cropped pages, multilingual passages, scans, malformed input, and a large document. Required evidence is correct rendering/navigation/zoom; text search and exact selection capture; durable highlight geometry; notes and bookmarks; progress restoration; return-to-passage after process death; bounded memory and responsive UI; lifecycle cleanup; and dependency/license compatibility. Record device/API, artifact version, supported features, failures, and observed resource use.

Choose one PDF engine only if these checks establish a credible supported experience. A rendering-only demo does not pass the highlighting requirement. If neither passes, return with the evidence and a revised recommendation before widening scope or silently dropping features. At the original design milestone the proof had not run. It has now run: both PDF candidates fail the acceptance gate; see the final decision linked above.

Each reader integration exposes only opening/closing, navigation to a saved location, progress updates, capability state, selection capture, and saved-highlight display. Capabilities are per document and device. Missing text is reported as “No selectable text available”; that alone does not prove a PDF is scanned. Do not build a universal renderer abstraction or force PDF and EPUB locations into one page-number model.

## Files, identities, and durable notes

Import through SAF, validate type/content and available space, then copy into app-private storage by default. Original provider URIs are provenance, not stable identity. A source UUID identifies the book; a content checksum identifies the imported document version. Exact duplicates prompt the user to open the existing source instead of silently creating another. Commit the source record only after its staged copy is complete; interrupted imports are recoverable and leave no apparently usable partial source.

Copies cost storage but make offline reading independent of provider access. Original files remain untouched. Removing a book removes its app-owned copy after confirmation, while retaining source metadata and commonplace entries by default. Deleting associated writing is a separate explicit action. Reattaching a different file must not silently reuse old highlight coordinates; compare checksums and mark locations unresolved when identity differs.

Core records are:

- **Source:** UUID, title/authors, format, original reference, checksum, app-owned asset reference, availability, and timestamps.
- **Entry:** UUID, one of the four types, optional title/body/quotation, optional source/location, source attribution snapshot, optional highlight color, and created/modified UTC timestamps. A thought needs no source; a captured highlight needs a nonempty quotation and attribution. An annotation can attach to the source without claiming a precise passage.
- **Location:** versioned format/engine identifier and payload. EPUB retains Readium locator JSON. PDF retains zero-based physical page index, optional genuine printed label, and selection geometry with coordinate-system/page-box/rotation information. Human-facing physical page numbers are one-based. Missing precision stays missing.
- **Organization:** tags and collections with explicit entry join tables; entry-to-entry relationships with a relation type. Removing a tag/collection deletes membership only. Explicit entry deletion removes its joins and relationship edges, not other entries.
- **Reading state:** per-source progress and separate saved bookmarks; never reuse “last position” as the only bookmark.
- **Portability records:** export target, stable ID-to-filename mappings, previous output hashes, operation status, and manifest version. Room schema versions/migrations are distinct from export and locator versions.

Room is the canonical store. Use transactions for multi-record edits and organization changes, foreign keys for valid references, and tested migrations without destructive fallback. Persist editor drafts promptly, including on backgrounding; show “Saved” only after a successful durable write. Drafts recover after restart but do not appear as finished entries or exports until committed. Unacknowledged keystrokes cannot be promised durable after sudden process termination.

Progress writes are bounded/debounced and flushed at navigation/background boundaries. Parsing, copying, rendering work, indexing, and exports stay off the main thread. Failures leave existing entries intact and offer retry or reattachment, not silent deletion.

## A calm library and notebook

The four primary destinations are **Library, Commonplace, Search, and Settings & data**. Reading opens from Library; selecting supported text offers “Keep in Commonplace,” with optional reflection, color, and tags. An entry offers “Open passage”; a missing source or unresolved location leaves the quotation visible with a clear recovery action.

Library supports title/recently added/recently opened sorting and honest progress. Commonplace supports creating and editing all entry types, tags, and collections. Search covers entry titles/bodies/quotations, source titles/authors, and tags, filtered by type, source, tag, and date. Use Room-backed text indexing for text fields plus relational filters; Unicode preservation is mandatory, while tokenizer behavior must be tested for multilingual text rather than assuming English stemming works everywhere. Searching every book's entire contents is not implied by collection search.

Use warm paper, forest-green accents, readable typography, restrained book details, and a proper dark theme. Reader preferences include EPUB font size, margins, and reading colors; PDF font reflow is not promised. Support large system text, TalkBack, meaningful focus order, adequate contrast, and at least 48 dp touch targets. Show useful empty/loading/error states and saved/unsaved status. Tablet layouts adapt without creating a separate product.

## Markdown is portable; export is not sync

Use a versioned UTF-8 Markdown format with valid, safely serialized YAML front matter, stable IDs, timestamps, entry type, tags, source metadata, and locator metadata. Preserve multiline Unicode writing and separate quotation from reflection. Each entry and source note has a readable filename ending in its full stable UUID; sanitize unsafe names and keep existing filename mappings stable when titles change.

Individual, selected-entry, source-with-entries, and full-collection exports use the same serializer. Relative Markdown links are the default; optional wikilinks do not replace IDs. A partial export includes required source metadata and labels relationships to omitted entries instead of creating broken local links. A versioned manifest maps IDs to paths and content hashes and records included sources, export scope, omissions, and failures. Never claim original book files are part of ordinary Markdown export.

SAF folder writes must validate access and read back completed output before reporting success. SAF providers need not support atomic rename: incomplete runs remain marked incomplete until files and manifest are verified. Repeated exports reuse mappings, skip identical output, and compare existing content against the last written hash before replacement. Conflicts or unknown existing files are preserved for user choice; no silent overwrites or deletions. A new destination uses a new export set. Future vault integration adds previews and permission/conflict recovery, not an unearned “live sync” claim.

## Backup and privacy

Backup is a separate versioned archive containing a consistent logical database snapshot, preferences, and all app-owned documents/assets needed for restoration. Include per-file checksums, lengths, schema version, and an inventory of anything excluded. Never imply that original files outside app ownership are included. Warn that a backup contains private writing and book files; encryption is not promised in this beta.

Restore validates versions, checksums, referential integrity, available space, and extraction paths before changing live data. Reject traversal, absolute paths, symlinks, duplicate paths, and excessive archive expansion. Stage and validate a replacement store first; show counts and exclusions, require confirmation to replace existing data, and retain a recoverable pre-restore snapshot until success. Do not attempt conflict-prone merge restore in the first beta. External SAF grants cannot be restored from an archive; folder access must be requested again.

No accounts, default analytics, or internet dependency for core use. Do not log note bodies, quotations, passwords, or private paths. Untrusted EPUB/PDF input requires resource bounds and parser updates. EPUB rendering must not fetch remote resources by default or expose unrestricted native bridges; permitted publication scripting must remain sandboxed. External links require an explicit user action and safe scheme handling. Android cloud backup must not silently upload private books/notes; exclude these stores unless a later, explicit policy is approved.

## What will count as ready

Beta readiness requires an installable build that launches on a supported device/emulator, not just successful compilation. Automated checks must cover persistence/restart, migrations, locator round-trips, search, organizations, safe import failures, Markdown escaping/links/collisions/repeated exports, interrupted writes, and fresh-install backup restoration. Reader/UI checks must demonstrate opening both formats, durable highlights/thoughts/progress, jump-back, permission errors, accessibility, and offline operation. Use synthetic or legally distributable fixtures. Record physical-device testing separately; release signing secrets never enter Git.

GitHub Actions will build and run available automated tests once an Android project exists. Detailed data/schema, Markdown, technical-decision, testing, and development-status documents will be maintained alongside implementation, not created here as empty promises.

## Historical design checkpoint and review decision

The repository baseline is commit `9f9a88bd05fab3a3c1054d07ea897f76fecb2d46` on `main`: a one-line README and GPL-3.0 LICENSE, with no application or build system. Existing history is preserved. The owner selected Apache-2.0; this documentation-only milestone leaves LICENSE unchanged and does not represent the repository as already relicensed. The authorized license replacement and complete third-party notice review must occur before a release; prior history is not rewritten.

The current sandbox has Java 21 runtime but no detected Java compiler, Android SDK, Gradle, Kotlin CLI, or emulator, and roughly 31 GB free. No Android build, reader proof, device test, or APK exists. Toolchain setup and actual compatibility measurements are prerequisites for later engineering work, not evidence already obtained.

**Owner review requested:** approve this written design or request changes to the reading-and-commonplace experience, beta boundary, app-owned import trade-off, or data-safety rules. PDF engine selection and Android compatibility remain subject to proof evidence. Written-spec approval permits drafting an implementation plan only, not implementation. Implementation begins only after that plan is reviewed and its execution method is selected.

## First milestone outcome and owner decision

Tasks 1–6 are closed; Task 7 records a documentation/evidence checkpoint and the required no-selection stop. The Apache-2.0 replacement, buildable shell, safe bounded imports, Room records and Readium proof are implemented. Full successful PDF and release-readiness matrices are not. AndroidX lacks validated original crop/rotation mapping and failed the current-API navigation/zoom case. ComposePdf lacks exact application capture and restored-overlay APIs; its large-file copy, incomplete performance protocol and untested ARM/16-KiB runtime remain blockers.

Keep both comparison flavors/source, minSdk 28 and all original requirements/history. The smallest revised recommendation is a separately approved PDF feasibility plan for precise selection/geometry/restored highlights, with the remaining platform, safety, performance, licensing and accessibility gates retained. This is not authorization to begin it or to omit PDF from the intended beta. Wait for the owner before a next milestone.
