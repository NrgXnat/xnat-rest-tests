package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TestGroups;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.testng.annotations.BeforeClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class BaseSearchFilterTest extends BaseSearchTest {

    protected static final String GROUP_DISPLAY_NAME = "Group";
    protected static final String GROUP_DISPLAY_FIELD_ID = "SUB_GROUP";
    protected static final String GROUP_SCHEMA_PATH = DataType.SUBJECT + "/group";
    protected static final String MR_WEIGHT_SCHEMA_PATH = "xnat:mrSessionData/dcmPatientWeight";
    protected static final String WEIGHT_COLUMN_NAME = "xnat_col_mrsessiondatadcmpatientweight";
    protected static final String WEIGHT_DISPLAY_NAME = "DICOM Patient Weight";
    protected static final String T1_SCAN_COUNT_DISPLAY_NAME = "T1 Scan Count";
    protected static final String MR_T1_SCAN_COUNT_FIELD_ID = "SCAN_COUNT_TYPE=T1";
    protected static final String T1_SCAN_COUNT_COLUMN_NAME = "scan_count_type_t1";
    protected static final String MR_DELAY_SCHEMA_PATH = "xnat:mrSessionData/delay";
    protected static final String DELAY_COLUMN_NAME = "xnat_col_mrsessiondatadelay";
    protected static final String DELAY_DISPLAY_NAME = "Experiment Delay Field";
    protected static final String T1 = "T1";

    protected final Project testProject = registerTempProject();
    
    protected final Subject subjectSymmetric = new Subject(testProject).group("Symmetric"); // I'm so funny
    protected final Subject subjectSpecialLinear = new Subject(testProject).group("Special linear");
    protected final Subject subjectFrobenius = new Subject(testProject).group("Frobenius");
    protected final Subject subjectCyclic = new Subject(testProject).group("Cyclic");
    protected final Subject subjectNull = new Subject(testProject).group("");
    protected final MRSession mrSession1305Pounds = mrWithFields("130.5", "10000", Arrays.asList(T1, T1, "T2"));
    protected final MRSession mrSession150Pounds = mrWithFields("150", "5000", Collections.singletonList(T1));
    protected final MRSession mrSession200Pounds = mrWithFields("200.0", "1234", Arrays.asList("T2", T1, T1, T1));
    protected final MRSession mrSessionNoWeight = mrWithFields("", "", null);

    protected final BiFunction<MRSession, SearchRow, Boolean> mrMatchesLabel =
            (session, row) -> session.getLabel().equals(row.get("mr_project_identifier_" + testProject.getId().toLowerCase()));

    @BeforeClass(groups = TestGroups.SEARCH)
    protected void setup() {
        mainInterface().createProject(testProject);
    }

    protected MRSession mrWithFields(String weight, String delay, List<String> seriesDescriptionsForScans) {
        final Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put(MR_WEIGHT_SCHEMA_PATH, weight);
        fieldMap.put(MR_DELAY_SCHEMA_PATH, delay);
        final List<Scan> scans = IntStream.range(0, seriesDescriptionsForScans == null ? 0 : seriesDescriptionsForScans.size())
                        .mapToObj(index -> {
                            @SuppressWarnings("ConstantConditions") // if it's null, this method will never be called
                            final String description = seriesDescriptionsForScans.get(index);
                            return new MRScan().id(String.valueOf(index)).seriesDescription(description).type(description);
                        }).collect(Collectors.toList());
        return new MRSession(testProject, subjectNull).scans(scans).specificFields(fieldMap);
    }

    protected class SearchValidator<X> {
        private final SearchColumn searchColumnToCheck;
        private final XnatSearchDocument searchDocument;
        private final BiFunction<X, SearchRow, Boolean> responseChecker;

        SearchValidator(SearchColumn searchColumnToCheck, XnatSearchDocument searchDocument, BiFunction<X, SearchRow, Boolean> responseChecker) {
            this.searchColumnToCheck = searchColumnToCheck;
            this.searchDocument = searchDocument;
            this.responseChecker = responseChecker;
        }

        @SafeVarargs
        protected final void performAndValidateSearch(X... expectedObjectsInResponse) {
            final List<X> expectedItems = Arrays.asList(expectedObjectsInResponse);
            final SearchResponse searchResponse = mainInterface().performSearch(searchDocument);
            assertEquals(expectedItems.size(), searchResponse.getTotalRecords());
            assertEquals(expectedItems.size(), searchResponse.getResult().size());
            assertTrue(searchResponse.getColumns().contains(searchColumnToCheck));
            for (X expectedObject : expectedItems) {
                assertTrue(
                        searchResponse
                                .getResult()
                                .stream()
                                .anyMatch(row -> responseChecker.apply(expectedObject, row))
                );
            }
        }
    }

    protected static SearchColumn buildExpectedSearchColumn(String expectedColumnName, String expectedType, DataType dataType, String expectedDisplayName) {
        return new SearchColumn()
                .key(expectedColumnName)
                .type(expectedType)
                .xpath(dataType.getXsiType() + "." + expectedColumnName.toUpperCase())
                .elementName(dataType.getXsiType())
                .header(expectedDisplayName)
                .id(expectedColumnName.toUpperCase());
    }

}
