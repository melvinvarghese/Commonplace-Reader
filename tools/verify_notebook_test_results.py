#!/usr/bin/env python3
"""Reject incomplete, stale, duplicated, skipped or failed notebook device evidence."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REQUIRED_CASES = {
    'im.commonplace.reader.data.NotebookMigrationTest': set('''
        v1MigrationPreservesEveryExistingValueAndConstraint freshAndMigratedV2SchemasMatch migrationFailureNeverFallsBackToDeletion
    '''.split()),
    'im.commonplace.reader.data.NotebookRepositoryTest': set('''
        newThoughtPreservesExactBodyAndHasNoAttribution sourceNoteAndAnnotationKeepDistinctKinds highlightEditChangesOnlyTitleReflectionColor
        orphanedEntryRemainsEditable newSourceNoteRetainsDraftIfSourceRowDisappears commitClearsOnlyItsDraftAtomically failedDraftDeleteRollsBackEntryUpdate
        retryAfterCommittedAcknowledgmentLossDoesNotDuplicate retryAfterNewerCommitReturnsConflict staleSessionCannotOverwriteOrResurrectDraft
        twoTargetsKeepIndependentDrafts blankRequiredBodyStaysDraftOnly sourceSnapshotsRefreshOnlyOnExplicitNewSourceChange
        staleEntryRevisionKeepsBothVersionsAndDraft existingDraftIsOfferedInsteadOfOverwritten inconsistentLegacyEntryCannotBeSilentlyRewritten
        lateDraftWriteAfterCommitNeverResurrects unsupportedOpaqueLocatorBytesAreNotReencodedByEditing
    '''.split()),
    'im.commonplace.reader.ui.NotebookTest': set('''
        commonplaceTabHasHonestEmptyState readerCreatedHighlightAppearsWithSeparateReflection createThoughtSaveThenEditPreservesIdentity
        createAnnotationAndTwoSourceNotesForSameBook typeFilterUsesOnlyFinishedEntries draftsOfferResumeAndDiscard
        highlightQuotationAndAttributionAreNotEditable missingBookWritingRemainsReadableAndEditable loadingFailureOffersRetry
        rotationRetainsAcknowledgedEditorDraft discardEditKeepsFinishedEntry
    '''.split()),
    'im.commonplace.reader.ui.NotebookLayoutTest': {'largeTextLightAndDarkKeepSaveAndConflictActionsReachable'},
    'im.commonplace.reader.reader.NotebookPassageTest': set('''
        entryLocationBeatsLastProgress sameSourceNewEntryRequestIsNotIgnored recreationDoesNotReplayConsumedJump
        immediateRecreationAfterAcceptanceRetainsPendingPassage
        nativePositionAfterJumpUpdatesNormalProgress returnRestoresEntryDetail changedBytesKeepWritingVisibleWithoutNativeFallback
    '''.split()),
}

def verify(root, expected_sha, expected_api=None):
    root=Path(root)
    if not re.fullmatch(r'[0-9a-f]{40}',expected_sha): raise ValueError('invalid expected source SHA')
    identities=[]
    expected={(klass,name) for klass,names in REQUIRED_CASES.items() for name in names}
    for flavor in ('androidx','composepdf'):
        folder=root/flavor
        try: identity=json.loads((folder/'identity.json').read_text())
        except (OSError,ValueError) as error: raise ValueError(f'{flavor}: notebook run identity missing or invalid') from error
        if (identity.get('sourceSha')!=expected_sha or identity.get('flavor')!=flavor
            or not re.fullmatch(r'[0-9a-f]{32}',identity.get('runId',''))
            or not str(identity.get('api','')).isdigit() or not identity.get('abi')
            or (expected_api is not None and identity.get('api')!=str(expected_api))
            or identity.get('exitCode')!=0): raise ValueError(f'{flavor}: notebook run identity or invocation mismatch')
        identities.append(identity['runId'])
        reports=sorted(folder.rglob('TEST-*.xml'))
        if not reports: raise ValueError(f'{flavor}: no notebook instrumentation XML reports')
        hashes={str(p.relative_to(folder)):hashlib.sha256(p.read_bytes()).hexdigest() for p in reports}
        if hashes!=identity.get('xmlSha256'): raise ValueError(f'{flavor}: notebook reports do not match this run')
        seen=set()
        for report in reports:
            try: tree=ET.parse(report).getroot()
            except ET.ParseError as error: raise ValueError(f'{flavor}: malformed notebook XML') from error
            for element in tree.iter():
                if element.tag in ('failure','error','skipped') or any(int(element.get(k,'0')) for k in ('failures','errors','skipped')):
                    raise ValueError(f'{flavor}: notebook failure, error or skip')
                if element.tag=='testcase':
                    case=(element.get('classname'),element.get('name'))
                    if case in seen: raise ValueError(f'{flavor}: duplicate notebook case {case}')
                    if case not in expected: raise ValueError(f'{flavor}: unexpected notebook case {case}')
                    seen.add(case)
        if seen!=expected: raise ValueError(f'{flavor}: missing notebook cases: {sorted(expected-seen)}')
        print(f'{flavor}: {len(seen)} notebook cases PASS; source={expected_sha}; API={identity["api"]}; ABI={identity["abi"]}; run={identity["runId"]}')
    if len(set(identities))!=len(identities): raise ValueError('notebook run identities reused across flavors')
    return True

if __name__=='__main__':
    if len(sys.argv)!=2: sys.exit('Usage: verify_notebook_test_results.py <notebook-xml-root>')
    try:
        sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
        api=subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()
        verify(Path(sys.argv[1]),sha,api)
    except (ValueError,OSError,subprocess.CalledProcessError) as error: sys.exit(str(error))
