package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.dicom.SimpleInjectibleDicomValidation;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.prearchive.SessionData;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.nrg.testing.TestGroups.ANONYMIZATION;

@Test(groups = ANONYMIZATION)
public class BaseAnonymizationTest extends BaseXnatRestTest {

    protected Project anonProject = new Project();
    protected final File anonData = TestData.ANON_2.toFile();
    protected final List<DicomScpReceiver> createdReceivers = new ArrayList<>();
    protected boolean projectCreated = false;
    protected static final String RETAIN_PRIVATE_TAGS = "retainPrivateTags";

    @BeforeMethod(groups = ANONYMIZATION)
    protected void createProject() {
        anonProject = new Project();
        mainInterface().createProject(anonProject);
        projectCreated = true;
        mainInterface().regenerateUserSession(); // hack for XNAT-5187
        mainAdminInterface().disableProjectDicomRoutingConfig();
        mainAdminInterface().disableSubjectDicomRoutingConfig();
        mainAdminInterface().disableSessionDicomRoutingConfig();
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
    }

    @AfterMethod(groups = ANONYMIZATION, alwaysRun = true)
    protected void clean() {
        restDriver.clearPrearchiveSessions(mainUser, anonProject);
        if (projectCreated) {
            mainInterface().deleteProject(anonProject);
            projectCreated = false;
        }
    }

    @AfterClass(groups = ANONYMIZATION, alwaysRun = true)
    protected void resetAnon() {
        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(restDriver.getDefaultXnatAnonScript());
        for (DicomScpReceiver dicomScpReceiver : createdReceivers) {
            mainAdminInterface().deleteDicomScpReceiver(dicomScpReceiver);
        }
    }

    protected DicomScpReceiver newDefaultReceiver() {
        return new DicomScpReceiver()
                .host(Settings.DICOM_HOST)
                .aeTitle(RandomHelper.randomID(10))
                .port(Settings.DICOM_PORT);
    }

    protected class BasicAnonymizationTest extends GenericAnonymizationTest<BasicAnonymizationTest> {
        protected DicomEditVersion dicomEditVersion = DicomEditVersion.DE_6;
        protected String scriptName;
        protected final List<ScriptValidation> scriptContainer = new ArrayList<>();

        BasicAnonymizationTest() {
            withSetup(() -> {
                final AnonScript script = XnatObjectUtils.anonScriptFromFile(dicomEditVersion, scriptName);
                mainInterface().setProjectAnonScript(anonProject, script);
                mainAdminInterface().disableSiteAnonScript();
            });
            withEnabledScripts(scriptContainer);
        }

        BasicAnonymizationTest(String requiredComponent, String... additionalComponents) {
            this();
            scriptName = Paths.get(requiredComponent, additionalComponents).toString();
        }

        BasicAnonymizationTest withDicomEditVersion(DicomEditVersion dicomEditVersion) {
            this.dicomEditVersion = dicomEditVersion;
            return this;
        }

        BasicAnonymizationTest withScriptName(String scriptName) {
            this.scriptName = scriptName;
            return this;
        }

        BasicAnonymizationTest withData(TestData testData) {
            this.testData = testData.toFile();
            return this;
        }

        BasicAnonymizationTest withData(LocallyCacheableDicomTransformation dicomTransformation) {
            testData = dicomTransformation.build().locateOverallZip().toFile();
            return this;
        }

        BasicAnonymizationTest withValidation(ScriptValidation scriptValidation) {
            scriptContainer.clear();
            scriptContainer.add(scriptValidation);
            return this;
        }

        BasicAnonymizationTest withValidation(Consumer<RootDicomObject> validationMethod) {
            return withValidation(
                    new SimpleInjectibleDicomValidation() {
                        @Override
                        protected void validation(RootDicomObject root) {
                            validationMethod.accept(root);
                        }
                    }
            );
        }
    }

