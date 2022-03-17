package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;


@TestRequires(admin = true, data = TestData.SAMPLE_1_SCAN_4)
public class TestDicomSCPWhitelist extends BaseXnatRestTest {
    private static final Logger    log               = Logger.getLogger(TestDicomSCPWhitelist.class);
    private final String           myPublicIpAddress = getPublicIpAddress();
    private final Project          project           = new Project();
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
            log.warn(throwable);
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

        new XnatCStore(receiver, "INVALID_AE")
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

        try{
            new XnatCStore(receiver, "INVALID_AE")
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        }catch(Throwable e){
            if(! (e instanceof AAssociateRJ)){
                log.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidIpAddress(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList("8.8.8.8"));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try{
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        }catch(Throwable e){
            if(! (e instanceof AAssociateRJ)){
                log.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidIpAddress(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(myPublicIpAddress));
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
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE + "@" + myPublicIpAddress));
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
        receiver.setWhitelist(Collections.singletonList("INVALID@" + myPublicIpAddress));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try{
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        }catch(Throwable e){
            if(! (e instanceof AAssociateRJ)){
                log.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithValidAEAndInvalidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList(Settings.CALLING_AE_TITLE + "@8.8.8.8"));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try{
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        }catch(Throwable e){
            if(! (e instanceof AAssociateRJ)){
                log.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    @Test
    public void testWhitelistEnabledWithInvalidAEAndValidIp(){
        receiver.whitelistEnabled(true);
        receiver.setWhitelist(Collections.singletonList("INVALID@" + myPublicIpAddress));
        mainAdminInterface().updateDicomScpReceiver(receiver);

        try{
            new XnatCStore(receiver)
                    .overwrittenHeaders(Collections.singletonMap(Tag.StudyDescription, project.getId()))
                    .data(TestData.SAMPLE_1_SCAN_4)
                    .sendDICOM();
        }catch(Throwable e){
            if(! (e instanceof AAssociateRJ)){
                log.error(e.getMessage(), e);
                throw e;
            }
        }
        assertEquals(0, mainInterface().getPrearchiveEntriesForProject(project).size());
    }

    private String getPublicIpAddress(){
        final List<String> checkIpUrls = Arrays.asList("http://checkip.amazonaws.com", "https://ipv4.icanhazip.com",
                                "https://myexternalip.com/raw", "http://ipecho.net/plain", "http://www.trackip.net/ip");

        for(String urlStr : checkIpUrls){
            try {
                final URL url                    = new URL(urlStr);
                final InputStreamReader inStream = new InputStreamReader(url.openStream());
                return new BufferedReader(inStream).readLine();
            } catch (IOException ignored) { }
        }
        throw new RuntimeException("Failed to retrieve public ip address");
    }
}
