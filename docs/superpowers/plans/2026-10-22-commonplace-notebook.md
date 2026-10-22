# Commonplace Notebook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dependable offline Commonplace notebook for browsing and editing all four entry types, recovering drafts and reopening exact supported EPUB passages.

**Architecture:** Keep one application module and the existing Room database, with a focused notebook repository/DAO, lifecycle-aware ViewModels and Compose list/detail/editor screens. Separate notebook drafts from finished entries and reader capture drafts. Extend the internal reader entry route without replacing reader engines or source-verification logic.

**Tech Stack:** Existing pinned Kotlin, Compose/Material 3, ViewModel, coroutines/Flow, Room 2.8.5, Readium 3.4.0, JUnit/Robolectric, Android instrumentation and Python CI guards. Add only the matching Room testing artifact if needed for migration tests; no toolchain upgrades.

**Spec:** [Approved notebook design](../specs/2026-10-22-commonplace-notebook-design.md). The owner approved the written specification and requested this plan. Read both documents. This plan still requires owner review and execution-method selection before implementation.

## Global Constraints

- “PDF remains NO SELECTION.” No new PDF engine/capture, invented geometry, accepted-reader claim or weakened PDF evidence gate.
- “The existing warm-paper and forest-green theme, dark mode, offline behavior, one-module architecture and Android API 28 baseline remain.”
- “The captured quotation, source identity, attribution snapshot, entry kind and location are not editable in this milestone.” No source reattachment/type conversion/deletion UI.
- “Reject blank required bodies, but preserve accepted text exactly.” Preserve Unicode, line breaks, nulls, stable IDs and createdAt.
- “Autosave persists a separate local draft; it does not publish a finished entry or mutate the committed version in the background.” Debounce is 300 ms; acknowledge only completed durable writes.
- “Only one active notebook draft per target is permitted; different targets have independent drafts.” Notebook drafts do not replace reader_drafts.
- “No destructive upgrade or downgrade fallback.” Room schema advances 1→2; locator envelope stays 1.
- Tags, collections, collection-wide search/indexing, export, backup, sync and release publication stay later requirements. Do not create placeholders for them.
- Keep current dependency pins and strict verification. No main-thread storage, Internet permission, private-writing logs or cloud-backup exposure.
- Commit as `Melvin Varghese <36334526+melvinvarghese@users.noreply.github.com>`. Use ordinary incremental pushes to `main`; never force-push, rewrite history or overwrite unrelated remote work.

## Review Focus

1. Lost acknowledgment or two Save taps: the same submitted snapshot produces one entry/one revision, including after draft deletion (Tasks 2–3).
2. Delayed autosave after Save/Discard, or two editor sessions: late writes cannot resurrect drafts or replace newer writing (Tasks 2–3).
3. Source disappears between draft creation and Save: retain the source snapshot/draft, block only the invalid new source-linked commit, and keep existing orphaned entries editable (Tasks 1–3).
4. Legacy Unicode/null/native-locator values and unsupported locations: preserve exact fields through migration and editing rather than normalizing or dropping rows (Tasks 1–2).
5. Entry target is the same source already open, or Activity recreates after a jump: open the requested passage once, reject late source events and do not reset the person's subsequent navigation (Task 5).

---

## Starting point and work boundaries

Planning baseline is commit `5016048c3396af2bac28f826ce99992979728470`; no notebook implementation exists. The foundation worktree is `/workspace/Commonplace-Reader/.worktrees/commonplace-foundation`. At execution start, verify active environment, clean status, remote main and worktree isolation before reusing it. Read the relevant execution, TDD, minimal-change and verification skills. Never assume a cached build or prior CI run proves new source.

JDK 17 exists locally; no `/dev/kvm` was observed during planning. Check the SDK through DEVELOPMENT at execution time. Use disposable CI emulators for unavailable local runtime checks; do not silently use the owner's laptop. Planning does not run app tests or install dependencies.

All main Kotlin paths below are relative to `app/src/main/java/im/commonplace/reader/`. Shared data tests follow the current convention: files in `app/src/androidTest/java/im/commonplace/reader/data/` also run on the host through the existing Gradle source-set mapping. Do not put Compose/device recovery tests in that shared data directory.