    protected class SiteAnonReceiverTest extends BasicAnonymizationTest {
        private DicomScpReceiver scpReceiverSpecification;

        SiteAnonReceiverTest(String scriptName) {
            super(scriptName);
            withSetup(setup());
            withData(TestData.ANON_2);
            withUpload(uploadWithCstore());
        }

        SiteAnonReceiverTest() {
            this(null);
        }

        @Override
        SiteAnonReceiverTest withData(TestData testData) {
            this.testData = testData.toDirectory();
            return this;
        }

        @Override
        SiteAnonReceiverTest withData(LocallyCacheableDicomTransformation dicomTransformation) {
            testData = dicomTransformation.build().locateBaseDirForOnlyTransformation().toFile();
            return this;
        }

        SiteAnonReceiverTest withReceiverSpecification(DicomScpReceiver scpReceiverSpecification) {
            this.scpReceiverSpecification = scpReceiverSpecification;
            return this;
        }

        protected Supplier<ImagingSession> uploadWithCstore() {
            return () -> {
                new XnatCStore(scpReceiverSpecification).data(testData).sendDICOMToProject(anonProject);
                if (scpReceiverSpecification.isDirectArchive()) {
                    restDriver.waitForDirectArchiveEmpty(mainUser, anonProject, 120);
                } else {
                    final SessionData prearcSession = mainInterface().expectSinglePrearchiveResultForProject(anonProject);
                    mainInterface().rebuildSession(prearcSession, false);
                    mainInterface().archiveSession(prearcSession);
                }
                return mainInterface().readProject(anonProject.getId()).getSubjects().get(0).getSessions().get(0);
            };
        }

        Runnable setup() {
            return () -> {
                mainAdminInterface().createDicomScpReceiver(scpReceiverSpecification);
                createdReceivers.add(scpReceiverSpecification);
                final AnonScript script = XnatObjectUtils.anonScriptFromFile(dicomEditVersion, scriptName);
                mainAdminInterface().enableSiteAnonScript();
                mainAdminInterface().setSiteAnonScript(script);
            };
        }
    }

    @SuppressWarnings("unchecked")
    protected class GenericAnonymizationTest<X extends GenericAnonymizationTest<X>> {
        private Runnable setupStep;
        protected File testData = anonData;
        private List<ScriptValidation> enabledScripts;
        private List<ScriptValidation> disabledScripts;
        protected Supplier<ImagingSession> uploadStep = defaultUploadStep();

        X withSetup(Runnable setupStep) {
            this.setupStep = setupStep;
            return (X) this;
        }

        X withUpload(Supplier<ImagingSession> uploadStep) {
            this.uploadStep = uploadStep;
            return (X) this;
        }

        X withEnabledScripts(List<ScriptValidation> enabledScripts) {
            this.enabledScripts = enabledScripts;
            return (X) this;
        }

        X withDisabledScripts(List<ScriptValidation> disabledScripts) {
            this.disabledScripts = disabledScripts;
            return (X) this;
        }

        void validate(ImagingSession session, List<ScriptValidation> enabled, List<ScriptValidation> disabled) {
            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, anonProject, session.getSubject(), session);
            if (enabled != null) {
                for (ScriptValidation script : enabled) {
                    script.validateScriptRan(dicom);
                }
            }
            if (disabled != null) {
                for (ScriptValidation script : disabled) {
                    script.validateScriptDidntRun(dicom);
                }
            }
        }

        protected Supplier<ImagingSession> defaultUploadStep() {
            return () -> {
                final Subject subject = new Subject(anonProject);
                final ImagingSession session = new ImagingSession(anonProject, subject);
                session.extension(new SessionImportExtension(session, testData));
                mainInterface().createSubject(subject);
                return session;
            };
        }

        ImagingSession run() {
            setupStep.run();
            final ImagingSession session = uploadStep.get();
            validate(session, enabledScripts, disabledScripts);
            return session;
        }
    }

}
