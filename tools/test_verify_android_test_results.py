import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_android_test_results.py")
SHELL_ONLY = ('<testsuites tests="1" failures="0" errors="0" skipped="0">'
           '<testsuite><testcase name="launchAndNavigate" '
           'classname="im.commonplace.reader.ui.ShellTest"/></testsuite></testsuites>')

REPOSITORY_CASES = (
    'unicodeCaptureAndAnnotationRecoverExactValuesAndSourceAttribution',
    'blankCaptureIsRejectedWithoutAnEntry',
    'progressAndTwoBookmarksRemainIndependentAfterReopen',
    'draftSurvivesReopenAndNeverAppearsAsEntry',
    'successfulCaptureClearsDraftInSameCommit',
    'failedCaptureRetainsDraftAndLeavesNoEntry',
    'failureAfterInsertRollsBackEntryAndDraftDeletion',
    'missingSourceAndFormatMismatchCannotCreatePartialRecords',
    'unknownLocationVersionRemainsRecoverableButCannotOverwriteProgress',
    'cancellationPropagatesWithoutAcknowledgingOrSaving',
    'sourceDeletionRetainsWritingAndAttributionButRemovesDependentState',
    'duplicateChecksumAndDanglingForeignKeysAreRejected',
    'invalidDraftOrBlankAnnotationDoesNotOverwriteExistingWriting',
)
OLD_PASSING = SHELL_ONLY.replace('tests="1"', 'tests="14"').replace("</testsuite>", "".join(
    f'<testcase name="{name}" classname="im.commonplace.reader.data.ReaderRepositoryTest"/>'
    for name in REPOSITORY_CASES) + "</testsuite>")

IMPORT_CASES = (
    'cancellationAtRegistrationNeverDeletesDurablyReferencedAsset',
    'duplicateOfMissingSourceDoesNotPretendToRepairOrDeleteWriting',
    'unknownLengthUnseekableStreamCopiesExactBytesIntoRandomRelativeAsset',
    'extensionAndMimeAreNotAcceptedAsEvidenceOfPdfContent',
    'pdfHeaderWithoutParseableDocumentIsUnsupported',
    'invalidEpubContainerIsUnsupported',
    'missingSpineMemberIsUnsupported',
    'revokedPermissionReturnsSafeFailureWithoutProviderPath',
    'providerFailureHalfwayDoesNotRegisterOrLeaveAFile',
    'remainingSpaceIsCheckedDuringCopyNotJustAtStart',
    'enospcFromStreamBoundaryLeavesNoPartial',
    'cancellationPropagatesAndRemovesStaging',
    'cancelledCoroutineMidCopyLeavesNoSource',
    'databaseFailureAfterPlacementRemovesOnlyNewAssetAndKeepsWriting',
    'concurrentDuplicatesHaveOneCompleteAssetAndOneSource',
    'startupRemovesAbandonedStagingAndOrphanButRetainsNotesAndUnownedFiles',
    'missingAssetBecomesUnavailableWithoutLosingSourceOrWriting',
    'unsafeZipPathsAndDuplicateMembersAreRejected',
    'oversizedZipMemberIsRejectedBeforeParsing',
    'xmlDoctypeAndEntityExpansionAreRejectedWithoutFetching',
    'successfulCopyCanBeReopenedAfterOriginalAccessIsRevoked',
    'inputBeyond256MiBStopsWithoutRegisteringOrBufferingWholeFile',
    'tooManyZipMembersAreRejected',
    'cumulativeZipExpansionAbove512MiBIsRejected',
    'duplicateZipPathsAndForgedCrcAreRejected',
)
PROVIDER_CASES = (
    'spoofedMimeUnknownLengthPipeImportsEpubAndSurvivesProviderRevocationOffline',
    'realPartialProviderPipeLeavesNoUsableSource',
    'sharedPdfCorpusUsesRealPlatformParserAndRejectsEncryptedAndMalformed',
    'sharedEpubCorpusImportsWithoutRenderingOrFetchingRemoteResources',
)
PASSING = OLD_PASSING.replace('</testsuite>', ''.join(
    f'<testcase name="{name}" classname="im.commonplace.reader.imports.DocumentImporterTest"/>'
    for name in IMPORT_CASES) + ''.join(
    f'<testcase name="{name}" classname="im.commonplace.reader.fixtures.ProviderImportTest"/>'
    for name in PROVIDER_CASES) + '</testsuite>')