| Files | Responsibility |
| --- | --- |
| `data/EntryEntity.kt`, `data/CommonplaceDatabase.kt`, `data/NotebookDraftEntity.kt`, `data/NotebookDao.kt`, `data/NotebookMigration.kt`, `AppContainer.kt` | Schema 2, explicit migration and notebook persistence composition |
| `notebook/NotebookModels.kt`, `notebook/NotebookStore.kt`, `data/NotebookRepository.kt` | Editor-safe values, test seam, validation, transactional drafts/commits |
| `notebook/NotebookViewModel.kt` | Browse/editor state, debounce, flush, save/retry/conflict coordination |
| `ui/NotebookScreen.kt`, `ui/NotebookDetailScreen.kt`, `ui/NotebookEditorScreen.kt`, `ui/CommonplaceApp.kt` | Functional notebook route and screens, no direct Room access |
| `reader/EntryReaderTarget.kt`, `ReaderActivity.kt`, `reader/ReaderViewModel.kt`, `reader/epub/ReadiumHost.kt`, `ui/ReaderScreen.kt` | Resolve entry targets, exact EPUB jump, lifecycle/return behavior |
| `app/schemas/.../2.json`, Gradle/catalog/verification metadata | Schema export and matching migration-test dependency, only if needed |
| `app/src/androidTest/.../data/Notebook{Migration,Repository}Test.kt`, `app/src/test/.../notebook/NotebookViewModelTest.kt`, `app/src/androidTest/.../ui/NotebookTest.kt`, `app/src/androidTest/.../reader/NotebookPassageTest.kt` | Storage, scheduling, UI and native navigation contracts |
| `app/src/androidTest/.../notebook/NotebookRecoveryProbe.kt`, `tools/run_notebook_recovery.py`, `tools/test_run_notebook_recovery.py`, result guards/CI | Real process-death proof, strict named evidence and independent notebook results |
| `README.md`, `docs/{ARCHITECTURE,DATA_MODEL,TESTING}.md` | Proven capabilities and remaining limitations, updated at the final gate |

Dependencies: Task 1 → Task 2 → Task 3 → Task 4. Task 5 can be developed after Task 2 but integrates with Task 4. Task 6 needs Tasks 4–5. Task 7 reviews all previous tasks. Under native execution, keep that order. Under subagent-driven execution, each task has one implementer and a fresh reviewer; do not parallel-write shared contracts.

## Task 1: Schema 2 and a lossless v1 migration

**Files:** Modify `data/{EntryEntity,CommonplaceDatabase}.kt`, `AppContainer.kt`, `app/build.gradle.kts`; create `data/{NotebookDraftEntity,NotebookDao,NotebookMigration}.kt`, `notebook/NotebookModels.kt`, `app/src/androidTest/java/im/commonplace/reader/data/NotebookMigrationTest.kt`, and exported schema `app/schemas/im.commonplace.reader.data.CommonplaceDatabase/2.json`. Retain `1.json`. Add a Room-testing catalog alias at existing version 2.8.5 and reviewed dependency verification entries if required.

**Produces:**
- `EntryKind { HIGHLIGHT, ANNOTATION, THOUGHT, SOURCE_NOTE }`; entries keep their fields and gain `revision: Long = 0`, `lastNotebookCommitId: String? = null`; `attributionTitle: String?` becomes nullable.
- `NotebookFields(title: String?, body: String?, colorArgb: Int?)` contains only editable fields, never capture evidence.
- `NotebookDraftEntity`: `targetId: String` PK; `isNew: Boolean`; fixed `kind: EntryKind`; nullable `sourceId`, `sourceTitleSnapshot`, `sourceAuthorsSnapshot`; editable fields; `baseEntryRevision: Long?`; `sessionId: String`; `draftRevision: Long`; `commitToken: String`; `modifiedAt: Long`. Source ID is context, not a cascading FK; no FK may delete draft writing.
- `MIGRATION_1_2: Migration`; `CommonplaceDatabase.notebookDao(): NotebookDao`. Room schema version 2 and explicit migration registration.

