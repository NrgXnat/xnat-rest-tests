package org.nrg.testing.xnat.tests.eventservice;

import com.google.common.collect.Sets;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.xnat.pogo.events.*;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.nrg.testing.TestNgUtils.assertNotEquals;
import static org.testng.AssertJUnit.*;

@AddedIn(Xnat_1_8_0.class)
public class TestEventServiceListings extends BaseEventServiceTest {

    @Test
    public void testEventListing() {
        mainInterface().createProject(testSpecificProject);
        final List<Event> siteLevelEvents = mainInterface().readAvailableSiteEvents();
        final List<Event> projectLevelEvents = mainInterface().readAvailableProjectEvents(testSpecificProject);
        final List<Event> exclusivelySiteEvents = new ArrayList<>();
        Event projectEvent = null;
        for (Event event : siteLevelEvents) {
            assertTrue(event.getEventScope().contains(EventScope.SITE));
            if (!event.getEventScope().contains(EventScope.PROJECT)) {
                exclusivelySiteEvents.add(event);
            }
            if (event.getType().equals(Event.PROJECT_EVENT_TYPE)) {
                projectEvent = event;
            }
        }
        assertTrue(exclusivelySiteEvents.contains(projectEvent));
        for (Event event : projectLevelEvents) {
            assertNotEquals(event.getType(), Event.PROJECT_EVENT_TYPE);
            assertEquals(Arrays.asList(EventScope.PROJECT, EventScope.SITE), event.getEventScope());
        }
        assertEquals(Sets.newHashSet(siteLevelEvents), Sets.union(Sets.newHashSet(projectLevelEvents), Sets.newHashSet(exclusivelySiteEvents)));
    }

    @Test
    public void testActionListing() {
        mainInterface().createProject(testSpecificProject);
        assertEquals(
                findLoggingAction(mainAdminInterface().readSiteActionsForEvent(Event.PROJECT_EVENT_TYPE)),
                findLoggingAction(mainInterface().readProjectActionsForEvent(testSpecificProject, Event.PROJECT_EVENT_TYPE))
        );
    }

    private Action findLoggingAction(List<Action> actions) {
        final Action loggingAction = actions.stream().filter(action -> "Logging Action".equals(action.getDisplayName()))
                .findAny()
                .orElse(null);
        assertNotNull(loggingAction);
        return loggingAction;
    }

}
