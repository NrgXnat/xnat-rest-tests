package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

@AddedIn(Xnat_1_8_5.class)
@TestRequires(admin = true, data = TestData.SAMPLE_1_SCAN_4)
public class TestDicomSCPWhitelist extends BaseXnatRestTest {
    private static final Logger       LOG            = Logger.getLogger(TestDicomSCPWhitelist.class);
    private static final String       INVALID_AE     = "NOT_WHITELISTED";
    private static final String       INVALID_IP     = "8.8.8.8";
    private static final List<String> CHECK_IP_URLS  = Arrays.asList("http://checkip.amazonaws.com", "https://ipv4.icanhazip.com",
                                                                     "https://myexternalip.com/raw", "http://ipecho.net/plain",
                                                                     "http://www.trackip.net/ip");

    private final List<String>     myIps    = getMyIps();
    private final Project          project  = new Project();
    private final DicomScpReceiver receiver
                        = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
                                                .host(Settings.DICOM_HOST)
                                                .port(Settings.DICOM_PORT)
                                                .identifier(DicomObjectIdentifier.DEFAULT.getId())
                                                .enabled(true)
                                                .customProcessing(false)
                                                .directArchive(false)
                                                .anonymizationEnabled(true)
                                                .whitelistEnabled(false);
    @BeforeClass
    public void setup() {
        mainInterface().createProject(project);
        mainAdminInterface().createDicomScpReceiver(receiver);
    }

    @BeforeMethod(alwaysRun = true)
    @AfterClass(alwaysRun = true)
    public void clearPrearchive() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        mainAdminInterface().deleteProject(project);
        mainAdminInterface().deleteDicomScpReceiver(receiver);
    }

    @Test
    public void testWhitelistDisabled() {
        receiver.whitelistEnabled(false);
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        new XnatCStore(receiver, INVALID_AE)
                .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                .data(TestData.SAMPLE_1_SCAN_4)
                .sendDICOM();
        assertEquals(1, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidAETitle() {
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        new XnatCStore(receiver)
                .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                .data(TestData.SAMPLE_1_SCAN_4)
                .sendDICOM();
        assertEquals(1, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidAETitle() {
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try {
            new XnatCStore(receiver, INVALID_AE)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        } catch (Throwable e){
            if (!(e instanceof AAssociateRJ)) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidIpAddress(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(INVALID_IP));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try {
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        } catch (Throwable e){
            if (!(e instanceof AAssociateRJ)) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidIpAddress(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(myIps);
        mainAdminInterface().updateDicomScpReceiver(receiver);

        new XnatCStore(receiver)
                .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                .data(TestData.SAMPLE_1_SCAN_4)
                .sendDICOM();

        assertEquals(1, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidAEAndValidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(myIps.stream().map(ip -> Settings.CALLING_AE_TITLE + "@" + ip)
                .collect(Collectors.toList()));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        new XnatCStore(receiver)
                .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                .data(TestData.SAMPLE_1_SCAN_4)
                .sendDICOM();

        assertEquals(1, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidAEAndInvalidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(INVALID_AE + "@" + INVALID_IP));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try {
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        } catch (Throwable e) {
            if (!(e instanceof AAssociateRJ)) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidAEAndInvalidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE + "@" + INVALID_IP));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try {
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        } catch (Throwable e) {
            if (!(e instanceof AAssociateRJ)) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidAEAndValidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(myIps.stream().map(ip -> INVALID_AE + "@" + ip)
                .collect(Collectors.toList()));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try {
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        } catch (Throwable e){
            if (!(e instanceof AAssociateRJ)) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    private List<String> getMyIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                Enumeration<InetAddress> inetAddresses = networkInterfaces.nextElement().getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        ips.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {}
        ips.add(getPublicIpAddress());
        return ips;
    }

    private String getPublicIpAddress(){
        for(String urlStr : CHECK_IP_URLS){
            try {
                final URL url                    = new URL(urlStr);
                final InputStreamReader inStream = new InputStreamReader(url.openStream());
                return new BufferedReader(inStream).readLine();
            } catch (IOException ignored) { }
        }
        throw new RuntimeException("Failed to retrieve public ip address");
    }
}
