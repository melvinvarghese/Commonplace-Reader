# Commonplace notebook: writing and editing

**Status: written specification approved by the owner on 2026-10-22.** The owner approved the full writing-and-editing notebook specification and requested the implementation plan. This document makes its behavior precise; it does not authorize implementation. The separate implementation plan still requires review and execution-method selection.

## What this adds

Commonplace becomes a place to write as well as keep passages. A new Commonplace tab gathers finished entries from every book alongside independent thoughts. People can read an entry, edit their own writing, recover a draft after restarting, and return to a supported EPUB passage without first finding the book in Library.

Reliability comes first: an unavailable book must not hide its writing, a failed save must not clear input, and retrying must not create another copy. The existing warm-paper and forest-green theme, dark mode, offline behavior, one-module architecture and Android API 28 baseline remain.

This is a notebook milestone, not the complete beta. Tags, collections, collection-wide search, Markdown export, backup/restore, entry deletion, entry-type conversion, source removal/reattachment, sync and release publication are outside it. Deferred beta requirements remain requirements. Search in a book remains separate from collection search; do not add an empty Search tab.

**PDF remains NO SELECTION.** Neither experimental candidate is accepted. No engine, new PDF capture, guessed geometry, baseline increase or weakened PDF acceptance gate is authorized. PDF-associated writing remains readable and editable under the rules below.

## The notebook experience

Navigation becomes Library, Commonplace, and Settings & data. Returning from a reader or changing tabs restores the current notebook list/detail context where practical; durable writing never depends on navigation state.

The notebook lists finished entries newest-created first, with stable UUID ordering for ties. Editing an old entry does not unexpectedly move it to the top. Each card shows its type, optional title, a short quotation/body preview and source attribution when present. Type filters are All, Thoughts, Highlights, Annotations and Source notes; they are simple database filters, not text indexing or collection search. Loading, empty, filtered-empty, load-error and retry states are distinct. Lists are lazy and queries run off the main thread.

Selecting a card opens its detail: full quotation, reflection/body, source snapshot, created/modified dates, Edit and an applicable source/passage action. Keep quotation and reflection visually separate. Truncation is only for list previews, never persisted text.

New entry offers Thought, Annotation or Source note. Highlights originate only in real reader selection. Annotation and Source note creation includes a book picker using existing source records, including records marked missing; the person can write about a known book without readable source bytes. No manual source catalog or file import redesign is included.

An entry with a recoverable edit offers Resume draft, view the saved entry, or Discard draft. A separate Drafts section exposes unfinished new entries, including after process death. Drafts never appear as finished entries or inflate finished-entry counts.

## Four entry types, with explicit meanings

| Type | Meaning and creation | Editable fields |
| --- | --- | --- |
| Thought | Independent writing, created in Commonplace; no source, quotation or location | Optional title, required body |
| Highlight | A reader-captured quotation with its original attribution and precise supported location | Optional title, optional reflection (`body`), optional highlight color |
| Annotation | A discrete observation about an existing book; retains the current source-level meaning and need not identify a passage | Optional title, required body |
| Source note | A whole-book summary, synthesis or reading note about an existing book; multiple notes per source are allowed | Optional title, required body |

Annotation versus Source note expresses the writer's intention, not a length threshold or automatic conversion. Existing ANNOTATION rows are never renamed or reclassified. Neither type gains an invented quotation or location.

Reject blank required bodies, but preserve accepted text exactly: do not trim stored writing, normalize Unicode, flatten line breaks or replace null with empty strings during migration. Title is optional; blank titles display a type-specific fallback without persisting a generated title. A highlight may have no reflection because the quotation is its content.

The captured quotation, source identity, attribution snapshot, entry kind and location are not editable in this milestone. An editor cannot rewrite capture evidence or silently attach an old highlight to different bytes. Existing entries with null source references remain editable; keep their source snapshot. New source-attached entries require an existing source row and take attribution from it inside the save transaction. Thoughts have no source attribution, represented as null rather than a fabricated book name.

Existing legacy or unsupported records must not be deleted, normalized or silently made valid. If a record cannot satisfy the safe edit contract, show its readable fields with an explanation rather than overwriting it. Source unavailability alone is not a reason to prohibit editing writing.

## Safe saving and recovery

The editor has explicit Save. Autosave persists a separate local draft; it does not publish a finished entry or mutate the committed version in the background. Labels distinguish Not saved yet, Saving draft, Draft saved on this device, Saving entry, Saved and Save failed. Never show Saved before the corresponding durable write completes.