- [ ] **1. Write `v1MigrationPreservesEveryExistingValueAndConstraint`.** Seed exported v1 using raw SQL, including both kinds, null/empty fields, Malayalam/CJK/decomposed Unicode, native JSON whitespace/unknown fields, orphaned attribution, missing sources, bookmarks/progress and reader drafts. Test exact old-row values after migration, defaults `revision=0`/commit=null, preserved source checksum uniqueness and entry SET NULL behavior. Add `freshAndMigratedV2SchemasMatch` and `migrationFailureNeverFallsBackToDeletion` using a transaction-aborting migration fixture, not a production fallback.
- [ ] **2. Run the failing test.** `./gradlew --no-daemon --dependency-verification=strict :app:testAndroidxDebugUnitTest --tests '*NotebookMigrationTest'`. Establish the helper/dependency wiring first; missing tool/dependency errors are not a behavioral red result. Expected behavioral failure: v2 migration/schema is unavailable or expected preserved/default values differ.
- [ ] **3. Implement the minimal schema and migration.** Rebuild entries only as needed for attribution nullability, copying existing columns without parsing/re-encoding locators or changing text. Restore FK/index definitions exactly; add draft schema, export v2, register the migration. Expose exported schemas to device tests with `androidTest.assets.srcDir("schemas")` and host test resources with `test.resources.srcDir("schemas")`; keep schema-seeding helpers in tests. Update nullable-attribution call sites narrowly; do not change existing reader write rules or test expectations to conceal regressions.
- [ ] **4. Verify both flavor unit suites and device migration class.** Run the host gate below; later run the same migration class on API 28/37.0. Confirm new/migrated schema equality, no destructive fallback and all original repository tests still pass.
- [ ] **5. Commit only the schema/migration deliverable:** `feat: add lossless notebook schema migration`. Push only after its applicable checks pass, with runtime evidence honestly pending if unavailable.

## Task 2: Entry rules and guarded draft/commit storage

**Files:** Create `notebook/NotebookStore.kt`, `data/NotebookRepository.kt`, `app/src/androidTest/java/im/commonplace/reader/data/NotebookRepositoryTest.kt`; extend `NotebookModels.kt`, `NotebookDao.kt`, `AppContainer.kt` and only necessary `ReaderRepository.kt` compatibility points.

**Consumes:** Task 1 types and `CommonplaceDatabase`.

**Produces:** `NotebookRepository(database, io = Dispatchers.IO, clock = System::currentTimeMillis, newId = UUID generator) : NotebookStore`; expose it from AppContainer.

`NotebookStore` contracts:
- `observeEntries(kind: EntryKind?): Flow<List<EntryEntity>>` orders `createdAt DESC, id ASC`; `observeDrafts(): Flow<List<NotebookDraftEntity>>`; `getEntry(id: String): EntryEntity?`; `getDraft(targetId: String): NotebookDraftEntity?` (all reads except Flow are suspend).
- `observeSources(): Flow<List<SourceEntity>>` reuses the existing source DAO flow, including MISSING records, for the ViewModel's source picker state; no new source store is introduced.
- `beginNew(kind: EntryKind, sourceId: String?): NotebookDraftEntity` allocates stable target/session IDs and draft revision 0; rejects HIGHLIGHT and invalid source/type combinations. New THOUGHT attribution is null.
- `beginEdit(entryId: String): BeginEditResult`, with `Started(draft)`, `ResumeRequired(draft)` or `Unavailable(reason)`. No overwriting an existing draft.
- `resumeDraft(targetId: String, expectedSessionId: String, expectedRevision: Long): DraftWriteResult` claims a new session atomically only when the expected row still matches.
- `writeDraft(targetId: String, sessionId: String, expectedRevision: Long, fields: NotebookFields, selectedSourceId: String?): DraftWriteResult`; `Written(draft)`, `Conflict` or `Missing`. Existing entries cannot change their source; for new entries, an explicit selection change refreshes both source snapshots transactionally. A successful write increments draft revision and assigns a new stable commit token for that persisted snapshot.
- `discardDraft(targetId: String, sessionId: String, expectedRevision: Long): DiscardResult`, with `Discarded`, `Conflict` or `Missing`; deletes only the matching draft; no unconditional upsert.
- `commit(request: NotebookCommitRequest): NotebookCommitResult`, where request carries targetId/sessionId/draftRevision/commitToken. Result is `Saved(entry)`, `Conflict(current: EntryEntity?)`, `Invalid(reason)` or `MissingDraft`. Storage exceptions/cancellation propagate to the ViewModel.

