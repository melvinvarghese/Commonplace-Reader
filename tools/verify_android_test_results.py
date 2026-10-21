#!/usr/bin/env python3
"""Reject successful exits that omit executed shell, storage or import contracts."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REPOSITORY_CASES = {
    "unicodeCaptureAndAnnotationRecoverExactValuesAndSourceAttribution",
    "blankCaptureIsRejectedWithoutAnEntry",
    "progressAndTwoBookmarksRemainIndependentAfterReopen",
    "draftSurvivesReopenAndNeverAppearsAsEntry",
    "successfulCaptureClearsDraftInSameCommit",
    "failedCaptureRetainsDraftAndLeavesNoEntry",
    "failureAfterInsertRollsBackEntryAndDraftDeletion",
    "missingSourceAndFormatMismatchCannotCreatePartialRecords",
    "unknownLocationVersionRemainsRecoverableButCannotOverwriteProgress",
    "cancellationPropagatesWithoutAcknowledgingOrSaving",
    "sourceDeletionRetainsWritingAndAttributionButRemovesDependentState",
    "duplicateChecksumAndDanglingForeignKeysAreRejected",
    "invalidDraftOrBlankAnnotationDoesNotOverwriteExistingWriting",
}

IMPORT_CASES = {
    'cancellationAtRegistrationNeverDeletesDurablyReferencedAsset',
    'cancellationPropagatesAndRemovesStaging',
    'cancelledCoroutineMidCopyLeavesNoSource',
    'concurrentDuplicatesHaveOneCompleteAssetAndOneSource',
    'cumulativeZipExpansionAbove512MiBIsRejected',
    'databaseFailureAfterPlacementRemovesOnlyNewAssetAndKeepsWriting',
    'duplicateOfMissingSourceDoesNotPretendToRepairOrDeleteWriting',
    'duplicateZipPathsAndForgedCrcAreRejected',
    'enospcFromStreamBoundaryLeavesNoPartial',
    'extensionAndMimeAreNotAcceptedAsEvidenceOfPdfContent',
    'inputBeyond256MiBStopsWithoutRegisteringOrBufferingWholeFile',
    'invalidEpubContainerIsUnsupported',
    'missingAssetBecomesUnavailableWithoutLosingSourceOrWriting',
    'missingSpineMemberIsUnsupported',
    'oversizedZipMemberIsRejectedBeforeParsing',
    'pdfHeaderWithoutParseableDocumentIsUnsupported',
    'providerFailureHalfwayDoesNotRegisterOrLeaveAFile',
    'remainingSpaceIsCheckedDuringCopyNotJustAtStart',
    'revokedPermissionReturnsSafeFailureWithoutProviderPath',
    'startupRemovesAbandonedStagingAndOrphanButRetainsNotesAndUnownedFiles',
    'successfulCopyCanBeReopenedAfterOriginalAccessIsRevoked',
    'tooManyZipMembersAreRejected',
    'unknownLengthUnseekableStreamCopiesExactBytesIntoRandomRelativeAsset',
    'unsafeZipPathsAndDuplicateMembersAreRejected',
    'xmlDoctypeAndEntityExpansionAreRejectedWithoutFetching',
}

PROVIDER_CASES = {
    'realPartialProviderPipeLeavesNoUsableSource',
    'sharedEpubCorpusImportsWithoutRenderingOrFetchingRemoteResources',
    'sharedPdfCorpusUsesRealPlatformParserAndRejectsEncryptedAndMalformed',
    'spoofedMimeUnknownLengthPipeImportsEpubAndSurvivesProviderRevocationOffline',
}

READER_CASES = {
    'readerOwnsOneRealNavigator',
    'exactNativeSelectionCaptureBookmarkReopenDecorationAndSearch',
    'sourceSwitchClosesPriorNavigatorAndIntegrityMismatchDoesNotApplyCoordinates',
    'hostilePublicationMakesZeroRequestsToWorkingControlledEndpointWhileLocalReadingWorks',
    'malformedDrmAndFixedLayoutReportExplicitErrorOrUnsupported',
    'controlsRemainReachableAt200PercentTextInDarkLandscape',
    'nativeContainerSurvivesResizePanelTransitionsAndTeardown',
}

PDF_CASES = {
    'renderingNavigationZoomAndRecreationOrExplicitUnsupported',
    'exactNativeMultilingualSearchAndSelectionOrExplicitUnsupported',
    'repeatedTextSearchUsesZeroBasedNativePagesOrExplicitUnsupported',
    'croppedRotatedGeometryIsNotInventedOrExplicitUnsupported',
    'noTextIsExactAndNotDiagnosedAsScanOrExplicitUnsupported',
    'replacedBytesKeepWritingButNeverApplySyntheticSavedCoordinates',
    'malformedAndEncryptedDoNotCrashOrExplicitUnsupported',
}
COMPOSEPDF_ONLY_PDF_CASES = {
    'largeDocumentDoesNotDuplicateFullFileBuffers',
    'repeatedOpenCloseAndCancellationReleaseResources',
}
COMPOSEPDF_PDF_CASES = PDF_CASES | COMPOSEPDF_ONLY_PDF_CASES


def verify_pdf_focused(root):
    reports = sorted(root.rglob("TEST-*.xml"))
    if not reports:
        raise ValueError("composepdf focused PDF: no instrumentation XML reports")
    passed = set()
    for report in reports:
        tree = ET.parse(report).getroot()
        for element in tree.iter():
            if element.tag in ("failure", "error", "skipped"):
                testcase = next((ancestor for ancestor in tree.iter("testcase")
                                 if element in list(ancestor.iter())), None)
                name = testcase.get("name") if testcase is not None else "unknown"
                raise ValueError(f"composepdf focused PDF case {name} failed, errored or skipped")
            if any(int(element.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                raise ValueError("composepdf focused PDF: non-passing test summary")
            if (element.tag == "testcase"
                    and element.get("classname") == "im.commonplace.reader.reader.pdf.PdfProofTest"):
                passed.add(element.get("name"))
    missing = COMPOSEPDF_PDF_CASES - passed
    if missing:
        raise ValueError(f"composepdf focused PDF cases did not execute: {sorted(missing)}")
    print(f"composepdf focused PDF: {len(COMPOSEPDF_PDF_CASES)} proof cases executed and passed")

def verify(root):
    for flavor in ("androidx", "composepdf"):
        reports = sorted((root / flavor).rglob("TEST-*.xml"))
        if not reports:
            raise ValueError(f"{flavor}: no instrumentation XML reports")
        shell_passed = False
        repository_passed = set()
        import_passed = set()
        provider_passed = set()
        reader_passed = set()
        pdf_passed = set()
        for report in reports:
            tree = ET.parse(report).getroot()
            if flavor in ("androidx", "composepdf"):
                for testcase in tree.iter("testcase"):
                    if testcase.get("classname") == "im.commonplace.reader.reader.pdf.PdfProofTest":
                        name = testcase.get("name")
                        if any(child.tag in ("failure", "error", "skipped") for child in testcase.iter()):
                            raise ValueError(f"{flavor}: PDF case {name} failed, errored or skipped")
                        if flavor == "androidx" and name in PDF_CASES:
                            pdf_passed.add(name)
                        elif flavor == "composepdf" and name in COMPOSEPDF_PDF_CASES:
                            pdf_passed.add(name)
            for element in tree.iter():
                if element.tag in ("failure", "error", "skipped"):
                    raise ValueError(f"{flavor}: failed, errored or skipped test")
                if any(int(element.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                    raise ValueError(f"{flavor}: non-passing test summary")
                if (element.tag == "testcase"
                        and element.get("classname") == "im.commonplace.reader.ui.ShellTest"
                        and element.get("name") == "launchAndNavigate"):
                    shell_passed = True
                if (element.tag == "testcase"
                        and element.get("classname") == "im.commonplace.reader.data.ReaderRepositoryTest"):
                    repository_passed.add(element.get("name"))
                if element.tag == "testcase" and element.get("classname") == "im.commonplace.reader.imports.DocumentImporterTest":
                    import_passed.add(element.get("name"))
                if element.tag == "testcase" and element.get("classname") == "im.commonplace.reader.fixtures.ProviderImportTest":
                    provider_passed.add(element.get("name"))
                if element.tag == "testcase" and element.get("classname") == "im.commonplace.reader.reader.ReadiumProofTest":
                    reader_passed.add(element.get("name"))
        if not shell_passed:
            raise ValueError(f"{flavor}: launchAndNavigate did not execute")
        missing = REPOSITORY_CASES - repository_passed
        if missing:
            raise ValueError(f"{flavor}: repository cases did not execute: {sorted(missing)}")
        for label, expected, executed in (("import", IMPORT_CASES, import_passed), ("provider", PROVIDER_CASES, provider_passed), ("reader", READER_CASES, reader_passed)):
            missing = expected - executed
            if missing:
                raise ValueError(f"{flavor}: {label} cases did not execute: {sorted(missing)}")
        if flavor in ("androidx", "composepdf"):
            expected_pdf = PDF_CASES if flavor == "androidx" else COMPOSEPDF_PDF_CASES
            missing = expected_pdf - pdf_passed
            if missing:
                raise ValueError(f"{flavor}: PDF cases did not execute: {sorted(missing)}")
        pdf_summary = f" and {len(expected_pdf)} PDF proof cases executed" if flavor in ("androidx", "composepdf") else ""
        print(f"{flavor}: shell, {len(REPOSITORY_CASES)} repository, {len(IMPORT_CASES)} import and {len(PROVIDER_CASES)} provider and {len(READER_CASES)} native reader cases executed and passed{pdf_summary}")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--pdf-focused":
        try:
            verify_pdf_focused(Path(sys.argv[2]))
        except (ValueError, OSError, ET.ParseError) as error:
            sys.exit(str(error))
        sys.exit(0)
    if len(sys.argv) != 2:
        sys.exit("Usage: verify_android_test_results.py <connected/debug/flavors> | --pdf-focused <report-directory>")
    try:
        verify(Path(sys.argv[1]))
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(str(error))
