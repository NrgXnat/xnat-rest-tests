package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.nrg.testing.TestGroups.SEARCH;

@Test(groups = SEARCH)
public class BaseSearchTest extends BaseXnatRestTest {

    protected File searchFile(String file) {
        return getDataFile(Paths.get("search", file));
    }

}
