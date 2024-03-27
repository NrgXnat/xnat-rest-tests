package org.nrg.testing.xnat.tests.performance;

import org.nrg.testing.annotations.PerformanceTestPlugin;
import org.nrg.testing.xnat.performance.XnatPerformanceTests;
import org.nrg.testing.xnat.performance.actions.*;
import org.nrg.testing.xnat.performance.validator.PolynomialRegressionValidator;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestPerformanceProjectScaling extends XnatPerformanceTests {

    private static final Consumer<XnatInterface> CREATE_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project());
    private static final Consumer<XnatInterface> CREATE_PUBLIC_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project().accessibility(Accessibility.PUBLIC));
    private static final int NUM_ADMIN_PROJECTS = 750;
    private static final int NUM_ACCOUNTS_CREATING_PROJECTS = 750;
    private static final int NUM_PUBLIC_PROJECTS = 500;
    private static final int NUM_PROJECTS_TO_MAKE_PUBLIC = 40;
    private static final String EXTRA_DATA_TYPE_ID = "create-projects-admin-100-extra-datatypes";

    public void testCreateProjectsAsAdmin() {
        performanceScenario()
                .tests(
                        new RepeatedMonitorableAction("create-projects-admin")
                                .title("Cumulative time for admin creating projects")
                                .actionDescription("Number of projects created")
                                .asUser(mainAdminUser)
                                .overallIterationCount(NUM_ADMIN_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                                .compareTo(EXTRA_DATA_TYPE_ID, "with 100 additional datatypes added", "100-types")
                ).run();
    }

    @PerformanceTestPlugin("datatype-proliferation-1.0.0.jar")
    public void testCreateProjectsExtraDatatypes() {
        performanceScenario()
                .tests(
                        new RepeatedMonitorableAction(EXTRA_DATA_TYPE_ID)
                                .title("Cumulative time for admin creating projects with 100 extra data types added")
                                .actionDescription("Number of projects created")
                                .asUser(mainAdminUser)
                                .overallIterationCount(NUM_ADMIN_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    public void testCreateProjectsAsAdminAdditionalTypeRegistration() {
        performanceScenario()
                .setup(
                        performanceStateHelper -> {
                            Stream.of(
                                            DataType.lookupAllKnownScanTypes(),
                                            DataType.lookupSessionTypesNotAddedByDefault(),
                                            Arrays.asList(DataType.SCID_RESEARCH, DataType.PITTSBURGH_SIDE_EFFECTS, DataType.UPDRS, DataType.YBOCS, DataType.YGTSS)
                                    ).flatMap(Collection::stream)
                                    .forEach(mainAdminInterface()::setupDataType);
                        }
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

    public void testCreateManyProjectsAsIndividualUsers() {
        mainAdminInterface().disableSmtp();
        performanceScenario()
                .tests(
                        new RepeatedMonitorableAction("create-many-projects-one-per-user")
                                .title("Cumulative time for unique users each creating a single project")
                                .actionDescription("Number of projects created")
                                .withUserProvider(new SequentialUserProvider(createGenericUsers(NUM_ACCOUNTS_CREATING_PROJECTS)))
                                .overallIterationCount(NUM_ACCOUNTS_CREATING_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    public void testPublicProjectScaling() {
        final List<Project> projectsToMakePublic = IntStream.range(0, NUM_PROJECTS_TO_MAKE_PUBLIC)
                .mapToObj(ignored -> new Project())
                .collect(Collectors.toList());
        for (Project project : projectsToMakePublic) {
            mainInterface().createProject(project);
        }
        final Consumer<XnatInterface> publishAction = xnatInterface -> xnatInterface.updateAccessibility(projectsToMakePublic.remove(0), Accessibility.PUBLIC);
        performanceScenario()
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

}