**Commit algorithm:** in one transaction, check an already-present target's lastNotebookCommitId against the requested token before requiring a draft, then validate the matching draft/session/revision, entry base revision and allowed fields. New entry IDs must not collide; existing entry revisions must match. Preserve all immutable fields on edit. For a new source-linked entry obtain attribution from the live source row, including MISSING rows; reject an absent row without deleting the draft. Insert/update, increment entry revision (new entries start at 1), record the token and delete the matching draft atomically. A superseded token returns Conflict, never replay. Commit token strings are opaque IDs, not timestamps.

- [ ] **1. Write real file-backed tests.** `newThoughtPreservesExactBodyAndHasNoAttribution`, `sourceNoteAndAnnotationKeepDistinctKinds`, `highlightEditChangesOnlyTitleReflectionColor`, `orphanedEntryRemainsEditable`, and `newSourceNoteRetainsDraftIfSourceRowDisappears`. Assert exact UUID/createdAt/quotation/attribution/locator preservation; unsupported location payloads may remain unresolved but are never rewritten merely to edit body. Safe editing of an inconsistent legacy record returns Unavailable without changing it.
- [ ] **2. Write transaction/race tests before each corresponding implementation:** `commitClearsOnlyItsDraftAtomically`, `failedDraftDeleteRollsBackEntryUpdate`, `retryAfterCommittedAcknowledgmentLossDoesNotDuplicate`, `retryAfterNewerCommitReturnsConflict`, `staleSessionCannotOverwriteOrResurrectDraft`, `twoTargetsKeepIndependentDrafts`, `blankRequiredBodyStaysDraftOnly`, `sourceSnapshotsRefreshOnlyOnExplicitNewSourceChange`. Use real SQLite triggers for rollback and barriers for ordering; never assert only mock call counts.
- [ ] **3. Observe red.** Run each new test with `:app:testAndroidxDebugUnitTest --tests '*NotebookRepositoryTest.<method>'`; expected failure is the missing/incorrect storage behavior, not an infrastructure error. Add missing signatures only as necessary to reach an assertion failure.
- [ ] **4. Implement the smallest DAO/repository operations**, with database transactions, revision/session CAS predicates and exact text retention. A delayed writer may update an existing matching draft only, never recreate one. The submitted commit token remains stable across retries.
- [ ] **5. Run both full flavor unit suites**, existing reader repository tests and focused instrumented repository tests when a disposable emulator is available. Confirm schema 2 works through the production AppContainer, not only test builders.
- [ ] **6. Commit:** `feat: add guarded notebook drafts and entry commits`.

## Task 3: Lifecycle-aware notebook state and editor coordination

**Files:** Create `notebook/NotebookViewModel.kt`, `app/src/test/java/im/commonplace/reader/notebook/NotebookViewModelTest.kt`; extend `NotebookModels.kt`. Keep test-only fake stores in the test source set.

**Consumes:** `NotebookStore` from Task 2.

**Produces:** `NotebookViewModel(store: NotebookStore)` with `StateFlow<NotebookUiState>`; state includes Loading/Empty/Content/Error list state, filter, selected detail, drafts, editor snapshot, durable draft handle, save status, conflict saved version and notice. Actions: `setFilter(kind: EntryKind?)`, `openEntry(id: String)`, `newEntry(kind: EntryKind, sourceId: String?)`, `editEntry(id: String)`, `resumeDraft(targetId: String)`, `updateFields(fields: NotebookFields)`, `selectNewSource(sourceId: String)`, `save()`, `retrySave()`, `discardDraft()`, `flushDraft(): Job`, `requestLeave()`, `reloadSavedEntry()`, `retryLoad()`.

