# 15 words or less #

This project runs integration tests on an XNAT test server using XNAT's REST API.
            	
# Usage #
This project contains a series of REST-based tests for core XNAT. Most of the configuration properties can be included either on the command-line (using the syntax -Dproperty.name=property.value) when running the tests, or in the main properties file. By default, the tests will attempt to find the properties file in src/test/resources/config/local.properties. However, a different properties file (which is still required to be in src/test/resources/config) may be specified with the xnat.config property. If a property is found in both the properties file, and on the command-line, the value on the command line will take precedence. Provided here is an example command to launch the tests:
```
#!bash

$ mvn clean test -Dxnat.config=mycustom.properties -Dxnat.main.password=passw0rd -Dxnat.mainAdmin.password=passw0rd -Dxnat.admin.password=passw0rd
```

## Configuration ##
All properties below must be specified:

* xnat.users.email: An email address for the user accounts.
* xnat.baseurl: The URL for the source XNAT server.
* xnat.main.user: The username of a non-admin account on the server to use in most tests. This account need not exist, as the tests will set up the user if necessary.
* xnat.main.password: The password for the account identified by xnat.main.user.
* xnat.mainAdmin.user: The username of an admin account on the server. This account need not exist, as the tests will set up the user if necessary. Note: it's completely reasonable to use the admin account from xnat.admin.user here too.
* xnat.mainAdmin.password: The password for the account identified by xnat.mainAdmin.user.
* xnat.admin.user: The username of an admin account on the server. This account must exist and is used to create the test accounts listed above.
* xnat.admin.password: The password for the account identified by xnat.admin.user.
* xnat.version: The version of XNAT the server is running.

Additionally, there are a few properties which may be specified optionally if you wish the tests to send out a summary email of the results on completion:

* xnat.notifiedEmails: Comma-separated list of emails to which results will be sent on test completion.
* xnat.notifyOnSuccess: Should summary email be sent when all tests pass? Defaults to false (as long as prerequisite properties are set).
* xnat.notificationTitle: How should the tests be referred to in the summary email? e.g. "XNAT develop-branch REST tests"
* xnat.users.email.password: The password for the email account referenced in xnat.users.email. If notification emails are being used, the email in xnat.users.email must now be a gmail address (configured to allow external applications to access it). The password for the xnatselenium@gmail.com address is available to NRG users from Charlie Moore on request.

# Need more Info? #

More information may be found in the README for the [nrg_test](http://www.bitbucket.org/xnatdev/nrg_test/) project, the main dependency for this one. Various additional configuration parameters are documented there, some of which will have no effect (because they're used in other downstream test projects). However, the main parameters that should be used are listed above.

