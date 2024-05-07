#!/bin/sh

# Hostname:port to talk to
export COLLECTOR_SERVER=${COLLECTOR_SERVER:-http://127.0.0.1:8084}
# WRITE access token
export TOKEN=${TOKEN:-missing}
# Local Mandrel installation directories
export D=/home/karm/X/JDKs

# Mandrel installations@Quarkus versions
QMS=(
"mandrel-java17-22.3.5.0-Final@2.13.3.Final" \
"mandrel-java17-22.3.5.0-Final@2.16.12.Final" \
"mandrel-java17-23.0.3.0-Final@3.2.12.Final" \
"mandrel-java21-23.1.2.0-Final@3.8.4" \
"mandrel-java21-23.1.2.0-Final@3.9.5" \
"mandrel-java22-24.0.1.0-Final@3.9.5" \
"mandrel-java22-24.0.1.0-Final@3.10.0"
)

# Assuming podman...
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true

cat > /tmp/runner_info.json <<endmsg
{
  "test_version": "Mandrel Integration Testsuite $(git describe --always --long)",
  "operating_system": "$(uname -o)",
  "architecture": "$(uname -m)",
  "memory_size_bytes": $(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo),
  "memory_available_bytes": $(awk '/MemAvailable/ {print $2 * 1024}' /proc/meminfo),
  "description": "Quarkus MP ORM AWT Performance Check, Local Mandrel installations",
  "triggered_by": "Manual run"
}
endmsg

export RUNNER_INFO_ID=$( curl -s -w '\n' -H "Content-Type: application/json" -H "token: $TOKEN" \
--post302 --data "@/tmp/runner_info.json" "${COLLECTOR_SERVER}/api/v1/image-stats/runner-info" | jq .id)

if [[ $RUNNER_INFO_ID =~ ^[0-9]+$ ]]; then
    echo "RUNNER_INFO_ID to be used for uploads: $RUNNER_INFO_ID" | tee -a log.log
else
    echo "Fatal error. RUNNER_INFO_ID is not a number." | tee -a log.log
    exit 1
fi

for QM in "${QMS[@]}"; do
    M=$(echo ${QM} | cut -d@ -f1)
    if [[ ! -d ${D}/${M} ]]; then
        echo "Fatal error. Mandrel installation ${M} not found." | tee -a log.log
        exit 1
    fi
done

for QM in "${QMS[@]}"; do
    M=$(echo ${QM} | cut -d@ -f1)
    Q=$(echo ${QM} | cut -d@ -f2)
    echo "Q:${Q}, M:${M}" | tee -a log.log
    export JAVA_HOME=${D}/${M};export GRAALVM_HOME=${JAVA_HOME};export PATH=${JAVA_HOME}/bin:${PATH}
    native-image --version | tee -a log.log
    mvn clean verify -DincludeTags=perfcheck -Ptestsuite \
        -Dtest=PerfCheckTest\#testQuarkusMPOrmAwtLocal \
        -Dquarkus.version="${Q}" -Dperf.app.report=true \
        -Dperf.app.endpoint="${COLLECTOR_SERVER}" \
        -Dperf.app.secret.token="${TOKEN}" \
        -Dperf.app.runner.info.id="${RUNNER_INFO_ID}" | tee -a log.log
    rm -rf "${Q}_${M}"
    mv ./testsuite/target/archived-logs "${Q}_${M}"
done

# Print the stats we just created:
echo "Stats for RUNNER_INFO_ID: ${RUNNER_INFO_ID}" | tee -a log.log
curl -s -H "token: $TOKEN" "${COLLECTOR_SERVER}/api/v1/image-stats/lookup/runner-info/id?key=${RUNNER_INFO_ID}" | jq | tee -a log.log

# Delete the stats we just created:
export STATS_ID_TO_DELETE=($(curl -s -H "token: $TOKEN" "${COLLECTOR_SERVER}/api/v1/image-stats/lookup/runner-info/id?key=${RUNNER_INFO_ID}" | jq -r '.[].id'))
# Iterate over each ID and send a DELETE request
for ID in "${STATS_ID_TO_DELETE[@]}"; do
    echo "Deleting stats with ID: ${ID}" | tee -a log.log
    curl -X DELETE -H "Content-Type: application/json" -H "token: $TOKEN" "${COLLECTOR_SERVER}/api/v1/image-stats/${ID}" | jq | tee -a log.log
done
# Delete the now unused runner-info
echo "Deleting runner-info with ID: ${RUNNER_INFO_ID}" | tee -a log.log
curl -X DELETE -H "Content-Type: application/json" -H "token: $TOKEN" "${COLLECTOR_SERVER}/api/v1/image-stats/runner-info/${RUNNER_INFO_ID}" | jq | tee -a log.log
