package org.nrg.testing.xnat.tests.eventservice;

import com.google.common.collect.Sets;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.events.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

@AddedIn(Xnat_1_8_0.class)
public class TestEventServiceUsage extends BaseEventServiceTest {

    @Test
    public void testSubscriptionUpdate() {
        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                build();

        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Project project = new Project();
        final Subject subject1 = new Subject(project);
        mainInterface().createProject(project);
        projectsToCleanup.add(project);

        mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 1
        );

        subscription.getEventFilter().setEventType(Event.SESSION_EVENT_TYPE);
        mainAdminInterface().updateSubscription(subscription);

        final Subject subject2 = new Subject(project);
        final ImagingSession session = new MRSession(project, subject2);
        mainInterface().createSubject(subject2);

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 2
        );

        assertEquals(session.getLabel(), events.get(0).getTrigger().getLabel());
        assertEquals(subject1.getLabel(), events.get(1).getTrigger().getLabel());
    }

    @Test
    public void testSubscriptionDeactivate() {
        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                build();

        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Project project = new Project();
        final Subject subject1 = new Subject(project);
        mainInterface().createProject(project);
        projectsToCleanup.add(project);

        mainAdminInterface().deactivateSubscription(subscription);

        final Subject subject2 = new Subject(project);
        mainInterface().createSubject(subject2);

        mainAdminInterface().activateSubscription(subscription);

        final Subject subject3 = new Subject(project);
        mainInterface().createSubject(subject3);

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 2
        );

        assertEquals(subject3.getLabel(), events.get(0).getTrigger().getLabel());
        assertEquals(subject1.getLabel(), events.get(1).getTrigger().getLabel());
    }

    @Test
    public void testSubscriptionWithEventServiceDisabled() {
        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                build();

        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Project project = new Project();
        final Subject subject1 = new Subject(project);
        mainInterface().createProject(project);
        projectsToCleanup.add(project);

        mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 1
        );
        mainAdminInterface().disableEventService();

        final Subject subject2 = new Subject(project);
        mainInterface().createSubject(subject2);

        mainAdminInterface().enableEventService();

        final Subject subject3 = new Subject(project);
        mainInterface().createSubject(subject3);

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 2
        );

        assertEquals(subject3.getLabel(), events.get(0).getTrigger().getLabel());
        assertEquals(subject1.getLabel(), events.get(1).getTrigger().getLabel());
    }

    @Test
    public void testMultipleProjectSubscription() {
        final Project project1 = new Project();
        final Project project2 = new Project();
        final Project project3 = new Project();

        for (Project project : Arrays.asList(project1, project2, project3)) {
            mainInterface().createProject(project);
            projectsToCleanup.add(project);
        }

        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                projects(Arrays.asList(project1, project2)).
                build();
        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Subject subject1 = new Subject(project1);
        final Subject subject2 = new Subject(project2);
        final Subject subject3 = new Subject(project3);
        mainInterface().createSubject(subject3);

        int expectedNum = 1;
        for (Subject subject : Arrays.asList(subject1, subject2)) {
            mainInterface().createSubject(subject);
            mainAdminInterface().queryDeliveredEvents(
                    buildDeliveredEventQueryForSubscription(subscription), expectedNum
            );
            expectedNum++;
        }

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 2
        );

        assertEquals(subject2.getLabel(), events.get(0).getTrigger().getLabel());
        assertEquals(subject1.getLabel(), events.get(1).getTrigger().getLabel());
    }

    @Test
    public void testPayloadFilteringDoubleQuote() {
        performFilterTest(MR_SESSION_PAYLOAD_FILTER_DOUBLE_QUOTE);
    }

    @Test
    public void testPayloadFilteringSingleQuote() {
        performFilterTest(MR_SESSION_PAYLOAD_FILTER_SINGLE_QUOTE);
    }

    @Test
    public void testPayloadFilteringInclusionDoubleQuote() {
        performFilterTest(MR_SESSION_PAYLOAD_FILTER_INCLUSION_DOUBLE_QUOTE);
    }

    @Test
    public void testPayloadFilteringInclusionSingleQuote() {
        performFilterTest(MR_SESSION_PAYLOAD_FILTER_INCLUSION_SINGLE_QUOTE);
    }

    @Test
    public void testProjectSubscriptionUpdate() {
        final Project project1 = new Project();
        mainInterface().createProject(project1);
        projectsToCleanup.add(project1);
        final Project project2 = new Project();
        mainInterface().createProject(project2);
        projectsToCleanup.add(project2);

        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                forProject(project1).
                build();

        mainInterface().createProjectSubscription(project1, subscription);
        subscriptionsToCleanup.add(subscription);

        final Subject subject1 = new Subject(project1);
        mainInterface().createSubject(subject1);
        final Subject subject2 = new Subject(project2);
        mainInterface().createSubject(subject2);

        mainInterface().queryProjectDeliveredEvents(
                project1, buildDeliveredEventQueryForSubscription(subscription), 1
        );

        subscription.getEventFilter().setEventType(Event.SESSION_EVENT_TYPE);
        mainInterface().updateProjectSubscription(project1, subscription);

        final Subject subject3 = new Subject(project1);
        final ImagingSession session1 = new MRSession(project1, subject3);
        final Subject subject4 = new Subject(project2);
        mainInterface().createSubject(subject4.addExperiment(new MRSession(project2, subject4)));
        mainInterface().createSubject(subject3);

        final List<DeliveredEvent> events = mainInterface().queryProjectDeliveredEvents(
                project1, buildDeliveredEventQueryForSubscription(subscription), 2
        );

        assertEquals(session1.getLabel(), events.get(0).getTrigger().getLabel());
        assertEquals(subject1.getLabel(), events.get(1).getTrigger().getLabel());
    }

    @Test
    public void testProjectSubscriptionWithEventServiceDisabled() {
        final Project project1 = new Project();
        mainInterface().createProject(project1);
        projectsToCleanup.add(project1);
        final Project project2 = new Project();
        mainInterface().createProject(project2);
        projectsToCleanup.add(project2);

        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                forProject(project1).
                build();

        mainInterface().createProjectSubscription(project1, subscription);
        subscriptionsToCleanup.add(subscription);

        mainAdminInterface().disableEventService();

        final Subject subject1 = new Subject(project1);
        mainInterface().createSubject(subject1);
        final Subject subject2 = new Subject(project2);
        mainInterface().createSubject(subject2);

        mainAdminInterface().enableEventService();

        final Subject subject3 = new Subject(project1);
        final Subject subject4 = new Subject(project2);
        mainInterface().createSubject(subject4);
        mainInterface().createSubject(subject3);

        final List<DeliveredEvent> events = mainInterface().queryProjectDeliveredEvents(
                project1, buildDeliveredEventQueryForSubscription(subscription), 1
        );

        assertEquals(subject3.getLabel(), events.get(0).getTrigger().getLabel());

        assertEquals(events, mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription)
        ));
    }

    @Test
    public void testProjectPayloadFilteringDoubleQuote() {
        performProjectFilterTest(MR_SESSION_PAYLOAD_FILTER_DOUBLE_QUOTE);
    }

    @Test
    public void testProjectPayloadFilteringSingleQuote() {
        performProjectFilterTest(MR_SESSION_PAYLOAD_FILTER_SINGLE_QUOTE);
    }

    @Test
    public void testProjectPayloadFilteringInclusionDoubleQuote() {
        performProjectFilterTest(MR_SESSION_PAYLOAD_FILTER_INCLUSION_DOUBLE_QUOTE);
    }

    @Test
    public void testProjectPayloadFilteringInclusionSingleQuote() {
        performProjectFilterTest(MR_SESSION_PAYLOAD_FILTER_INCLUSION_SINGLE_QUOTE);
    }

    @Test
    @TestRequires(data = TestData.SAMPLE_1)
    public void testMultipleTriggeredEvents() {
        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SCAN_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                build();

        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Project project = new Project();
        mainInterface().createProject(project);
        projectsToCleanup.add(project);

        restDriver.uploadToSessionZipImporter(TestData.SAMPLE_1, project);

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 3
        );

        assertEquals(
                Sets.newHashSet("4 - t1_mpr_1mm_p2_pos50", "5 - t1_mpr_1mm_p2_pos50", "6 - t2_spc_1mm_p2"),
                events.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    private void performFilterTest(String filter) {
        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SESSION_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                filter(filter).
                build();

        mainAdminInterface().createSubscription(subscription);
        subscriptionsToCleanup.add(subscription);

        final Project project = new Project();
        final Subject subject1 = new Subject(project);
        final PETSession pet = new PETSession(project, subject1);
        final MRSession mr = new MRSession(project, subject1);
        mainInterface().createProject(project);
        projectsToCleanup.add(project);

        final List<DeliveredEvent> events = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription), 1
        );

        assertEquals(mr.getLabel(), events.get(0).getTrigger().getLabel());
    }

    private void performProjectFilterTest(String filter) {
        final Project project1 = new Project();
        mainInterface().createProject(project1);
        projectsToCleanup.add(project1);
        final Project project2 = new Project();
        mainInterface().createProject(project2);
        projectsToCleanup.add(project2);

        final Subscription subscription = new SubscriptionBuilder().
                event(Event.SESSION_EVENT_TYPE, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                filter(filter).
                forProject(project1).
                build();

        mainInterface().createProjectSubscription(project1, subscription);
        subscriptionsToCleanup.add(subscription);

        final Subject otherProjectSubject = new Subject(project2);
        final PETSession otherPet = new PETSession(project2, otherProjectSubject);
        final MRSession otherMr = new MRSession(project1, otherProjectSubject);
        mainInterface().createSubject(otherProjectSubject);

        final Subject mainProjectSubject = new Subject(project1);
        final PETSession pet = new PETSession(project1, mainProjectSubject);
        final MRSession mr = new MRSession(project1, mainProjectSubject);
        mainInterface().createSubject(mainProjectSubject);

        final List<DeliveredEvent> events = mainInterface().queryProjectDeliveredEvents(
                project1, buildDeliveredEventQueryForSubscription(subscription), 1
        );

        assertEquals(mr.getLabel(), events.get(0).getTrigger().getLabel());

        assertEquals(events, mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subscription)
        ));
    }

}