READER_CASES = (
    'readerOwnsOneRealNavigator',
    'exactNativeSelectionCaptureBookmarkReopenDecorationAndSearch',
    'sourceSwitchClosesPriorNavigatorAndIntegrityMismatchDoesNotApplyCoordinates',
    'hostilePublicationMakesZeroRequestsToWorkingControlledEndpointWhileLocalReadingWorks',
    'malformedDrmAndFixedLayoutReportExplicitErrorOrUnsupported',
    'controlsRemainReachableAt200PercentTextInDarkLandscape',
    'nativeContainerSurvivesResizePanelTransitionsAndTeardown',
)
PRE_READER_PASSING = PASSING
PASSING = PASSING.replace('</testsuite>', ''.join(
    f'<testcase name="{name}" classname="im.commonplace.reader.reader.ReadiumProofTest"/>'
    for name in READER_CASES) + '</testsuite>')

class InstrumentationResultGuardTest(unittest.TestCase):
    def run_guard(self, reports):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for flavor, content in reports.items():
                folder = root / flavor
                folder.mkdir()
                (folder / "TEST-emulator.xml").write_text(content)
            return subprocess.run([sys.executable, str(SCRIPT), directory],
                                  capture_output=True, text=True)

    def test_accepts_executed_shell_test_in_both_flavors(self):
        self.assertEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), PASSING)).returncode, 0)

    def test_rejects_missing_reports_even_if_gradle_succeeded(self):
        self.assertNotEqual(self.run_guard({}).returncode, 0)

    def test_rejects_missing_second_flavor(self):
        self.assertNotEqual(self.run_guard({"androidx": PASSING}).returncode, 0)

    def test_rejects_zero_tests(self):
        empty = '<testsuites tests="0" failures="0" errors="0" skipped="0"/>'
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), empty)).returncode, 0)

    def test_rejects_failed_or_skipped_shell_test(self):
        for child in ("failure", "error", "skipped"):
            with self.subTest(child=child):
                report = PASSING.replace('/></testsuite>', f'><{child}/></testcase></testsuite>')
                self.assertNotEqual(self.run_guard({"androidx": report, "composepdf": PASSING}).returncode, 0)

    def test_rejects_other_test_in_place_of_shell_test(self):
        other = PASSING.replace("launchAndNavigate", "someOtherTest")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), other)).returncode, 0)

    def test_rejects_shell_only_reports_without_durable_repository_tests(self):
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), SHELL_ONLY)).returncode, 0)

    def test_rejects_one_missing_repository_case(self):
        report = PASSING.replace(REPOSITORY_CASES[0], "notTheDurabilityContract")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), report)).returncode, 0)

    def test_rejects_previous_milestone_without_importer_cases(self):
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), OLD_PASSING)).returncode, 0)

    def test_rejects_one_missing_import_case(self):
        report = PASSING.replace(IMPORT_CASES[0], "notTheImportContract")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), report)).returncode, 0)

    def test_rejects_one_missing_provider_case(self):
        report = PASSING.replace(PROVIDER_CASES[0], "notTheRealProviderContract")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), report)).returncode, 0)

    def test_rejects_previous_import_milestone_without_native_reader_cases(self):
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), PRE_READER_PASSING)).returncode, 0)

    def test_rejects_one_missing_native_reader_case(self):
        report = PASSING.replace(READER_CASES[0], "notTheNativeReaderContract")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), report)).returncode, 0)

    def test_rejects_missing_resize_and_teardown_native_case(self):
        report = PASSING.replace("nativeContainerSurvivesResizePanelTransitionsAndTeardown", "notTheResizeAndTeardownContract")
        self.assertNotEqual(self.run_guard(dict.fromkeys(("androidx", "composepdf"), report)).returncode, 0)

    def test_rejects_malformed_xml(self):
        self.assertNotEqual(self.run_guard({"androidx": "broken", "composepdf": PASSING}).returncode, 0)


if __name__ == "__main__":
    unittest.main()