- [ ] **1. Write scheduling tests with virtual time.** Pin `advanceTimeBy(299)` = no draft acknowledgment, then the 300-ms boundary triggers one latest-value durable write. Test `savedLabelWaitsForCommit`, `draftSavedIsNotEntrySaved`, `failedWriteKeepsInput`, `saveFreezesSubmittedFieldsAndRepeatedTapIsIgnored`, `retryUsesSameCommitToken`, `backgroundFlushUsesLatestSnapshot`, `staleCompletionCannotAcknowledgeNewerText`, and `conflictKeepsDraftAndShowsCurrentSavedVersion`.
- [ ] **2. Add leave/recovery tests.** `leaveWhileUnacknowledgedOffersRetryStayOrDiscard`, `openingExistingDraftDoesNotOverwriteIt`, `draftForAnotherEntryDoesNotCollide`, `discardWaitsForGuardedDeleteBeforeLeaving`. A draft-load error cannot silently start a blank draft. State must not classify an unacknowledged write as safely absent.
- [ ] **3. Run `:app:testAndroidxDebugUnitTest --tests '*NotebookViewModelTest'` and observe assertion failures**, with deterministic coroutine dispatcher setup, before writing coordination logic.
- [ ] **4. Implement state collection, 300-ms coalescing, serialized per-editor operations and generation guards.** Save flushes/finalizes the current snapshot before commit and reuses it on retry. New typing is disabled while commit is pending; failures restore the editable input. Do not rely on ViewModel cleanup to flush; lifecycle/navigation callers invoke flush while the scope is alive. Resume claims the stored session before further writes.
- [ ] **5. Run both complete flavor unit suites**, verifying actual state transitions as well as store arguments; use Task 2's SQLite tests as the authority for persistence behavior.
- [ ] **6. Commit:** `feat: coordinate notebook editing and draft recovery`.

## Task 4: Functional Commonplace screens and editor integration

**Files:** Create `ui/{NotebookScreen,NotebookDetailScreen,NotebookEditorScreen}.kt`, `app/src/androidTest/java/im/commonplace/reader/ui/NotebookTest.kt`; modify `ui/CommonplaceApp.kt`, `AppContainer.kt` composition as necessary, `app/src/androidTest/java/im/commonplace/reader/ui/ShellTest.kt`. Do not alter the Library sort feature that was not selected.

**Consumes:** Task 3 state/actions and repository source list; pass source records through a ViewModel, never query the database in a composable.

**Produces:** `NotebookScreen(contentPadding: PaddingValues, onOpenEntryInReader: (String) -> Unit, model: NotebookViewModel)` with list/detail/editor rendering and functional tab integration. Internal detail/editor composables accept immutable state and callbacks. Retain route/detail ID and filter through `rememberSaveable`/SavedStateHandle as appropriate; durable drafts come only from Room.

- [ ] **1. Add UI tests first.** `commonplaceTabHasHonestEmptyState`, `readerCreatedHighlightAppearsWithSeparateReflection`, `createThoughtSaveThenEditPreservesIdentity`, `createAnnotationAndTwoSourceNotesForSameBook`, `typeFilterUsesOnlyFinishedEntries`, `draftsOfferResumeAndDiscard`, `highlightQuotationAndAttributionAreNotEditable`, `missingBookWritingRemainsReadableAndEditable`, `loadingFailureOffersRetry`. Test actual committed rows after Save, not just a toast.
- [ ] **2. Run the focused device command** below with class `im.commonplace.reader.ui.NotebookTest` on one available disposable emulator. Expected red: missing tab/controls or failing state behavior. If local runtime is absent, use a draft branch/CI run to obtain red before implementing screens; do not label inability to launch as the red test.
- [ ] **3. Build the functional screens.** Add the Commonplace destination only with working list/detail/editor. List order is newest-created/UUID tie-break, filters are All and four types, drafts have their own section. New-entry options exclude Highlight. Thought/Annotation/Source-note body is required; highlight reflection optional. Preserve exact accepted text and render optional-title fallbacks only in the UI.
- [ ] **4. Wire lifecycle/save/error UX.** Flush on ON_STOP and before navigation, distinguish Draft saved on this device from Saved, handle pending/error/conflict states, and prevent duplicate submission. Source picker can select MISSING metadata. Unacknowledged leave flow offers retry/stay or explicit discard; Back never silently loses input.
- [ ] **5. Add UI regression checks** for keyboard/IME insets, rotation, light/dark and 200% system text, scroll access to Save/conflict actions, semantic names/focus and minimum 48-dp targets. Test newer saved text remains visible separately during conflict; Discard never deletes the finished entry. Spoken TalkBack is an explicit manual gate, not inferred from semantics.
- [ ] **6. Run both unit suites, lint/build gate, both-flavor NotebookTest/ShellTest on API 28/37.0 as available.** Record runtime failures/pending cases without pretending the host suite verifies UI interaction.
- [ ] **7. Commit:** `feat: add Commonplace notebook and recoverable editors`.

