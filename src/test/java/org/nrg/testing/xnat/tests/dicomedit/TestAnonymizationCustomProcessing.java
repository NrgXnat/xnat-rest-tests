package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.AlterPixelsBasicScript;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.LateAnonClientLikeScript;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_8_9_2;

import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_9_2.class)
public class TestAnonymizationCustomProcessing extends BaseAnonymizationTest {

    private static final LocallyCacheableDicomTransformation ANON_DATA_LATE_ELEMENTS = defineAnonDataWithExtraLateElements();

    public void testCustomProcessingSiteAnonAllPrivateElements() {
        new SiteAnonReceiverTest("post00324000AnonAllPrivate.das")
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true))
                .withData(ANON_DATA_LATE_ELEMENTS)
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
                    root.putNonexistenceChecks("(0008,9205)", "(0015,1020)", "(0015,1021)", "(0044,0001)");
                    root.putValueEqualCheck("(0044,0002)", "YES");
                    root.putWildcardedNonexistenceCheck("(2001,XXXX)");
                    root.putWildcardedNonexistenceCheck("(2005,XXXX)");
                    root.putValueEqualCheck("(2050,0020)", "IDENTITY");

                    root.putSequenceCheck(
                            "(5200,9229)",
                            (item) -> {
                                item.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
                                item.putWildcardedNonexistenceCheck("(2005,XXXX)");
                            }
                    );

                    root.putNonexistenceChecks(0x97531050, 0x97531051); // specified like this because we need integer overflow...

                    root.putSequenceCheck(0xFFFAFFFA, (singleItem) -> {
                        singleItem.putValueEqualCheck("(0400,0005)", "1000");
                        singleItem.putNonexistenceChecks("(0400,0110)");
                        singleItem.putValueEqualCheck("(0400,0305)", "CMS_TSP");
                    });
                }).run();
    }

    public void testCustomProcessingSiteAnonTargetedPrivateElements() {
        new SiteAnonReceiverTest("post00324000AnonTargetedPrivateElements.das")
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true))
                .withData(ANON_DATA_LATE_ELEMENTS)
                .withValidation(lateAnonTargetedPrivateElementChecks(true))
                .run();
    }

    public void testCustomProcessingSiteAnonTargetedPrivateElementsDE4() {
        new SiteAnonReceiverTest("post00324000AnonTargetedPrivateElements.das")
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true))
                .withDicomEditVersion(DE_4)
                .withData(ANON_DATA_LATE_ELEMENTS)
                .withValidation(lateAnonTargetedPrivateElementChecks(false))
                .run();
    }

    @TestRequires(data = TestData.SAMPLE_1)
    public void testCustomProcessingSiteAnonPixelEdit() {
        new SiteAnonReceiverTest("alterPixelsBasic.das")
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true))
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsBasicScript())
                .run();
    }

    public void testCustomProcessingSiteAnonClientScript() {
        new ClientLikeScriptTest()
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true).directArchive(false))
                .run();
    }

    public void testCustomProcessingDirectArchiveSiteAnonClientScript() {
        new ClientLikeScriptTest()
                .withReceiverSpecification(newDefaultReceiver().customProcessing(true).directArchive(true))
                .run();
    }

    private class ClientLikeScriptTest extends SiteAnonReceiverTest {

        ClientLikeScriptTest() {
            super("clientLikeScript.das");
            withDicomEditVersion(DE_4);
            withValidation(new LateAnonClientLikeScript());
            withUpload(() -> {
                uploadWithCstore().get(); // perform upload as in superclass, but let's check the labels too
                final MRSession session = new MRSession(anonProject, new Subject(anonProject, "BRAIN"), "2D");
                ((LateAnonClientLikeScript) scriptContainer.get(0)).session(session);
                return session;
            });
        }

        @Override
        Runnable setup() {
            return () -> {
                mainAdminInterface().setSubjectDicomRoutingConfig("(0018,0015):(.*)");
                mainAdminInterface().setSessionDicomRoutingConfig("(0018,0023):(.*)");
                super.setup().run();
            };
        }

    }

    private static Consumer<RootDicomObject> lateAnonTargetedPrivateElementChecks(boolean checkSequence) {
        return (root) -> {
            root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
            root.putNonexistenceChecks("(0008,9205)", "(0044,0001)");
            root.putValueEqualCheck("(0015,1020)", "ABC");
            root.putValueEqualCheck("(0015,1021)", "XYZ");
            root.putValueEqualCheck("(0044,0002)", "YES");

            root.putValueEqualCheck("(2001,100c)", "N");
            root.putValueEqualCheck("(2001,100e)", "N");
            root.putValueEqualCheck("(2005,1038)", "N");
            root.putNonexistenceChecks("(2005,1039)", "(2005,10c0)");
            root.putValueEqualCheck("(2005,10a9)", "2D");

            root.putValueEqualCheck("(2050,0020)", "IDENTITY");

            root.putValueEqualCheck(0x97531050, "blah blah"); // specified like this because we need integer overflow...
            root.putNonexistenceChecks(0x97531051);

            if (checkSequence) {
                root.putSequenceCheck(0xFFFAFFFA, (singleItem) -> {
                    singleItem.putValueEqualCheck("(0400,0005)", "1000");
                    singleItem.putNonexistenceChecks("(0400,0110)");
                    singleItem.putValueEqualCheck("(0400,0305)", "CMS_TSP");
                });
            }
        };
    }

    private static LocallyCacheableDicomTransformation defineAnonDataWithExtraLateElements() {
        final String privateCreatorId = "Anonymization tests";
        return new LocallyCacheableDicomTransformation("anon2-extra-late-elements")
                .data(TestData.ANON_2)
                .simpleTransform(
                        TransformFunction.simple((dicom) -> {
                            dicom.getDataset().setString(privateCreatorId, 0x00151020, VR.LO, "ABC");
                            dicom.getDataset().setString(privateCreatorId, 0x00151021, VR.LO, "XYZ");
                            dicom.getDataset().setString(privateCreatorId, 0x97531050, VR.LO, "blah blah");
                            dicom.getDataset().setString(privateCreatorId, 0x97531051, VR.LO, "blah blah again");
                            final Sequence sequence = dicom.getDataset().newSequence(Tag.DigitalSignaturesSequence, 1);
                            final Attributes sequenceItem = new Attributes();
                            sequence.add(sequenceItem);
                            sequenceItem.setInt(Tag.MACIDNumber, VR.US, 1000);
                            sequenceItem.setString(Tag.CertificateType, VR.CS, "X509_1993_SIG");
                            sequenceItem.setString(Tag.CertifiedTimestampType, VR.CS, "CMS_TSP");
                        })
                );
    }

}
