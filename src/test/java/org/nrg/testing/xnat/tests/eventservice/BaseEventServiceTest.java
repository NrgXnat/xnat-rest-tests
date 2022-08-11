package org.nrg.testing.xnat.tests.eventservice;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.events.DeliveredEventQueryFilterKey;
import org.nrg.xnat.pogo.events.Subscription;
import org.nrg.xnat.pogo.paginated_api.HibernateFilter;
import org.nrg.xnat.pogo.paginated_api.PaginatedRequest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.nrg.testing.TestGroups.*;

@Test(groups = EVENT_SERVICE)
public abstract class BaseEventServiceTest extends BaseXnatRestTest {

    protected List<Subscription> subscriptionsToCleanup = new ArrayList<>();
    protected List<Project> projectsToCleanup = new ArrayList<>();
    private boolean initialEventServiceStatus;
    protected static final String MR_SESSION_PAYLOAD_FILTER_DOUBLE_QUOTE = "@.xsiType == \"xnat:mrSessionData\"";
    protected static final String MR_SESSION_PAYLOAD_FILTER_SINGLE_QUOTE = MR_SESSION_PAYLOAD_FILTER_DOUBLE_QUOTE.replace("\"", "'");
    protected static final String MR_SESSION_PAYLOAD_FILTER_INCLUSION_DOUBLE_QUOTE = "(@.xsiType in [\"xnat:mrSessionData\"])";
    protected static final String MR_SESSION_PAYLOAD_FILTER_INCLUSION_SINGLE_QUOTE = MR_SESSION_PAYLOAD_FILTER_INCLUSION_DOUBLE_QUOTE.replace("\"", "'");

    @BeforeClass(groups = EVENT_SERVICE)
    protected void cacheStatus() {
        initialEventServiceStatus = mainAdminInterface().readEventServiceEnabled();
        mainAdminInterface().enableEventService();
    }

    @BeforeMethod(groups = EVENT_SERVICE)
    protected void enableStatus() {
        mainAdminInterface().enableEventService();
    }

    @AfterClass(alwaysRun = true, groups = EVENT_SERVICE)
    protected void restoreStatus() {
        mainAdminInterface().setEventServiceStatus(initialEventServiceStatus);
    }

    @AfterClass(alwaysRun = true, groups = EVENT_SERVICE)
    protected void cleanupCreatedObjects() {
        for (Project project : projectsToCleanup) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
        for (Subscription subscription : subscriptionsToCleanup) {
            try {
                mainAdminInterface().deleteSubscription(subscription);
            } catch (Exception | Error ignored) {}
        }
    }

    protected PaginatedRequest buildDeliveredEventQueryForSubscription(Subscription subscription) {
        return new PaginatedRequest().filter(DeliveredEventQueryFilterKey.SUBSCRIPTION,
                new HibernateFilter(subscription.getName()));
    }
}