## Task 5: Resolve and open the exact saved EPUB passage

**Files:** Create `reader/EntryReaderTarget.kt`, `app/src/test/java/im/commonplace/reader/reader/EntryReaderTargetTest.kt`, `app/src/androidTest/java/im/commonplace/reader/reader/NotebookPassageTest.kt`; modify `ReaderActivity.kt`, `reader/ReaderViewModel.kt`, `reader/epub/ReadiumHost.kt`, `ui/ReaderScreen.kt` and notebook route integration. Add focused cases to existing `ReaderViewModelTest.kt`.

**Consumes:** `NotebookStore.getEntry`, existing source lookup/VerifiedSource and Readium locator validation; supported notebook details from Task 4.

**Produces:** `EntryReaderTargetResolver(notebook: NotebookStore, reader: ReaderStore, verifyFile: suspend (SourceEntity) -> File)` with `suspend resolveEntryReaderTarget(entryId: String): EntryReaderTarget`. Production verification delegates to existing `verifySourceFile(filesDir, source)`; tests inject disposable file verification. Results: `Passage(entryId, sourceId, location)`, `Book(entryId, sourceId)` for available EPUB source-level notes, or `Unavailable(reason)`. Reader Intent extra `entryId` is an internal stable-ID request; validate it and resolve records, not caller-provided JSON. Keep current sourceId route for Library.

Keep `ReaderViewModel.open(sourceId: String)` for Library and add `openTarget(target: EntryReaderTarget)` for a resolved notebook request, so target resolution occurs once. The Activity handles Unavailable without opening an unrelated source. A navigation request token is consumed once after native readiness, survives recreation as consumed and is replaced by a genuinely new entry request. Notebook exit callback says Back to entry; Library launches retain Library exit behavior.

- [ ] **1. Write resolver tests:** `sourceChecksumMismatchRetainsWritingAndRefusesTarget`, `unknownEngineOrEnvelopeIsUnresolved`, `missingSourceKeepsSnapshot`, `pdfTargetNeverBecomesAcceptedPassage`, `sourceNoteOffersBookNotInventedPassage`, `entryIdCannotSelectUnrelatedSource`. Preserve opaque locator bytes and report safe messages without private paths.
- [ ] **2. Write ViewModel/native tests:** `sameSourceNewEntryRequestIsNotIgnored`, `oldSourceEventCannotConsumeNewJump`, `recreationDoesNotReplayConsumedJump`, `entryLocationBeatsLastProgress`, `nativePositionAfterJumpUpdatesNormalProgress`, `returnRestoresEntryDetail`. Use the original reflowable fixture with two distinct passages and a third saved-progress position; compare native locator/selected text, not only that the Activity launched.
- [ ] **3. Observe red** in host resolver/ViewModel tests and focused `NotebookPassageTest` before implementing the route. Existing in-session Open passage is not proof of the new cross-Activity handoff.
- [ ] **4. Implement stable-ID resolution, existing canonical-path/checksum checks and readiness-gated navigation.** Do not write fabricated progress while waiting; subsequent real navigator events retain normal debounce/flush behavior. Keep full locators and source-switch guards. A failed/missing target must never silently fall back to a different last-progress passage labeled as success.
- [ ] **5. Run both full flavor unit suites, lint/build, focused native tests on both APIs/flavors**, plus existing EPUB proof/recovery. PDF source annotations remain notebook-visible but no PDF passage success is claimed.
- [ ] **6. Commit:** `feat: reopen notebook entries at exact EPUB passages`.

## Task 6: Real notebook recovery and independent CI evidence

