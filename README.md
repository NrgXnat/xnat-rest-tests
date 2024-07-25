# Overview #

This project runs integration tests on an XNAT test server using XNAT's REST API. It contains tests for both core XNAT itself and several common XNAT plugins.
            	
# Usage #
Most of the configuration properties can be included either on the command-line (using the syntax -Dproperty.name=property.value) when running the tests, or in the main properties file. By default, the tests will attempt to find the properties file in src/test/resources/config/local.properties. However, a different properties file (which is still required to be in src/test/resources/config) may be specified with the xnat.config property. If a property is found in both the properties file, and on the command-line, the value on the command line will take precedence. Provided here is an example command to launch the tests:
```
#!bash

$ mvn clean test -Dxnat.config=mycustom.properties -Dxnat.main.password=passw0rd -Dxnat.mainAdmin.password=passw0rd -Dxnat.admin.password=passw0rd
```

## Configuration ##
There are several properties required to be set. The meanings of the properties are defined in [nrg_test](http://www.bitbucket.org/xnatdev/nrg_test/). Note that if the default value is correct for the particular test run, the particular property can of course be omitted:
* xnat.main.user
* xnat.main.password
* xnat.mainAdmin.user
* xnat.mainAdmin.password
* xnat.admin.user
* xnat.admin.password
* xnat.version
* xnat.users.email
* xnat.baseurl
* xnat.dicom.host
* xnat.dicom.port
* xnat.dicom.aetitle

Properties required to successfully run tests for the DICOM Query/Retrieve (DQR) plugin are:
* dqr.pacs.dimse.host
* dqr.pacs.dimse.aeTitle
* dqr.pacs.dimse.port
* dqr.pacs.dicomweb.rootUrl
* dqr.pacs.dicomweb.aeTitle
* dqr.scpReceiver.aeTitle
* dqr.scpReceiver.port

Properties required to successfully run tests for the Container Service plugin are:
* cs.swarm.timeout
* cs.swarm.constraints
* cs.backends

Properties additionally required to run the performance tests are:
* xnat.ssh.user
* xnat.ssh.key
* xnat.ssh.skipHostKeyVerification
* tomcat.control
* xnat.performance.allow (must be set to `true`)
* xnat.performance.resetId
* xnat.performance.plugin.install
* xnat.performance.plugin.uninstall
* xnat.performance.setBaseline
* xnat.performance.exportOnly
* xnat.performance.newTestsOnly
* xnat.performance.compilePdf

Properties that are used only if using the summary email feature are:
* xnat.notifiedEmails
* xnat.notificationTitle
* xnat.notifyOnSuccess
* mail.smtp.host
* mail.smtp.port

Properties that these tests support to change some of the behavior, but are not required are:
* xnat.dicom.callingaetitle
* xnat.testBehavior.expectedFailures
* xnat.testBehavior.missingPlugins
* xnat.basic
* xnat.temp
* xnat.dependencies
* xnat.timelogs
* xnat.gitLogs
* xnat.setupMrscan
* xnat.allowLogging
* xnat.jira
* xnat.producePdf

## Performance Tests ##
See the section in [nrg_test](http://www.bitbucket.org/xnatdev/nrg_test/) for additional detail on the performance tests.

# Need more Info? #
More information may be found in the README for the [nrg_test](http://www.bitbucket.org/xnatdev/nrg_test/) project, the main dependency for this one. Various additional configuration parameters are documented there, some of which will have no effect (because they're used in other downstream test projects).

