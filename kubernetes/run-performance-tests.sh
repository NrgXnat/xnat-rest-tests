#!/usr/bin/env bash
# Runs this suite's performance tests as a Job in the namespace of an XNAT on Kubernetes, so REST calls and C-STORE
# sends go pod to service instead of through a workstation's connection to the cluster, and the tests reset and
# restart XNAT through kubectl with the Job's service account.
#
#   kubernetes/run-performance-tests.sh --namespace NS --config FILE [--context CTX] [--tests CLASS[#METHOD],...]
#       [--fresh-history] [--image IMAGE] [--kubectl-version vX.Y.Z] [--arch amd64|arm64]
#       [--nrg-test-version VERSION] [--avoid-pod POD] [--out DIR] [--deadline SECONDS] [--keep]
#       [-- -Dproperty=value ...]
#   kubernetes/run-performance-tests.sh --namespace NS [--context CTX] --attach JOB [--out DIR] [--keep]
#
# FILE is a properties file for the run (see src/test/resources/config/kubernetes.properties.example). The pod runs
# `mvn test` from a stock Maven image, as uid 1000 on a node of --arch, so it needs to reach the Maven repositories in
# pom.xml and the test data server. nrg_test is pom.xml's version unless --nrg-test-version names another; a copy in
# the local Maven repository is uploaded, and Maven doesn't update snapshots it already has, so a locally built
# nrg_test is what runs; any other version is downloaded.
# --fresh-history starts the run without the performance history in the repository, so results are judged against
# this run's own earlier deployments rather than past runs elsewhere. The Job keeps running if this script loses its
# connection; --attach picks it up again, follows it and collects the results.
set -euo pipefail

usage() { sed -n '2,/^[^#]/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

NAMESPACE= CONTEXT= CONFIG= TESTS= IMAGE=maven:3.9-eclipse-temurin-8 KUBECTL_VERSION= ARCH=amd64 ATTACH=
NRG_TEST_VERSION= OUT= DEADLINE=129600 KEEP=0 FRESH_HISTORY=0 AVOID_POD=xnat-0 MVN_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --namespace) NAMESPACE=$2; shift 2 ;;
        --context) CONTEXT=$2; shift 2 ;;
        --config) CONFIG=$2; shift 2 ;;
        --tests) TESTS=$2; shift 2 ;;
        --fresh-history) FRESH_HISTORY=1; shift ;;
        --image) IMAGE=$2; shift 2 ;;
        --kubectl-version) KUBECTL_VERSION=$2; shift 2 ;;
        --arch) ARCH=$2; shift 2 ;;
        --nrg-test-version) NRG_TEST_VERSION=$2; shift 2 ;;
        --avoid-pod) AVOID_POD=$2; shift 2 ;;
        --out) OUT=$2; shift 2 ;;
        --deadline) DEADLINE=$2; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --attach) ATTACH=$2; shift 2 ;;
        -h|--help) usage ;;
        --) shift; MVN_ARGS=("$@"); break ;;
        *) echo "Unknown argument: $1" >&2; usage 1 ;;
    esac
done
[ -n "$NAMESPACE" ] || usage 1
if [ -z "$ATTACH" ]; then
    [ -n "$CONFIG" ] || usage 1
    [ -f "$CONFIG" ] || { echo "No such config file: $CONFIG" >&2; exit 1; }
fi

REPO=$(cd "$(dirname "$0")/.." && pwd)
export COPYFILE_DISABLE=1  # keep macOS tar from adding ._ metadata files to the upload
K=(kubectl ${CONTEXT:+--context "$CONTEXT"} --namespace "$NAMESPACE")
JOB=${ATTACH:-xnat-performance-tests-$(date +%Y%m%d-%H%M%S)}
OUT=${OUT:-$REPO/target/kubernetes-runs/$JOB}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/xnat-rest-tests
NRG_TEST_VERSION=${NRG_TEST_VERSION:-$(sed -n 's:.*<nrg_test.version>\(.*\)</nrg_test.version>.*:\1:p' "$REPO/pom.xml" | head -1)}
SNAPSHOT=$HOME/.m2/repository/org/nrg/nrg_test/$NRG_TEST_VERSION
COLLECTED=0

finish() {
    if [ "$COLLECTED" -eq 1 ] && [ "$KEEP" -eq 0 ]; then
        "${K[@]}" delete job "$JOB" --wait=false >/dev/null 2>&1 || true
    elif [ "$COLLECTED" -eq 0 ]; then
        echo "The Job $JOB is still in the cluster. To follow it and collect its results:" >&2
        echo "  $0 --namespace $NAMESPACE ${CONTEXT:+--context $CONTEXT }--attach $JOB" >&2
    fi
}
trap finish EXIT

