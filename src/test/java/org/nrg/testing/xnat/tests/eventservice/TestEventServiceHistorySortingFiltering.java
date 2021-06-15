package org.nrg.testing.xnat.tests.eventservice;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.events.*;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.paginated_api.HibernateFilter;
import org.nrg.xnat.pogo.paginated_api.PaginatedRequest;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.AssertJUnit.*;

@AddedIn(Xnat_1_8_0.class)
public class TestEventServiceHistorySortingFiltering extends BaseEventServiceTest {

    final Project project1 = new Project();
    final Project project2 = new Project();
    Subject subject1, subject2, subject3, subject4;
    MRSession session1, session2, session3, session4;
    final Subscription subjectSubscription = prepareLoggingSubscription(Event.SUBJECT_EVENT_TYPE);
    final Subscription sessionSubscription = prepareLoggingSubscription(Event.SESSION_EVENT_TYPE);
    List<DeliveredEvent> subjectEvents;
    DeliveredEvent subject4Event, subject3Event, subject2Event, subject1Event;

    @BeforeClass
    public void addData() {
        mainAdminInterface().createSubscription(subjectSubscription);
        subscriptionsToCleanup.add(subjectSubscription);
        mainAdminInterface().createSubscription(sessionSubscription);
        subscriptionsToCleanup.add(sessionSubscription);
        mainInterface().createProject(project1);
        projectsToCleanup.add(project1);
        mainInterface().createProject(project2);
        projectsToCleanup.add(project2);
        subject1 = new Subject(project1, "C");
        session1 = new MRSession(project1, subject1, "C_MR");
        subject2 = new Subject(project1, "B");
        session2 = new MRSession(project1, subject2, "B_MR");
        subject3 = new Subject(project2, "A");
        session3 = new MRSession(project2, subject3, "A_MR");
        subject4 = new Subject(project2, "D");
        session4 = new MRSession(project2, subject4, "D_MR");
        final List<Subject> subjects = Arrays.asList(subject1, subject2, subject3, subject4);
        for (int index = 0; index < subjects.size(); index++) {
            (index < 3 ? mainInterface() : mainAdminInterface()).createSubject(subjects.get(index));
            mainAdminInterface().queryDeliveredEvents(buildDeliveredEventQueryForSubscription(subjectSubscription), index + 1);
            mainAdminInterface().queryDeliveredEvents(buildDeliveredEventQueryForSubscription(sessionSubscription), index + 1);
        }
        subjectEvents = mainAdminInterface().queryDeliveredEvents(buildDeliveredEventQueryForSubscription(subjectSubscription));
        subject4Event = subjectEvents.get(0);
        subject3Event = subjectEvents.get(1);
        subject2Event = subjectEvents.get(2);
        subject1Event = subjectEvents.get(3);
    }

    @Test
    public void testSiteDeliveredEventSorting() {
        final boolean usernameCompare = mainAdminUser.getUsername().compareToIgnoreCase(mainUser.getUsername()) > 0;
        assertEquals(subject4.getLabel(), subject4Event.getTrigger().getLabel());
        assertEquals(subject3.getLabel(), subject3Event.getTrigger().getLabel());
        assertEquals(subject2.getLabel(), subject2Event.getTrigger().getLabel());
        assertEquals(subject1.getLabel(), subject1Event.getTrigger().getLabel());

        final List<DeliveredEvent> sortedEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectSubscription).
                        sort(DeliveredEventQuerySortColumn.TIMESTAMP, PaginatedRequest.SortDir.ASC)
        );
        assertEquals(Arrays.asList(subject1Event, subject2Event, subject3Event, subject4Event), sortedEvents);

        final List<DeliveredEvent> userSortedEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectSubscription).
                        sort(DeliveredEventQuerySortColumn.USER, PaginatedRequest.SortDir.DESC)
        );
        assertEquals(subject4Event, userSortedEvents.get(usernameCompare ? 0 : 3));
    }

    @Test
    public void testSiteDeliveredEventFiltering() {
        assertEquals(Arrays.asList(subject2Event, subject1Event), mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectSubscription)
                        .filter(DeliveredEventQueryFilterKey.PROJECT, new HibernateFilter(project1.getId()))
                        .filter(DeliveredEventQueryFilterKey.EVENTTYPE, new HibernateFilter("Subject :"))
        ));

        assertEquals(subjectEvents, mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectSubscription)
                        .filter(DeliveredEventQueryFilterKey.STATUS, new HibernateFilter("Logging action completed"))
        ));

        assertEquals(Collections.singletonList(subject4Event), mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectSubscription).
                        filter(DeliveredEventQueryFilterKey.USER, new HibernateFilter(mainAdminUser.getUsername()))
        ));
    }

    @Test
    public void testSiteDeliveredEventPagination() {
        final PaginatedRequest query = buildDeliveredEventQueryForSubscription(subjectSubscription).size(2);
        assertEquals(Arrays.asList(subject4Event, subject3Event), mainAdminInterface().queryDeliveredEvents(query));
        query.setPage(2);
        assertEquals(Arrays.asList(subject2Event, subject1Event), mainAdminInterface().queryDeliveredEvents(query));
    }

    private Subscription prepareLoggingSubscription(String eventType) {
        return new SubscriptionBuilder().
                name(RandomHelper.randomID()).
                event(eventType, EventStatus.CREATED).
                actionKey(Action.LOGGING_ACTION).
                actAsEventUser().
                build();
    }

}