Use a 300-ms draft debounce, matching the existing capture approach. Request a flush on navigation and backgrounding; keep in-memory text on failure and offer retry. Navigation away while input is not durably acknowledged offers Retry/stay or explicitly Discard unacknowledged changes rather than silently claiming recovery. Android may kill a process before a boundary flush finishes: only acknowledged drafts are promised recoverable, not every last keystroke.

Each new entry receives a stable target UUID before its first durable draft. An existing-entry draft uses that entry UUID. Only one active notebook draft per target is permitted; different targets have independent drafts. Opening an existing draft asks to resume or discard it rather than replacing it. Reader capture drafts remain separate and retain their current source-scoped behavior.

Explicit Save first freezes the submitted editor snapshot, validates it, then atomically inserts/updates the finished entry and deletes its draft. Disable submission and field editing during this bounded commit; after completion the person can edit again. A failure rolls back both operations. Preserve UUID and createdAt on update; change modifiedAt and the entry revision only on a successful committed edit. Blank required content can be kept as a draft but cannot be committed.

### No duplicate retries or silent overwrites

An integer entry revision is the optimistic concurrency token; timestamps alone are insufficient. Existing-entry commits compare the revision originally opened with the current database revision. A mismatch leaves both the newer saved entry and the local draft intact. Show the current saved version separately and let the person keep the draft or explicitly reload/discard it; do not auto-merge or silently overwrite.

Give each submitted draft snapshot a stable commit token. Store the last notebook commit token on its target entry. A retry of that same successful commit returns the existing result without reapplying it. If a newer commit has since replaced that token, report a changed-entry conflict and refresh rather than replaying stale text. New-entry retries use the same target UUID and never insert a second entry. After restart, a removed draft plus its existing target resolves to the committed entry, not a new blank editor.

Draft writes also require a persisted draft revision/session ownership check. Creating a draft is an explicit operation, not an unconditional upsert from every autosave. Once a draft is saved or discarded, late writes from its former editor cannot recreate it. Serialize writes per active editor, cancel pending debounce work before Save/Discard, and enforce the guard in storage rather than trusting cancellation alone. Discard deletes only that draft, never the finished entry. Missing targets or stale sessions yield explicit conflict/error results, not resurrection.

## Return to the right EPUB passage

Open passage is offered for supported EPUB highlight records. Resolve the entry by stable ID; do not accept arbitrary serialized locator JSON from an external Intent. ReaderActivity is currently source-only, so add an internal entry-target handoff that carries entry ID and resolves its source and saved locator through the repository.

Before applying a location, check source existence/availability, app-owned canonical path and checksum, location-envelope validity, EPUB format and Readium engine compatibility. Preserve the original locator JSON. A failure keeps quotation, reflection and attribution visible, reports the actual problem, and provides Back to entry or an existing valid Library route. Reattachment remains unavailable; do not present a working-looking repair button.

For a valid request, the saved entry location takes precedence over the source's last progress. Apply it only when the native navigator is ready. A new explicit entry request must work even if that source is already active; the current same-source early-return must not swallow it. Source-switch generation guards reject late events from old navigators. Restoration of the same reader instance must not repeatedly replay the jump after the person has moved on.

The jump itself must not synthesize progress before successful native navigation. Thereafter actual navigator position events use normal progress semantics, including updating last reading position; this milestone does not add a separate browsing-history stack. Returning from the reader restores the notebook entry context instead of always labeling the exit Library. Annotation and Source note may offer Open book when available, using normal progress without pretending to have a passage. No PDF passage navigation is claimed; PDF-associated entries explain the experimental/unresolved state.

## Fit into the existing app

Keep the existing single application module and dependency container. Use Compose screens and ViewModels backed by a focused NotebookRepository/DAO boundary over the same Room database; do not introduce a second canonical store, new framework or new reader engine. ViewModels coordinate editor state and navigation; composables do not query Room or invoke reader engines directly. Reuse existing theme and source verification behavior.

The notebook observes entries written by the reader, and the reader observes notebook edits to title/reflection/color. The reader's highlight predicate remains HIGHLIGHT with compatible location; adding kinds must not turn source notes into decorations. Its source-level saved-items panel may continue displaying the new book-linked writing without offering passage controls for location-free notes. Existing reader draft workflows and native proof behavior remain regression targets, not silently rewritten as part of the notebook.

No in-memory-only finished entry cache, DataStore note bodies, private-writing logs or Internet permission is introduced. Notebook drafts and database records remain covered by existing Android cloud-backup/device-transfer exclusions.

## Database upgrade without losing writing

