#!/usr/bin/env bash
# Runs this suite's performance tests as a Job in the namespace of an XNAT on Kubernetes, so REST calls and C-STORE
# sends go pod to service instead of through a workstation's connection to the cluster, and the tests reset and
# restart XNAT through kubectl with the Job's service account.
#
#   kubernetes/run-performance-tests.sh --namespace NS --config FILE [--context CTX] [--tests CLASS[#METHOD],...]
#       [--image IMAGE] [--kubectl-version vX.Y.Z] [--arch amd64|arm64] [--nrg-test-version VERSION]
#       [--out DIR] [--deadline SECONDS] [--keep] [-- -Dproperty=value ...]
#
# FILE is a properties file for the run (see src/test/resources/config/kubernetes.properties.example). The pod runs
# `mvn test` from a stock Maven image, so it needs to reach the Maven repositories in pom.xml and the test data
# server. An nrg_test snapshot in the local Maven repository is copied in; any other version is downloaded.
set -euo pipefail

usage() { sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

NAMESPACE= CONTEXT= CONFIG= TESTS= IMAGE=maven:3.9-eclipse-temurin-8 KUBECTL_VERSION= ARCH=amd64
NRG_TEST_VERSION=2.7-kubernetes-SNAPSHOT OUT= DEADLINE=86400 KEEP=0 MVN_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --namespace) NAMESPACE=$2; shift 2 ;;
        --context) CONTEXT=$2; shift 2 ;;
        --config) CONFIG=$2; shift 2 ;;
        --tests) TESTS=$2; shift 2 ;;
        --image) IMAGE=$2; shift 2 ;;
        --kubectl-version) KUBECTL_VERSION=$2; shift 2 ;;
        --arch) ARCH=$2; shift 2 ;;
        --nrg-test-version) NRG_TEST_VERSION=$2; shift 2 ;;
        --out) OUT=$2; shift 2 ;;
        --deadline) DEADLINE=$2; shift 2 ;;
        --keep) KEEP=1; shift ;;
        -h|--help) usage ;;
        --) shift; MVN_ARGS=("$@"); break ;;
        *) echo "Unknown argument: $1" >&2; usage 1 ;;
    esac
done
[ -n "$NAMESPACE" ] && [ -n "$CONFIG" ] || usage 1
[ -f "$CONFIG" ] || { echo "No such config file: $CONFIG" >&2; exit 1; }

REPO=$(cd "$(dirname "$0")/.." && pwd)
export COPYFILE_DISABLE=1  # keep macOS tar from adding ._ metadata files to the upload
K=(kubectl ${CONTEXT:+--context "$CONTEXT"} --namespace "$NAMESPACE")
JOB=xnat-performance-tests-$(date +%Y%m%d-%H%M%S)
OUT=${OUT:-$REPO/target/kubernetes-runs/$JOB}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/xnat-rest-tests
SNAPSHOT=$HOME/.m2/repository/org/nrg/nrg_test/$NRG_TEST_VERSION

# kubectl for the pod, matching the cluster's version unless one is given.
if [ -z "$KUBECTL_VERSION" ]; then
    KUBECTL_VERSION=$("${K[@]}" version -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"].split("-")[0])')
fi
KUBECTL_BINARY=$CACHE/kubectl-$KUBECTL_VERSION-linux-$ARCH
if [ ! -x "$KUBECTL_BINARY" ]; then
    mkdir -p "$CACHE"
    curl -fsSL "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/$ARCH/kubectl" -o "$KUBECTL_BINARY.part"
    chmod +x "$KUBECTL_BINARY.part" && mv "$KUBECTL_BINARY.part" "$KUBECTL_BINARY"
fi

echo "Starting Job $JOB in $NAMESPACE"
"${K[@]}" apply -f "$REPO/kubernetes/rbac.yaml" >/dev/null
sed -e "s|\${JOB_NAME}|$JOB|g" -e "s|\${IMAGE}|$IMAGE|g" -e "s|\${DEADLINE_SECONDS}|$DEADLINE|g" \
    "$REPO/kubernetes/performance-job.yaml" | "${K[@]}" apply -f - >/dev/null

cleanup() {
    if [ "$KEEP" -eq 0 ]; then
        "${K[@]}" delete job "$JOB" --wait=false >/dev/null 2>&1 || true
    else
        echo "Kept Job $JOB"
    fi
}
trap cleanup EXIT

POD=
for _ in $(seq 1 120); do
    POD=$("${K[@]}" get pods -l "job-name=$JOB" -o name 2>/dev/null | head -1)
    [ -n "$POD" ] && break
    sleep 2
done
[ -n "$POD" ] || { echo "The Job's pod never appeared" >&2; exit 1; }
"${K[@]}" wait --for=condition=Ready "$POD" --timeout=600s >/dev/null
put() { "${K[@]}" exec -i "$POD" -c tests -- sh -c "$1"; }

echo "Uploading the tests to $POD"
git -C "$REPO" ls-files -z | tar -C "$REPO" --null -T - -cf - | put 'tar -xf - -C /work'
put 'mkdir -p /work/src/test/resources/config && cat > /work/src/test/resources/config/kubernetes-run.properties' < "$CONFIG"
put 'mkdir -p /work/bin && cat > /work/bin/kubectl && chmod +x /work/bin/kubectl' < "$KUBECTL_BINARY"
if [ -d "$SNAPSHOT" ]; then
    tar -C "$HOME/.m2/repository" -cf - "org/nrg/nrg_test/$NRG_TEST_VERSION" | put 'mkdir -p /root/.m2/repository && tar -xf - -C /root/.m2/repository'
fi
{
    echo 'export PATH=/work/bin:$PATH'
    echo 'cd /work'
    printf 'mvn -B'
    printf ' %q' "-Dnrg_test.version=$NRG_TEST_VERSION" "-Dxnat.config=kubernetes-run.properties"
    [ -n "$TESTS" ] && printf ' %q' "-Dtest=$TESTS"
    [ ${#MVN_ARGS[@]} -gt 0 ] && printf ' %q' "${MVN_ARGS[@]}"
    printf ' test\n'
} | put 'cat > /work/run.sh'
put 'touch /work/.ready'

echo "Running; following the log (Ctrl-C stops the run and deletes the Job unless --keep)"
"${K[@]}" logs -f "$POD" -c tests &
LOGS=$!
until "${K[@]}" exec "$POD" -c tests -- test -f /work/.exit 2>/dev/null; do sleep 15; done
kill "$LOGS" 2>/dev/null || true

mkdir -p "$OUT"
put 'cd /work && tar -cf - $(ls -d target/surefire-reports src/test/resources/data/performance xnat_test.log* 2>/dev/null)' | tar -xf - -C "$OUT" ||
    echo "Could not collect all of the results from $POD" >&2
EXIT=$("${K[@]}" exec "$POD" -c tests -- cat /work/.exit)
put 'touch /work/.collected'
echo "The tests exited $EXIT; results are in $OUT"
exit "$EXIT"