**Files:** Create `app/src/androidTest/java/im/commonplace/reader/notebook/NotebookRecoveryProbe.kt`, `tools/run_notebook_recovery.py`, `tools/test_run_notebook_recovery.py`, `tools/verify_notebook_test_results.py`, `tools/test_verify_notebook_test_results.py`; modify `.github/workflows/android.yml` and only necessary orchestration in `tools/run_emulator_smoke.py` with its existing regression tests. Retain existing `verify_android_test_results.py` full-suite/PDF requirements unchanged unless adding new required cases.

**Consumes:** Tasks 1–5 and current disposable-emulator conventions.

**Produces:** `python3 tools/run_notebook_recovery.py` runs both flavors; `python3 tools/verify_notebook_test_results.py <xml-root>` requires the exact named notebook/migration/navigation cases. Evidence under `build/reports/notebook-recovery/<flavor>/` and separate notebook XML archives. Report source SHA, emulator API/ABI, flavor and per-case status.

- [ ] **1. Test runner failures first.** Python tests `missingAcknowledgmentMarkerFails`, `emptyOrUnchangedPidFails`, `killWithoutPidDisappearanceFails`, `missingOrSkippedVerificationCaseFails`, `bothFlavorsRunAfterFirstFailure`, `recoveryUsesDedicatedDisposableMarker`. A zero `adb` exit is not enough; require exactly the intended passing assertion count and reject crashes/aborts/skips. Guard tests reject absent XML, duplicate/missing named cases, failure/error/skip and mismatched run identities.
- [ ] **2. Write guarded device stages and verification cases.** Separately prove an acknowledged unfinished new Thought, an edit draft over a saved highlight, two independent drafts, and a committed Unicode entry with no lingering draft. Persist IDs/expected text in a dedicated test marker, confirm the real live app PID, force-stop and confirm disappearance, launcher-reopen with a fresh PID, then verify through actual UI/repository state. Guarded ordinary-suite no-ops are not recovery passes. Never run these probes on personal data.
- [ ] **3. Run Python tests red**, then implement the minimal runner and result guard using the existing reader-recovery protocol. Run device stages red on disposable CI before adding only the missing integration behavior; do not weaken readiness/PID/assertion predicates to get a pass.
- [ ] **4. Run notebook instrumentation/recovery independently of the failing PDF suite.** Reuse the existing API `['28', '37.0']` smoke matrix and pinned emulator setup; do not duplicate emulator jobs. Extend `run_emulator_smoke.py` to run focused migration/repository/UI/navigation classes for each flavor, then notebook recovery unconditionally after earlier test failures, preserving separate outputs. All planned notebook phases must run and record results even when another notebook phase fails; aggregate failures at the end, do not hide them. Existing full-suite/PDF checks remain enabled, with known failures visible; do not add ignored tests or convert their failures to success.
- [ ] **5. Extend Python orchestration regression tests** to prove later notebook phases and both flavors still execute after a simulated earlier failure; final status remains nonzero if any required phase failed. Preserve original full-suite result guard and historical PDF limits. Upload logs/XML/recovery evidence with `if: always()`.
- [ ] **6. Execute API 28/37.0 × both-flavor notebook/recovery matrix and retain exact-source reports.** Explicitly label unavailable spoken TalkBack/physical hardware checks NOT RUN. If native evidence fails, keep the milestone incomplete and fix only within approved scope.
- [ ] **7. Commit:** `test: prove notebook recovery across real process restarts`.

## Task 7: Integrated acceptance, independent review and honest status

**Files:** Review all task changes; update `README.md`, `docs/{ARCHITECTURE,DATA_MODEL,TESTING}.md`. Do not overwrite historical proof evidence or mark the PDF milestone accepted. Add `docs/NOTEBOOK_PROOF.md` for concise exact-source acceptance results and evidence links.