Advance Room schema 1 to schema 2, preserving the exported v1 schema. Add THOUGHT and SOURCE_NOTE enum values without altering existing persisted kind names. Extend entries with revision (default 0 for existing rows), nullable lastNotebookCommitId and nullable attributionTitle for independent thoughts. Existing attribution strings remain byte-for-byte unchanged. Rebuilding a table to change nullability must preserve all fields, source FK semantics and indexes.

Add a separate notebook_drafts table with target UUID primary key, new/existing mode, fixed kind, optional source ID for new entries, editable title/body/color, base entry revision, draft/session revision and stable commit token for the submitted snapshot. Draft metadata is separate from entry timestamps. Retain sufficient source snapshot/context to show a draft if a source becomes unavailable; saving a new book-linked entry still rechecks its source row. Do not cascade-delete recoverable notebook writing when a source disappears. Existing entry immutables are loaded from the committed entry, not trusted from editor-supplied fields.

For draft display, persist nullable sourceTitleSnapshot and sourceAuthorsSnapshot. On new source-linked draft creation, copy both from the selected source row in the creation transaction; on an existing-entry edit, copy its preserved attribution snapshot. Thoughts use nulls. These snapshots stay unchanged for that draft's lifetime; switching a new draft's selected source, before submission, explicitly refreshes both from the newly selected row in the same guarded draft update. They are display/recovery context only: final attribution for a new entry still comes from its current source row inside the commit transaction. A missing source row blocks that new source-linked commit without discarding its draft.

Keep source IDs in notebook drafts as recoverable context rather than a cascading FK. An edit target disappearing causes a conflict, not automatic conversion to a new entry. No deletion UI is added, but storage guards must handle absent records safely. Fresh v2 and migrated v2 databases must have identical schema definitions. Register the explicit migration in AppContainer; no destructive upgrade or downgrade fallback.

Tests must verify actual content, not only schema validity. The [official Room migration guide](https://developer.android.com/training/data-storage/room/migrating-db-versions) distinguishes schema validation from checking that data survived. Use APIs compatible with this repository's pinned Room version; this design does not authorize copying newer Room 3 dependencies or upgrading the toolchain.

## What must pass before calling it implemented

1. **Migration preservation:** seed a real exported-v1 database with both existing kinds, Unicode/multiline/null fields, opaque/native/unsupported locators, missing-source states, bookmarks, progress and reader drafts. Migrate, reopen and compare every existing value, identity, timestamp, index and FK behavior. Verify both build flavors and fresh-v2 parity. Inject migration failure to confirm no destructive recovery path is used.
2. **Entry rules:** test each kind's required fields, immutable capture evidence, attribution derivation, editing orphaned/missing-source writing, stable creation timestamps, revision checks and exact text round trips. Reader-created entries appear without restarting the notebook.
3. **Save integrity:** test atomic save/draft-clear rollback, committed-but-unacknowledged retry, repeated taps, new-entry UUID reuse, stale edits, reordered autosaves, late writes after discard/commit and conflict retention. No duplicate or overwritten newer entry is an acceptable retry result.
4. **Real restart recovery:** exercise acknowledged new/edit drafts and finished entries through actual app-process termination and relaunch, with exact source/entry identity and text assertions. Test multiple independent drafts and distinguish host simulation from device recovery evidence.
5. **Navigation:** open a specific EPUB highlight instead of last progress; handle same-source requests, lifecycle recreation, source switching, missing/replaced bytes and unsupported locators. Verify return-to-entry and normal post-navigation progress behavior. Retain existing PDF failures as documented failures, not skipped acceptance gates.
6. **Usability and accessibility:** test empty/error/retry/filter states, Save versus draft labels, keyboard/insets, rotation, dark theme, 200% system text and at least 48-dp actions. Check TalkBack names, focus order and spoken save/conflict status on an actual available device/emulator; report manual checks as not run if unavailable. Do not substitute automated semantics checks for a spoken audit.
7. **Repository checks:** run both flavor unit suites, strict dependency verification, lint and app/test-APK builds, the relevant instrumented API 28/current matrix, and existing Python/fixture regressions. Report exact commands, source commit and PASS/FAIL/NOT RUN; keep known PDF failures separate from notebook results. Update README/data-model/testing docs only to describe proven behavior.

## Review and next step

The owner approved this written specification and requested the [task-by-task implementation plan](../plans/2026-10-22-commonplace-notebook.md). Implementation starts only after that plan is reviewed and an execution method is selected. Ordinary incremental verified pushes may continue under the existing authorization; never force-push or overwrite unrelated remote changes.
