package org.nrg.testing.xnat.tests.performance;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.annotations.PerformanceTestPlugin;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.performance.XnatPerformanceTests;
import org.nrg.testing.xnat.performance.actions.*;
import org.nrg.testing.xnat.performance.charting.AveragedCumulativeTimeSeriesCharter;
import org.nrg.testing.xnat.performance.validator.PolynomialRegressionValidator;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.XnatDeployment;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.testng.annotations.Test;

@Test(groups = "performance", dataProvider = XnatPerformanceTests.DEPLOYMENTS_PROVIDER)
public class TestPerformanceProjectScaling extends XnatPerformanceTests {

    private static final Consumer<XnatInterface> CREATE_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project());
    private static final Consumer<XnatInterface> CREATE_PUBLIC_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project().accessibility(Accessibility.PUBLIC));
    private static final int NUM_ADMIN_PROJECTS = 750;
    private static final int NUM_ACCOUNTS_CREATING_PROJECTS = 750;
    private static final int NUM_PUBLIC_PROJECTS = 500;
    private static final int NUM_PROJECTS_TO_MAKE_PUBLIC = 40;
    private static final int NUM_SESSIONS_CREATE = 10000;
    private static final String EXTRA_DATATYPES_DESCRIPTION = "with 100 datatypes added";
    private static final String CREATE_PROJECTS_EXTRA_DATA_TYPE_ID = "create-projects-admin-100-extra-datatypes";
    private static final String DATA_TYPE_PLUGIN = "datatype-proliferation-1.0.0.jar";
    private static final String EXPT_ID_EXTENSION = "100-extra-datatypes";
    private static final Project EXPT_CREATE_PROJECT = new Project("CREATE_EXPT_TEST");
    private static final Subject EXPT_CREATE_SUBJECT = new Subject(EXPT_CREATE_PROJECT, "SUBJ_001");
    private static final RepeatedMonitorableAction EXPT_CREATE_TEST_EXTRA_TYPES = experimentCreateTest(true);
    private static final RepeatedMonitorableAction EXPT_CREATE_TEST_BASE_SCENARIO = experimentCreateTest(false);
    private static final RepeatedMonitorableAction EXPT_PROJ_LISTING_TEST_ADMIN_EXTRA = experimentListingRetrieveTest(false, true);
    private static final RepeatedMonitorableAction EXPT_PROJ_LISTING_TEST_ADMIN_BASE = experimentListingRetrieveTest(false, false);
    private static final RepeatedMonitorableAction EXPT_PROJ_LISTING_TEST_NONADMIN_EXTRA = experimentListingRetrieveTest(true, true);
    private static final RepeatedMonitorableAction EXPT_PROJ_LISTING_TEST_NONADMIN_BASE = experimentListingRetrieveTest(true, false);

    public void testCreateProjectsAsAdmin(XnatDeployment deployment) {
        testCreateProjects(
                "create-projects-admin",
                (test) -> test.title("Cumulative time for admin creating projects").compareTo(CREATE_PROJECTS_EXTRA_DATA_TYPE_ID, EXTRA_DATATYPES_DESCRIPTION, "100-types"),
                deployment
        );
    }

    @PerformanceTestPlugin(DATA_TYPE_PLUGIN)
    public void testCreateProjectsExtraDatatypes(XnatDeployment deployment) {
        testCreateProjects(CREATE_PROJECTS_EXTRA_DATA_TYPE_ID, (test) -> test.title("Cumulative time for admin creating projects " + EXTRA_DATATYPES_DESCRIPTION), deployment);
    }

    public void testCreateProjectsAsAdminAdditionalTypeRegistration(XnatDeployment deployment) {
        performanceScenario(deployment)
                .setup(
                        performanceStateHelper -> Stream.of(
                                        DataType.lookupAllKnownScanTypes(),
                                        DataType.lookupSessionTypesNotAddedByDefault(),
                                        Arrays.asList(DataType.SCID_RESEARCH, DataType.PITTSBURGH_SIDE_EFFECTS, DataType.UPDRS, DataType.YBOCS, DataType.YGTSS)
                                ).flatMap(Collection::stream)
                                .forEach(mainAdminInterface()::setupDataType)
                ).tests(
                        new RepeatedMonitorableAction("create-projects-admin-registered-types")
                                .title("Cumulative time for admin creating projects, extra data types enabled")
                                .actionDescription("Number of projects created")
                                .asUser(mainAdminUser)
                                .overallIterationCount(NUM_ADMIN_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    public void testCreateManyProjectsAsIndividualUsers(XnatDeployment deployment) {
        mainAdminInterface().disableSmtp();
        performanceScenario(deployment)
                .tests(
                        new RepeatedMonitorableAction("create-many-projects-one-per-user")
                                .title("Cumulative time for unique users each creating a single project")
                                .actionDescription("Number of projects created")
                                .withUserProvider(new SequentialUserProvider(NUM_ACCOUNTS_CREATING_PROJECTS))
                                .overallIterationCount(NUM_ACCOUNTS_CREATING_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    public void testPublicProjectScaling(XnatDeployment deployment) {
        final List<Project> projectsToMakePublic = IntStream.range(0, NUM_PROJECTS_TO_MAKE_PUBLIC)
                .mapToObj(ignored -> new Project())
                .collect(Collectors.toList());
        for (Project project : projectsToMakePublic) {
            mainInterface().createProject(project);
        }
        final Consumer<XnatInterface> publishAction = xnatInterface -> xnatInterface.updateAccessibility(projectsToMakePublic.remove(0), Accessibility.PUBLIC);
        performanceScenario(deployment)
                .tests(
                        new RepeatedMonitorableAction("create-many-public-projects")
                                .title("Cumulative public project creation time")
                                .actionDescription("Number of projects created")
                                .asUser(mainUser)
                                .overallIterationCount(NUM_PUBLIC_PROJECTS)
                                .performanceTestAction(CREATE_PUBLIC_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC),
                        new RepeatedMonitorableAction("make-projects-public")
                                .title("Cumulative time to make existing projects public")
                                .actionDescription("Number of projects modified")
                                .asUser(mainUser)
                                .overallIterationCount(NUM_PROJECTS_TO_MAKE_PUBLIC)
                                .actionsPerSnapshot(1)
                                .performanceTestAction(publishAction)
                                .validateUsing(PolynomialRegressionValidator.LINEAR)
                ).run();
    }

    public void testCreateSessionsNonadmin(XnatDeployment deployment) {
        mainInterface().createProject(EXPT_CREATE_PROJECT);
        performanceScenario(deployment).tests(
                EXPT_CREATE_TEST_BASE_SCENARIO,
                EXPT_PROJ_LISTING_TEST_NONADMIN_BASE,
                EXPT_PROJ_LISTING_TEST_ADMIN_BASE
        ).run();
    }

    @PerformanceTestPlugin(DATA_TYPE_PLUGIN)
    public void testCreateSessionsNonadminExtraDatatypes(XnatDeployment deployment) {
        mainInterface().createProject(EXPT_CREATE_PROJECT);
        performanceScenario(deployment).tests(
                EXPT_CREATE_TEST_EXTRA_TYPES,
                EXPT_PROJ_LISTING_TEST_NONADMIN_EXTRA,
                EXPT_PROJ_LISTING_TEST_ADMIN_EXTRA
        ).run();
    }

    private void testCreateProjects(String id, Consumer<RepeatedMonitorableAction> testCustomization, XnatDeployment deployment) {
        final RepeatedMonitorableAction test = new RepeatedMonitorableAction(id)
                .actionDescription("Number of projects created")
                .asUser(mainAdminUser)
                .overallIterationCount(NUM_ADMIN_PROJECTS)
                .performanceTestAction(CREATE_PROJECT_ACTION)
                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC);
        testCustomization.accept(test);
        performanceScenario(deployment).tests(test).run();
    }

    private static RepeatedMonitorableAction experimentCreateTest(boolean extraTypes) {
        final List<String> components = new ArrayList<>();
        components.add("create-empty-experiments");
        if (extraTypes) {
            components.add(EXPT_ID_EXTENSION);
        }
        final String testName = StringUtils.join(components, "-");

        final RepeatedMonitorableAction createTest = new RepeatedMonitorableAction(testName)
                .actionDescription("Number of sessions created")
                .asUser(Settings.DEFAULT_XNAT_CONFIG.getMainUser())
                .overallIterationCount(NUM_SESSIONS_CREATE)
                .actionsPerSnapshot(50)
                .performanceTestAction(xnatInterface -> xnatInterface.createSubjectAssessor(new MRSession(EXPT_CREATE_PROJECT, EXPT_CREATE_SUBJECT)))
                .validateUsing(PolynomialRegressionValidator.LINEAR)
                .title("Cumulative time for non-admin creating empty MR Sessions under 1 subject " + (extraTypes ? EXTRA_DATATYPES_DESCRIPTION : ""));
        if (!extraTypes) {
            // noinspection ConstantConditions
            createTest.compareTo(EXPT_CREATE_TEST_EXTRA_TYPES, EXTRA_DATATYPES_DESCRIPTION, "100-types");
        }
        return createTest;
    }

    private static RepeatedMonitorableAction experimentListingRetrieveTest(boolean nonadmin, boolean extraTypes) {
        final List<String> components = new ArrayList<>();
        components.add("load-experiment-restlet");
        if (!nonadmin) {
            components.add("admin");
        }
        if (extraTypes) {
            components.add(EXPT_ID_EXTENSION);
        }
        final String testName = StringUtils.join(components, "-");
        final String chartTitle = String.format(
                "Average time for %s retrieving project-level experiment restlet listing for %d sessions %s",
                nonadmin ? "non-admin" : "admin",
                NUM_SESSIONS_CREATE,
                extraTypes ? EXTRA_DATATYPES_DESCRIPTION : ""
        );

        final RepeatedMonitorableAction action =  new RepeatedMonitorableAction(testName)
                .asUser(nonadmin ? Settings.DEFAULT_XNAT_CONFIG.getMainUser() : Settings.DEFAULT_XNAT_CONFIG.getMainAdminUser())
                .overallIterationCount(10)
                .actionsPerSnapshot(1)
                .performanceTestAction(xnatInterface -> xnatInterface.jsonQuery().get(xnatInterface.projectExperimentsUrl(EXPT_CREATE_PROJECT)))
                .validateUsing(PolynomialRegressionValidator.LINEAR)
                .chartUsing(AveragedCumulativeTimeSeriesCharter.INSTANCE)
                .title(chartTitle);
        if (nonadmin && !extraTypes) {
            action.compareTo(EXPT_PROJ_LISTING_TEST_ADMIN_EXTRA, "as admin " + EXTRA_DATATYPES_DESCRIPTION);
            action.compareTo(EXPT_PROJ_LISTING_TEST_ADMIN_BASE,  "as admin");
            action.compareTo(EXPT_PROJ_LISTING_TEST_NONADMIN_EXTRA, EXTRA_DATATYPES_DESCRIPTION);
        }
        return action;
    }

}
