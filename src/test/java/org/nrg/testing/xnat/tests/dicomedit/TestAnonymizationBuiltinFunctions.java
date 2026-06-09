package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;

import java.util.Arrays;
import java.util.function.Consumer;
import org.nrg.xnat.versions.Xnat_1_9_0;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;
import org.nrg.testing.annotations.MutatesServerState;

@TestRequires(admin = true, data = TestData.ANON_2)
@MutatesServerState
public class TestAnonymizationBuiltinFunctions extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> STRING_FUNCTION_VALIDATOR = (root) ->
            root.putValueEqualCheck("(0010,1000)", "mr_PDW_TSE_01204567_102030");
    private static final Consumer<RootDicomObject> URL_ENCODE_VALIDATOR = (root) ->
            root.putValueEqualCheck("(0010,1000)", "0%3D1");
    private static final Consumer<RootDicomObject> MATCH_VALIDATOR = (root) ->
            root.putValueEqualCheck("(0010,1000)", "b");

    public void testStringFunctionsDE4() {
        new BasicAnonymizationTest("stringFunctions.das")
                .withDicomEditVersion(DE_4)
                .withValidation(STRING_FUNCTION_VALIDATOR)
                .run();
    }

    public void testStringFunctionsDE6() {
        new BasicAnonymizationTest("stringFunctions.das")
                .withValidation(STRING_FUNCTION_VALIDATOR)
                .run();
    }

    public void testUrlEncodeDE4() {
        new BasicAnonymizationTest("urlEncode.das")
                .withDicomEditVersion(DE_4)
                .withValidation(URL_ENCODE_VALIDATOR)
                .run();
    }

    @ExpectedFailure(jiraIssue = "DE-7")
    public void testUrlEncodeDE6() {
        new BasicAnonymizationTest("urlEncode.das")
                .withValidation(URL_ENCODE_VALIDATOR)
                .run();
    }

    public void testGetURLDE4() {
        new GetUrlTest(DE_4).run();
    }

    public void testGetURLDE6() {
        new GetUrlTest(DE_6).run();
    }

    public void testMatchDE4() {
        new BasicAnonymizationTest("match.das")
                .withDicomEditVersion(DE_4)
                .withValidation(MATCH_VALIDATOR)
                .run();
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testMatchDE6() {
        new BasicAnonymizationTest("match.das")
                .withValidation(MATCH_VALIDATOR)
                .run();
    }

    @AddedIn(Xnat_1_8_1.class)
    public void testIsMatch() {
        new BasicAnonymizationTest("isMatch.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,1010)", "true");
                    root.putValueEqualCheck("(0008,1040)", "false");
                }).run();
    }

    @ExpectedFailure(jiraIssue = "DE-52")
    @AddedIn(Xnat_1_8_1.class)
    public void testSet() {
        new BasicAnonymizationTest("set.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0045,0010)", "PCI");
                    root.putValueEqualCheck("(0045,1045)", "PRIVATEVALUE");
                    root.putValueEqualCheck("(0028,0008)", "10", VR.IS);

                    root.putSequenceCheck(
                            "(0020,9222)",
                            (dimensionIndexSeqItem0) -> {
                                dimensionIndexSeqItem0.putValueEqualCheck("(0020,9164)", "1.2.3.4");
                                dimensionIndexSeqItem0.putValueEqualCheck("(0045,0010)", "PCI");
                            }, (dimensionIndexSeqItem1) -> {
                                dimensionIndexSeqItem1.putValueEqualCheck("(0020,9164)", "1.3.46.670589.11.5730.5.0.3224.2010071913175879000");
                                dimensionIndexSeqItem1.putNonexistenceChecks("(0045,0010)");
                            }
                    );
                }).run();
    }

    @AddedIn(Xnat_1_8_1.class)
    public void testDeleteFunction() {
        new BasicAnonymizationTest("deleteFunction.das")
                .withValidation((root) -> {
                    root.putNonexistenceChecks("(0008,0080)", "(0008,0081)", "(0008,1110)");

                    root.putSequenceCheck("(0018,9112)", (mrTimingSeqItem) -> {
                        mrTimingSeqItem.putNonexistenceChecks("(0018,0080)");
                        mrTimingSeqItem.putNonexistenceChecks("(0018,9176)");

                        mrTimingSeqItem.putSequenceCheck(
                                "(0018,9239)",
                                (specificAbsorptionItem0) -> {
                                    specificAbsorptionItem0.putValueEqualCheck("(0018,9179)", "IEC_LOCAL");
                                    specificAbsorptionItem0.putValueEqualCheck("(0018,9181)", "10");
                                }, (specificAbsorptionItem1) -> {
                                    specificAbsorptionItem1.putValueEqualCheck("(0018,9179)", "IEC_WHOLE_BODY");
                                    specificAbsorptionItem1.putNonexistenceChecks("(0018,9181)");
                                }
                        );
                    });

                    root.putNonexistenceChecks("(2001,1019)", "(2001,101f)");

                    root.putSequenceCheck("(2001,105f)", (privateSeqItem0) -> {
                        privateSeqItem0.putValueEqualCheck("(2001,1033)", "AP");
                        privateSeqItem0.putValueEqualCheck("(2005,1081)", "FH");
                        privateSeqItem0.putNonexistenceChecks("(2005,10a3)");
                    });
                }).run();
    }

    @AddedIn(Xnat_1_9_0.class)
    public void testConstantLogicFunctions() {
        new BasicAnonymizationTest("constantLogicFunctions.das")
                .withData(TestData.SIMPLE_PET)
                .withValidation((root) -> {
                    for (String element : Arrays.asList("(0018,9758)", "(0018,9759)", "(0018,9760)", "(0018,9761)")) {
                        root.putValueEqualCheck(element, "YEP");
                    }
                }).run();
    }

    private class GetUrlTest extends BasicAnonymizationTest {
        private GetUrlTest(DicomEditVersion dicomEditVersion) {
            super();
            withSetup(() -> {
                final AnonScript script = XnatObjectUtils.anonScriptFromFile(dicomEditVersion, "getURL.das");
                script.setContents(script.getContents().replace(
                        "%URL%",
                        formatXapiUrl("siteConfig", "siteId")
                ));
                mainInterface().setProjectAnonScript(anonProject, script);
                mainAdminInterface().disableSiteAnonScript();
            });
            withValidation((root) ->
                    root.putValueEqualCheck("(0010,1000)", mainAdminInterface().readSiteConfigPreference("siteId"))
            );
        }
    }

}
