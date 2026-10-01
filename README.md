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

### On Kubernetes ###
`kubernetes/run-performance-tests.sh` runs the performance tests as a Job in the namespace of an XNAT deployed on Kubernetes. Running in the cluster keeps REST calls and C-STORE sends pod to service, so the timings don't include a workstation's connection to the cluster. The tests reset and restart XNAT through kubectl with the Job's service account (`kubernetes/rbac.yaml`), as described under "Performance tests on Kubernetes" in nrg_test.

Each run wipes the target XNAT's archive and recreates its database, so only point it at an XNAT that holds nothing but test data.

1. Copy `src/test/resources/config/kubernetes.properties.example` outside the repository and fill it in.
2. Run, for example:
```
$ kubernetes/run-performance-tests.sh --context my-cluster --namespace my-xnat --config ~/my-xnat.properties \
      --tests TestPerformanceDicom#testLargeSeriesCountPerformance
```

The script applies the RBAC, starts the Job from a stock Maven image, uploads the tracked sources, the config, a Linux kubectl matching the cluster and, if your local Maven repository has it, nrg_test at the version in `pom.xml` (or `--nrg-test-version`), and then runs `mvn test` in the pod while following its log. Maven doesn't update snapshots it already has, so a locally built nrg_test is what runs. When the tests finish it copies `target/surefire-reports`, the XNAT logs saved before each restart in `target/xnat-logs`, the performance history and charts in `src/test/resources/data/performance`, and the test log into `target/kubernetes-runs/<job>`, then deletes the Job (`--keep` leaves it). The pod needs to reach the Maven repositories in `pom.xml` and the test data server.

The pod runs as uid 1000 with the restricted Pod Security Standard's settings, on a node of `--arch` (`amd64` by default), the architecture of the kubectl it is given. It keeps off the XNAT pod's node so the two don't compete for CPU: `--avoid-pod` names that pod, by default the config's `xnat.k8s.pod` or its StatefulSet's `<name>-0`.

The Job keeps running if the script loses its connection to the cluster; `--attach <job>` follows it again and collects its results. `--fresh-history` starts from an empty performance history, so each deployment is judged against the earlier deployments of the same run on the same XNAT, rather than against the history in the repository, which was recorded on other hardware. Run the script with `--help` for its other options.

# Need more Info? #
More information may be found in the README for the [nrg_test](http://www.bitbucket.org/xnatdev/nrg_test/) project, the main dependency for this one. Various additional configuration parameters are documented there, some of which will have no effect (because they're used in other downstream test projects).

