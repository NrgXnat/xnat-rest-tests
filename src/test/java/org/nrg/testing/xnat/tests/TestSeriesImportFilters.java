package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.components.ComponentizedTest;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.dicom.FilterMode;
import org.nrg.xnat.pogo.dicom.SeriesImportFilter;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.nrg.xnat.versions.Xnat_1_9_0;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.nrg.testing.TestGroups.IMPORTER;

@AddedIn(Xnat_1_8_10.class)
@DeprecatedIn(Xnat_1_9_0.class)
public class TestSeriesImportFilters extends BaseFileNamerTest {

    private static final String SERIES_6_SPEC = "sample1_series6.json";
    private static final LocallyCacheableDicomTransformation FULL_FEATURE_DATA = new LocallyCacheableDicomTransformation("full-sif-feature-data")
            .data(TestData.SAMPLE_1_SCAN_4)
            .simpleTransform(
                    (dicomInstance) -> {
                        if (instanceNumberWithin(dicomInstance, Arrays.asList(8, 9, 10))) {
                            dicomInstance.setString(Tag.ReceiveCoilName, VR.SH, "Something");
                        }
                        if (!instanceNumberEquals(dicomInstance, 25)) {
                            dicomInstance.setString(Tag.BeatRejectionFlag, VR.CS, "N");
                        }
                        if (instanceNumberEquals(dicomInstance, 50)) {
                            dicomInstance.setString(Tag.ImageType, VR.CS, "ORIGINAL", "MODIFIED", "SECONDARY");
                        }
                        if (instanceNumberEquals(dicomInstance, 60)) {
                            dicomInstance.setString(Tag.ImageType, VR.CS, "MODIFIED", "DERIVED");
                        }
                    }
            );

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1)
    public void testSeriesImportFilterBeyondStudyComments() {
        final List<Integer> instanceNumbersMatchingBlockSlabFilter = Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19, 23, 29);
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation("slab-filter-test-data")
                .data(TestData.SAMPLE_1)
                .simpleTransform(
                        TransformFunction.simple(
                                (dicomInstance) -> {
                                    if (instanceNumberWithin(dicomInstance, instanceNumbersMatchingBlockSlabFilter)) {
                                        addBlockSlabItems(dicomInstance);
                                    }
                                }
                        )
                );

        mainAdminInterface().updateDicomFileNamer("${InstanceNumber}");
        genericCstoreFilterTest(
                filterFromFile("block_slab_exists.txt").filterMode(FilterMode.WHITELIST),
                dicomTransformation,
                "sample1_instances_matching_block_slab_filter.json"
        );
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1)
    public void testSeriesImportFilterImplicitSeriesDescription() {
        genericCstoreFilterTest(
                filterFromFile("contains_t1.txt").filterMode(FilterMode.BLACKLIST),
                TestData.SAMPLE_1,
                SERIES_6_SPEC
        );
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testSeriesImportFilterFunctionalityStringKeys() {
        functionalityTest("feature_set_test_strings.txt");
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testSeriesImportFilterFunctionalityNumericKeys() {
        functionalityTest("feature_set_test_numeric.txt");
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1)
    @ExpectedFailure(jiraIssue = "XNAT-7886")
    public void testSeriesImportFilterSequenceExistence() {
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation("sif-sequence-data")
                .data(TestData.SAMPLE_1)
                .simpleTransform(
                        (dicomInstance) -> { // series 4 fails the first check, series 5 fails the second check, series 6 passes both
                            final int seriesNum = dicomInstance.getInt(Tag.SeriesNumber, 0);
                            if (seriesNum == 4) {
                                dicomInstance.newSequence(Tag.AnatomicRegionSequence, 0);
                            }
                            if (seriesNum != 5) {
                                dicomInstance.newSequence(Tag.PrimaryAnatomicStructureSequence, 0);
                            }
                        }
                );

        genericCstoreFilterTest(
                filterFromFile("sequence_check.txt").filterMode(FilterMode.BLACKLIST),
                dicomTransformation,
                SERIES_6_SPEC
        );
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1)
    public void testSeriesImportFilterPrivateElements() {
        genericCstoreFilterTest(
                filterFromFile("private_element_filter.txt").filterMode(FilterMode.WHITELIST),
                TestData.SAMPLE_1,
                "sample1_first_instances.json"
        );
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testSeriesImportFilterCstoreWhitelist() {
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation("sif-cstore-whitelist-data")
                .data(TestData.SAMPLE_1_SCAN_4)
                .simpleTransform(
                        (dicomInstance) -> {
                            if (instanceNumberEquals(dicomInstance, 1)) {
                                dicomInstance.setInt(Tag.Rows, VR.US, 128);
                            }
                            if (instanceNumberEquals(dicomInstance, 4)) {
                                dicomInstance.setString(Tag.ReceiveCoilName, VR.SH, "Something");
                            }
                            if (!instanceNumberEquals(dicomInstance, 5)) {
                                dicomInstance.setString(Tag.BeatRejectionFlag, VR.CS, "N");
                            }
                        }
                );
        genericCstoreFilterTest(
                filterFromFile("whitelist_checks.txt").filterMode(FilterMode.WHITELIST),
                dicomTransformation,
                "sample1_series4_first_5_instances.json"
        );
    }

    @Test(groups = IMPORTER)
    @TestRequires(data = TestData.SAMPLE_1)
    public void testSeriesImportFilterDicomZip() {
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation("sif-partial-filtering")
                .data(TestData.SAMPLE_1)
                .createZip()
                .simpleTransform(
                        (dicomInstance) -> {
                            if (dicomInstance.getInt(Tag.SeriesNumber, 0) == 5 && instanceNumberEquals(dicomInstance, 1)) {
                                addBlockSlabItems(dicomInstance);
                            }
                            if (dicomInstance.getInt(Tag.SeriesNumber, 0) == 6) {
                                addBlockSlabItems(dicomInstance);
                            }
                        }
                );
        final String fileSpec = "sample1_partial_filtering.json";

        run(new ComponentizedTest(
                new SetSiteFilter(filterFromFile("block_slab_exists.txt").filterMode(FilterMode.BLACKLIST)),
                new DicomZipStep(dicomTransformation.build().locateOverallZip().toFile(), true),
                REBUILD,
                new ValidateNamesInPrearc(fileSpec),
                ARCHIVE,
                new ValidateNamesInArchive(fileSpec)
        ));
    }

    private void functionalityTest(String filterName) {
        genericCstoreFilterTest(
                filterFromFile(filterName).filterMode(FilterMode.BLACKLIST),
                FULL_FEATURE_DATA,
                "sample1_series4_sifsubset.json"
        );
    }

    private void genericCstoreFilterTest(SeriesImportFilter seriesImportFilter, TestData data, String fileSpec) {
        genericCstoreFilterTest(seriesImportFilter, new CstoreStep(data), fileSpec);
    }

    private void genericCstoreFilterTest(SeriesImportFilter seriesImportFilter, LocallyCacheableDicomTransformation dicomTransformation, String fileSpec) {
        genericCstoreFilterTest(seriesImportFilter, new CstoreStep(dicomTransformation), fileSpec);
    }

    private void genericCstoreFilterTest(SeriesImportFilter seriesImportFilter, CstoreStep dataStep, String fileSpec) {
        run(new ComponentizedTest(
                new SetSiteFilter(seriesImportFilter),
                dataStep,
                REBUILD,
                new ValidateNamesInPrearc(fileSpec),
                ARCHIVE,
                new ValidateNamesInArchive(fileSpec)
        ));
    }

    private void addBlockSlabItems(Attributes dicomInstance) {
        dicomInstance.setInt(Tag.NumberOfBlockSlabItems, VR.IS, 100);
    }

    private SeriesImportFilter filterFromFile(String filterName) {
        return new SeriesImportFilter()
                .enabled(true)
                .filterBody(readDataFile(Paths.get("series_import_filters", filterName).toString()));
    }

    private class SetSiteFilter implements TestComponent {
        private final SeriesImportFilter filter;

        SetSiteFilter(SeriesImportFilter filter) {
            this.filter = filter;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().setSiteSeriesImportFilter(filter);
        }
    }

    private static boolean instanceNumberWithin(Attributes dicomInstance, List<Integer> possibleInstanceNumbers) {
        return possibleInstanceNumbers.contains(dicomInstance.getInt(Tag.InstanceNumber, 0));
    }

    private static boolean instanceNumberEquals(Attributes dicomInstance, int instanceNum) {
        return instanceNumberWithin(dicomInstance, Collections.singletonList(instanceNum));
    }

}
