package org.nrg.testing.xnat.tests.performance;

import org.nrg.testing.xnat.performance.XnatPerformanceTests;
import org.nrg.testing.xnat.performance.actions.*;
import org.nrg.testing.xnat.performance.validator.PolynomialRegressionValidator;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestPerformanceProjectScaling extends XnatPerformanceTests {

    private static final Consumer<XnatInterface> CREATE_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project());
    private static final Consumer<XnatInterface> CREATE_PUBLIC_PROJECT_ACTION = xnatInterface -> xnatInterface.createProject(new Project().accessibility(Accessibility.PUBLIC));
    private static final int NUM_ADMIN_PROJECTS = 750;
    private static final int NUM_ACCOUNTS_CREATING_PROJECTS = 750;
    private static final int NUM_PUBLIC_PROJECTS = 500;
    private static final int NUM_PROJECTS_TO_MAKE_PUBLIC = 40;

    @Test
    public void testCreateProjectsAsAdmin() {
        performanceScenario()
                .tests(
                        new RepeatedMonitorableAction("create-projects-admin")
                                .asUser(mainAdminUser)
                                .overallIterationCount(NUM_ADMIN_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    @Test
    public void testCreateManyProjectsAsIndividualUsers() {
        mainAdminInterface().disableSmtp();
        performanceScenario()
                .tests(
                        new RepeatedMonitorableAction("create-many-projects-one-per-user")
                                .withUserProvider(new SequentialUserProvider(createGenericUsers(NUM_ACCOUNTS_CREATING_PROJECTS)))
                                .overallIterationCount(NUM_ACCOUNTS_CREATING_PROJECTS)
                                .performanceTestAction(CREATE_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC)
                ).run();
    }

    @Test
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
                                .asUser(mainUser)
                                .overallIterationCount(NUM_PUBLIC_PROJECTS)
                                .performanceTestAction(CREATE_PUBLIC_PROJECT_ACTION)
                                .validateUsing(PolynomialRegressionValidator.STRICT_QUADRATIC),
                        new RepeatedMonitorableAction("make-projects-public")
                                .asUser(mainUser)
                                .overallIterationCount(NUM_PROJECTS_TO_MAKE_PUBLIC)
                                .actionsPerSnapshot(1)
                                .performanceTestAction(publishAction)
                                .validateUsing(PolynomialRegressionValidator.LINEAR)
                ).run();
    }

}
