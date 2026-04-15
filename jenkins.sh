#!/bin/bash

export JAVA_HOME="/usr/lib/jvm/jre-1.8.0-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
env

URL=$(echo $URL | sed 's/\/$//')

cfgFile=jenkins.properties
config=src/test/resources/config/$cfgFile
cat > $config << EOF
xnat.users.email=$EMAIL
xnat.baseurl=$URL
xnat.main.user=unitTest
xnat.main.password=unitTest234
xnat.mainAdmin.user=$ADMIN_USER
xnat.mainAdmin.password=$ADMIN_PWD
xnat.admin.user=$ADMIN_USER
xnat.admin.password=$ADMIN_PWD
xnat.version=$XNAT_LINEAGE
xnat.setupMrscan=true
xnat.testBehavior.expectedFailures=ignore
xnat.temp=/var/lib/jenkins/tmp
cs.backends=${CS_BACKEND:-docker,swarm}
cs.swarm.timeout=10
cs.swarm.constraints=false,engine.labels.instance.spot,==,False;false,engine.labels.instance.type,==,m5.large
EOF

# DQR plugin config (written only when DQR_AE_TITLE is provided by the Jenkins job)
if [[ ! -z "${DQR_AE_TITLE}" ]]; then
cat >> $config << EOF
dqr.scpReceiver.aeTitle=${DQR_AE_TITLE}
dqr.scpReceiver.port=${DQR_PORT:-8104}
dqr.pacs.dimse.aeTitle=${ORTHANC_AET}
dqr.pacs.dimse.host=${ORTHANC_IP}
dqr.pacs.dimse.port=4242
dqr.pacs.dicomweb.aeTitle=${ORTHANC_AET}
dqr.pacs.dicomweb.rootUrl=http://${ORTHANC_IP}:8042/dicom-web
EOF
fi

# set config per test expectations
basicAuth=$ADMIN_USER:$ADMIN_PWD
curl -s -X POST -u $basicAuth -H "Content-Type: application/json" $URL/xapi/siteConfig -d '{"uiAllowNonAdminProjectCreation":true,"requireChangeJustification":false,"security.prevent-data-deletion":false,"securityAllowNonPrivateProjects":true,"crPreventMerge":false,"projectAllowAutoArchive":true,"passwordComplexity":"^.*$","defaultProjectAutoArchiveSetting":4}'

# disable routing configs
for cfg in projectRules subjectRules sessionRules; do
    curl -s -X PUT -u $basicAuth "$URL/data/config/dicom/$cfg?status=disabled"
done

mvn -Dxnat.config=$cfgFile clean test ${TEST_CLASS:+ -Dtest=$TEST_CLASS} $MVN_OPTIONS
result=$?

# reset requireLogin
curl -s -X POST -u $basicAuth -H "Content-Type: application/json" $URL/xapi/siteConfig -d '{"requireLogin":true}'

testngXml=target/surefire-reports/testng-results.xml
if [[ $result -ne 0 ]] && [[ ! -e $testngXml ]]; then
    echo "Making a fake $testngXml so Jenkins marks this as a failure"
    cat > $testngXml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testng-results skipped="1" failed="0" total="1" passed="0">
</testng-results>
EOF
fi

rm -rf /tmp/*
exit $result