- [ ] **1. Run the complete host/build and Python/fixture gates below** against the final product/test commit. Record every failing test by name, warnings and command exits. A focused test run is not a full suite.
- [ ] **2. Run full existing device suites plus new independent notebook/recovery lanes.** Attempt full-suite/PDF checks without dropping assertions; report retained known PDF failures separately from notebook acceptance. Require all notebook storage/UI/navigation/recovery cases on API 28/37.0 and both flavors before calling this milestone verified. Verify existing native EPUB capture/recovery and import recovery have not regressed.
- [ ] **3. Check accessibility and data ownership.** Perform spoken TalkBack when a capable environment is available, otherwise mark NOT RUN and do not claim a manual audit. Verify backup exclusions/no Internet permission still cover drafts; inspect log output for private writing; verify source files remain untouched.
- [ ] **4. Obtain an independent integrated review.** Native mode uses a fresh reviewer of the whole diff; subagent mode still gets a whole-branch review after task reviews. Focus on migration content preservation, stale writer/retry ordering, immutable capture evidence, same-source navigation and exact evidence attribution. Fix findings test-first; rerun affected and complete host suites before accepting fixes.
- [ ] **5. Update user-facing status only from evidence.** Mark notebook PASS/FAIL/NOT RUN separately from full-beta/PDF status; preserve deferred requirements and actual support limits. Identify original app/test source SHA for each report, not a later docs-only SHA. A failure discovered at acceptance blocks the corresponding claim, not automatic scope expansion.
- [ ] **6. Commit documentation** with `docs: record notebook milestone evidence and limits [skip ci]` only when no product/test source changed in that commit. Push normally after checking remote ancestry; if remote advanced, integrate without overwriting unrelated work and rerun the applicable gate. End with completed tasks, exact commits, evidence, remaining failures and next owner decisions.
- [ ] **7. Deliver the requested trial APK at the end.** Copy the final verified `app/build/outputs/apk/androidx/debug/app-androidx-debug.apk` outside Git to `/workspace/Commonplace-Reader/deliverables/commonplace-notebook-androidx-debug.apk`. Verify file existence, SHA-256, package ID `im.commonplace.reader.androidx`, minSdk 28 and association with the tested product commit. Supply its exact path/checksum and installation guidance with the final result. Label it a debug notebook/EPUB trial with an experimental, unaccepted PDF comparison—not a release or a PDF-engine choice. Do not install it on the owner's computer/device without a request. If notebook runtime checks remain failing or unavailable, state those limits prominently rather than presenting the APK as verified.

## Reproducible verification commands

Use DEVELOPMENT's pinned JDK 17/SDK setup. The following commands are planned checks, **not results already obtained**.

Full host/build gate after each implementation task where applicable:

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:testAndroidxDebugUnitTest --rerun :app:testComposepdfDebugUnitTest --rerun \
  :app:lintAndroidxDebug :app:lintComposepdfDebug \
  :app:assembleAndroidxDebug :app:assembleComposepdfDebug \
  :app:assembleAndroidxDebugAndroidTest :app:assembleComposepdfDebugAndroidTest
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 -m unittest tools/fixtures/test_generate_fixtures.py -v
```

Focused notebook lane, on **each** disposable API 28/37.0 emulator (repeat per flavor task; never attribute one to both):

```sh
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedAndroidxDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=im.commonplace.reader.data.NotebookMigrationTest,im.commonplace.reader.data.NotebookRepositoryTest,im.commonplace.reader.ui.NotebookTest,im.commonplace.reader.reader.NotebookPassageTest
# Archive these XML files before another instrumentation run overwrites them.
python3 tools/verify_notebook_test_results.py build/reports/notebook-tests/androidx
# Repeat connectedComposepdfDebugAndroidTest and archive under .../composepdf.
python3 tools/run_notebook_recovery.py
```

Keep existing full-suite commands/result guard and `run_import_recovery.py`, `run_reader_recovery.py`, `run_pdf_recovery.py` as documented. A known PDF failure does not authorize skipping notebook tests, and a green focused notebook lane does not make the complete existing suite green.

## Plan review and execution handoff

**No implementation has started.** Review this plan together with the approved spec, then choose:

- **Native (recommended):** one implementer follows these seven tightly coupled storage/lifecycle tasks, then a fresh reviewer checks the integrated result. This minimizes interface drift and duplicated context.
- **Subagent-driven:** a fresh implementer and reviewer for each task, followed by whole-branch review. More independent checkpoints, with greater coordination overhead.

Approval of this written plan plus the selected execution method authorizes implementation. Preserve the selected method throughout unless the owner changes it. Significant scope/risk changes stop for a decision; ordinary progress uses brief keep-alives rather than repeated feature authorization.
