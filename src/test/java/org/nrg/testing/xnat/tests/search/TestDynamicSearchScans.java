package org.nrg.testing.xnat.tests.search;

import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;

public class TestDynamicSearchScans extends BaseDynamicSearchTest {

    private static final String ID = "ID";
    private static final String URI = "URI";
    private static final String TYPE = "type";
    private static final String QUALITY = "quality";
    private static final String XSI_TYPE = "xsiType";
    private static final String NOTE = "note";
    private static final String SERIES_DESCRIPTION = "series_description";
    private static final String IMAGE_SCAN_ID = "xnat_imagescandata_id";
    private static final String MR_SCAN_IMAGE_SCAN_ID = "xnat:mrscandata/xnat_imagescandata_id";

    private static final Function<Scan, Map<String, String>> mapId =
            mapField(ID, Scan::getId);

    private static final Function<Scan, Map<String, String>> mapUri =
            mapField(URI, (scan) -> String.format("/data/experiments/" + scan.getSession().getAccessionNumber() + "/scans/" + scan.getId()));

    private static final Function<Scan, Map<String, String>> mapType =
            mapField(TYPE, (scan) -> nullFallback(scan.getType()));

    private static final Function<Scan, Map<String, String>> mapQuality =
            mapField(QUALITY, (scan) -> nullFallback(scan.getQuality()));

    private static final Function<Scan, Map<String, String>> mapXsiType =
            mapField(XSI_TYPE, (scan) -> nullFallback(scan.getXsiType()));

    private static final Function<Scan, Map<String, String>> mapNote =
            mapField(NOTE, (scan) -> nullFallback(scan.getNote()));

    private static final Function<Scan, Map<String, String>> mapSeriesDescription =
            mapField(SERIES_DESCRIPTION, (scan) -> nullFallback(scan.getSeriesDescription()));

    private static final Function<Scan, Map<String, String>> minimalMapping =
            composeFunctions(
                    mapId, mapUri
            );

    private static final Function<Scan, Map<String, String>> expandedMapping =
            composeFunctions(
                    minimalMapping,
                    mapType,
                    mapQuality,
                    mapXsiType,
                    mapNote,
                    mapSeriesDescription
            );

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicScanSearchDefaultColumns() {
        new DynamicSearchResultValidator<Scan>()
                .uniquenessKey(URI)
                .keysToIgnore(Collections.singletonList(IMAGE_SCAN_ID))
                .objectMappingFunction(expandedMapping)
                .validateDynamicSearchResponse(
                        queryScans(new HashMap<>()),
                        Arrays.asList(t1Timeless, t1At202020, t1At080808, t2At111111, otherPetScan),
                        Collections.singletonList(t1At012345)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicScanSearchMinimalColumns() {
        new DynamicSearchResultValidator<Scan>()
                .uniquenessKey(URI)
                .keysToIgnore(Collections.singletonList(IMAGE_SCAN_ID))
                .objectMappingFunction(minimalMapping)
                .validateDynamicSearchResponse(
                        queryScans(mapWithColumns(ID)),
                        Arrays.asList(t1Timeless, t1At202020, t1At080808, t2At111111, otherPetScan),
                        Collections.singletonList(t1At012345)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicScanSearchMrScanFilter() {
        new DynamicSearchResultValidator<Scan>()
                .uniquenessKey(URI)
                .keysToIgnore(Arrays.asList(IMAGE_SCAN_ID, MR_SCAN_IMAGE_SCAN_ID))
                .objectMappingFunction(expandedMapping)
                .validateDynamicSearchResponse(
                        queryScans(Collections.singletonMap(XSI_TYPE, DataType.MR_SCAN.getXsiType())),
                        Arrays.asList(t1Timeless, t1At202020, t1At080808, t2At111111),
                        Arrays.asList(otherPetScan, t1At012345)
                );
    }

    private SearchResponse queryScans(Map<String, String> queryParams) {
        return queryScans(queryParams, mainUser);
    }

    private SearchResponse queryScans(Map<String, String> queryParams, User authUser) {
        return queryDynamicSearchEndpoint(
                formatRestUrl("experiments", mrSessionNoWeight.getAccessionNumber(), "scans"),
                queryParams,
                authUser
        );
    }

    private static Function<Scan, Map<String, String>> mapField(String fieldName, Function<Scan, String> fieldSerializer) {
        return (scan) -> Collections.singletonMap(fieldName, fieldSerializer.apply(scan));
    }

}
