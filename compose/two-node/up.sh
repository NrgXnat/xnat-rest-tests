#!/usr/bin/env bash
# Starts the two-node test environment and configures the site. FOR TESTING ONLY.
#
#   XNAT_IMAGE=xnat:two-node-test ./up.sh
#
# Node B starts only after the site has been configured through node A. Each node caches
# site preferences in memory, so a node that is already running may not see changes made
# through the other.
set -euo pipefail
cd "$(dirname "$0")"

: "${XNAT_IMAGE:?Set XNAT_IMAGE to an XNAT image, for example one built by build-image.sh}"
export XNAT_IMAGE

# XNAT's default administrator account.
admin=admin:admin
node_a=http://localhost:8081
node_b=http://localhost:8082

# Tomcat answers a few seconds before XNAT accepts logins, so the containers' health checks
# aren't enough before making authenticated requests.
wait_for_login() {
    local url=$1 status
    for _ in $(seq 150); do
        status=$(curl -s -o /dev/null -w '%{http_code}' -u "$admin" "$url/data/auth" || true)
        # 302 is the redirect to the setup page of a site that isn't initialized yet.
        if [[ $status == 200 || $status == 302 ]]; then
            return 0
        fi
        sleep 2
    done
    echo "$url didn't accept the admin login within 5 minutes (last status: $status)" >&2
    return 1
}

# Multi-node sites are told to run the dist-events plugin, so both nodes load it from ./plugins.
# A jar already there, such as a local build, is used as it is.
dist_events_version=${DIST_EVENTS_VERSION:-2.0.1}
shopt -s nullglob
dist_events_jars=(plugins/dist-events-*.jar)
shopt -u nullglob
if [[ ${#dist_events_jars[@]} -eq 0 ]]; then
    echo "Downloading dist-events $dist_events_version..."
    jar=plugins/dist-events-$dist_events_version-xpl.jar
    curl -fsSL -o "$jar.part" "https://nrgxnat.jfrog.io/nrgxnat/libs-release/org/nrg/xnatx/plugins/dist-events/$dist_events_version/dist-events-$dist_events_version-xpl.jar"
    mv "$jar.part" "$jar"
elif [[ ${#dist_events_jars[@]} -gt 1 ]]; then
    echo "More than one dist-events jar in plugins/: ${dist_events_jars[*]}" >&2
    exit 1
fi

echo "Starting Postgres, the broker and node A..."
docker compose up -d --wait postgres activemq xnat-a
wait_for_login "$node_a"

echo "Configuring the site through node A..."
# The same settings jenkins.sh applies before a CI run, plus initialization and the site URL.
curl -fsS -u "$admin" -X POST -H "Content-Type: application/json" "$node_a/xapi/siteConfig" -d '{
    "initialized": true,
    "siteUrl": "http://localhost:8080",
    "uiAllowNonAdminProjectCreation": true,
    "requireChangeJustification": false,
    "security.prevent-data-deletion": false,
    "securityAllowNonPrivateProjects": true,
    "crPreventMerge": false,
    "projectAllowAutoArchive": true,
    "passwordComplexity": "^.*$",
    "defaultProjectAutoArchiveSetting": 4
}' > /dev/null
# A fresh site has none of these, and disabling one that doesn't exist fails with a 500.
for config in projectRules subjectRules sessionRules; do
    if curl -fsS -o /dev/null -u "$admin" "$node_a/data/config/dicom/$config" 2> /dev/null; then
        curl -fsS -u "$admin" -X PUT "$node_a/data/config/dicom/$config?status=disabled" > /dev/null
    fi
done

echo "Starting node B and the load balancer..."
docker compose up -d --wait xnat-b lb
wait_for_login "$node_b"

cat << EOF

Ready:
  http://localhost:8080  load balancer (round robin, no affinity)
  http://localhost:8081  node A (primary)
  http://localhost:8082  node B

See README.md for running the REST tests against it. Stop with "docker compose down";
add -v to also delete the database and data.
EOF
