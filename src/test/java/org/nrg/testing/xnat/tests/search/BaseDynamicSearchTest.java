package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.users.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;

public class BaseDynamicSearchTest extends BaseSearchPermissionsTest {

    protected static final String IMAGE_SESSION_MODALITY = "xnat:imageSessionData/modality";

    protected static class DynamicSearchResultValidator<X> {
        private List<String> uniquenessCompositeKeys;
        private Function<X, List<Map<String, String>>> multiObjectMappingFunction;
        private List<String> keysToIgnore = new ArrayList<>();

        protected DynamicSearchResultValidator<X> uniquenessKey(String key) {
            return uniquenessCompositeKeys(Collections.singletonList(key));
        }

        protected DynamicSearchResultValidator<X> uniquenessCompositeKeys(List<String> keys) {
            uniquenessCompositeKeys = keys;
            return this;
        }

        protected DynamicSearchResultValidator<X> objectMappingFunction(Function<X, Map<String, String>> function) {
            return multiObjectMappingFunction(
                    (object) -> Collections.singletonList(function.apply(object))
            );
        }

        protected DynamicSearchResultValidator<X> multiObjectMappingFunction(Function<X, List<Map<String, String>>> function) {
            multiObjectMappingFunction = function;
            return this;
        }

        protected DynamicSearchResultValidator<X> keysToIgnore(List<String> keys) {
            keysToIgnore = keys;
            return this;
        }

        protected void validateDynamicSearchResponse(SearchResponse searchResponse, List<? extends X> objectsToAssertIncluded, List<? extends X> objectsToAssertExcluded) {
            for (String keyToIgnore : keysToIgnore) {
                for (Map<String, String> searchResponseObject : searchResponse.getResult()) {
                    assertTrue(searchResponseObject.containsKey(keyToIgnore));
                    searchResponseObject.remove(keyToIgnore);
                }
            }

            final List<Map<String, String>> expectedMapsToAssertIncluded = transformObjectsToMap(objectsToAssertIncluded);

            for (Map<String, String> expectedMap : expectedMapsToAssertIncluded) {
                final Predicate<Map<String, String>> mapChecker = mapMatchesExpected(expectedMap);
                assertEquals(
                        expectedMap,
                        searchResponse
                                .getResult()
                                .stream()
                                .filter(mapChecker)
                                .findFirst()
                                .orElseThrow(RuntimeException::new)
                );
            }
            for (Map<String, String> queriedObject : transformObjectsToMap(objectsToAssertExcluded)) {
                assertFalse(matchExists(queriedObject, searchResponse.getResult()));
            }
        }

        protected List<Map<String, String>> transformObjectsToMap(List<? extends X> objects) {
            return objects
                    .stream()
                    .map(multiObjectMappingFunction)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        protected Predicate<Map<String, String>> mapMatchesExpected(Map<String, String> expectedMap) {
            return (candidateMap) -> uniquenessCompositeKeys
                    .stream()
                    .allMatch(key -> nullFallback(expectedMap.get(key)).equals(nullFallback(candidateMap.get(key))));
        }

        protected boolean matchExists(Map<String, String> queriedObject, List<SearchRow> actualMaps) {
            return actualMaps
                    .stream()
                    .anyMatch(actualMap -> mapMatchesExpected(queriedObject).test(actualMap));
        }

        protected List<String> getUniquenessCompositeKeys() {
            return uniquenessCompositeKeys;
        }

        protected Function<X, List<Map<String, String>>> getMultiObjectMappingFunction() {
            return multiObjectMappingFunction;
        }

        protected List<String> getKeysToIgnore() {
            return keysToIgnore;
        }
    }

    protected SearchResponse queryDynamicSearchEndpoint(String url, Map<String, String> queryParams, User authUser) {
        return interfaceFor(authUser)
                .jsonQuery()
                .queryParams(queryParams)
                .get(url)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract()
                .jsonPath()
                .getObject("ResultSet", SearchResponse.class);
    }

    protected Map<String, String> mapWithColumns(String... columns) {
        final Map<String, String> asMap =  new HashMap<>();
        asMap.put("columns", String.join(",", columns));
        return asMap;
    }

    protected static String nullFallback(String value) {
        return StringUtils.defaultIfEmpty(value, "");
    }

    protected static String serializeLocalDate(LocalDate localDate) {
        return (localDate != null) ? TimeUtils.UNAMBIGUOUS_DATE.format(localDate) : "";
    }

    @SafeVarargs
    protected static <X> Function<X, Map<String, String>> composeFunctions(Function<X, Map<String, String>>... mappers) {
        return (object) -> {
            final Map<String, String> result = new HashMap<>();
            for (Function<X, Map<String, String>> mapper : mappers) {
                result.putAll(mapper.apply(object));
            }
            return result;
        };
    }

}