find_pod() {
    POD=
    for _ in $(seq 1 120); do
        POD=$("${K[@]}" get pods -l "job-name=$JOB" -o name 2>/dev/null | head -1)
        [ -n "$POD" ] && return 0
        sleep 2
    done
    echo "The Job's pod never appeared" >&2
    exit 1
}
put() { "${K[@]}" exec -i "$POD" -c tests -- sh -c "$1"; }

start_job() {
    # kubectl for the pod, matching the cluster's version unless one is given.
    if [ -z "$KUBECTL_VERSION" ]; then
        KUBECTL_VERSION=$("${K[@]}" version -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"].split("-")[0])')
    fi
    local kubectl_binary=$CACHE/kubectl-$KUBECTL_VERSION-linux-$ARCH
    if [ ! -x "$kubectl_binary" ]; then
        mkdir -p "$CACHE"
        curl -fsSL "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/$ARCH/kubectl" -o "$kubectl_binary.part"
        chmod +x "$kubectl_binary.part" && mv "$kubectl_binary.part" "$kubectl_binary"
    fi

    echo "Starting Job $JOB in $NAMESPACE"
    "${K[@]}" apply -f "$REPO/kubernetes/rbac.yaml" >/dev/null
    sed -e "s|\${JOB_NAME}|$JOB|g" -e "s|\${IMAGE}|$IMAGE|g" -e "s|\${DEADLINE_SECONDS}|$DEADLINE|g" \
        -e "s|\${AVOID_POD}|$AVOID_POD|g" -e "s|\${ARCH}|$ARCH|g" "$REPO/kubernetes/performance-job.yaml" | "${K[@]}" apply -f - >/dev/null
    find_pod
    "${K[@]}" wait --for=condition=Ready "$POD" --timeout=900s >/dev/null

    echo "Uploading the tests to $POD"
    git -C "$REPO" ls-files -z | tar -C "$REPO" --null -T - -cf - | put 'tar -xf - -C /work'
    if [ "$FRESH_HISTORY" -eq 1 ]; then
        put 'rm -f /work/src/test/resources/data/performance/*.json /work/src/test/resources/data/performance/*.tex'
    fi
    put 'mkdir -p /work/src/test/resources/config && cat > /work/src/test/resources/config/kubernetes-run.properties' < "$CONFIG"
    put 'mkdir -p /work/bin && cat > /work/bin/kubectl && chmod +x /work/bin/kubectl' < "$kubectl_binary"
    if [ -d "$SNAPSHOT" ]; then
        tar -C "$HOME/.m2/repository" -cf - "org/nrg/nrg_test/$NRG_TEST_VERSION" | put 'mkdir -p /work/.m2/repository && tar -xf - -C /work/.m2/repository'
    fi
    {
        echo 'export PATH=/work/bin:$PATH'
        echo 'cd /work'
        printf 'mvn -B -nsu'
        printf ' %q' "-Dmaven.repo.local=/work/.m2/repository" "-Dnrg_test.version=$NRG_TEST_VERSION" "-Dxnat.config=kubernetes-run.properties"
        [ -n "$TESTS" ] && printf ' %q' "-Dtest=$TESTS"
        [ ${#MVN_ARGS[@]} -gt 0 ] && printf ' %q' "${MVN_ARGS[@]}"
        printf ' test\n'
    } | put 'cat > /work/run.sh'
    put 'touch /work/.ready'
}

if [ -n "$ATTACH" ]; then
    find_pod
else
    start_job
fi

echo "Running; following the log (Ctrl-C detaches; the Job keeps running)"
"${K[@]}" logs -f "$POD" -c tests --since=1m &
LOGS=$!
until "${K[@]}" exec "$POD" -c tests -- test -f /work/.exit 2>/dev/null; do sleep 30; done
kill "$LOGS" 2>/dev/null || true

mkdir -p "$OUT"
put 'cd /work && tar -cf - $(ls -d target/surefire-reports src/test/resources/data/performance xnat_test.log* 2>/dev/null)' | tar -xf - -C "$OUT"
EXIT=$("${K[@]}" exec "$POD" -c tests -- cat /work/.exit)
put 'touch /work/.collected'
COLLECTED=1
echo "The tests exited $EXIT; results are in $OUT"
exit "$EXIT"
