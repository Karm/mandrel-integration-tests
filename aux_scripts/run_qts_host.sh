#!/bin/bash

# Hostname:port to talk to
export COLLECTOR_SERVER=${COLLECTOR_SERVER:-http://127.0.0.1:8084}
# WRITE access token
export TOKEN=${TOKEN:-missing}

# See q_common_modules.sh for more.
export QUARKUS_MODULES="\
rest-client,\
smallrye-config,\
smallrye-graphql,\
vertx,\
main,\
awt,\
hibernate-reactive-mariadb"

if [[ $(grep -s -c https://github.com/quarkusio/quarkus pom.xml) -le 0 ]]; then
  echo "Fatal error. This script is meant to be executed in Quarkus repo root dir"
  exit 1
fi

# Assuming podman...
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true

# Native-image tool
V="$(native-image --version)"
echo "$V"
if [[ $(echo "$V" | wc -l) -ge 3 ]]; then
  export M_VERSION=$(echo $V | sed -n 's/.*Mandrel-\([^ ]*\) .*(build \([^),]*\)).*/\1/p')
  export J_VERSION=$(echo $V | sed -n 's/.*Mandrel-\([^ ]*\) .*(build \([^),]*\)).*/\2/p')
else
  export M_VERSION=$(echo $V | sed -n 's/native-image \([^ ]*\) .*Version \([^)]*\)).*/\1/p')
  export J_VERSION=$(echo $V | sed -n 's/native-image \([^ ]*\) .*Version \([^)]*\)).*/\2/p')
fi
export Q_VERSION=$(sed -n '/quarkus-parent<\/artifactId>/{n;s/.*<version>\([^<]*\)<\/version>.*/\1/p;q}' pom.xml)
cat > /tmp/runner_info.json <<endmsg
{
  "test_version": "Quarkus Integration Testsuite $(git describe --always --long --tags)",
  "graalvm_version": "${M_VERSION}",
  "quarkus_version": "${Q_VERSION}",
  "jdk_version": "${J_VERSION}",
  "operating_system": "$(uname -o)",
  "architecture": "$(uname -m)",
  "memory_size_bytes": $(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo),
  "memory_available_bytes": $(awk '/MemAvailable/ {print $2 * 1024}' /proc/meminfo),
  "description": "Local Mandrel installations",
  "triggered_by": "https://github.com/quarkusio/quarkus/pull/66666666"
}
endmsg
tee -a log.log < /tmp/runner_info.json
export RUNNER_INFO_ID=$( curl -s -w '\n' -H "Content-Type: application/json" -H "token: $TOKEN" \
--post302 --data "@/tmp/runner_info.json" "${COLLECTOR_SERVER}/api/v1/image-stats/runner-info" | jq .id)

if [[ $RUNNER_INFO_ID =~ ^[0-9]+$ ]]; then
    echo "RUNNER_INFO_ID to be used for uploads: $RUNNER_INFO_ID" | tee -a log.log
else
    echo "Fatal error. RUNNER_INFO_ID is not a number." | tee -a log.log
    exit 1
fi

native-image --version | tee -a log.log
export MAVEN_OPTS="-Xmx5g -XX:MaxMetaspaceSize=4g"
./mvnw --batch-mode clean install -Dquickly
./mvnw clean verify -fae -f integration-tests/pom.xml -Dmaven.test.failure.ignore=true \
      --batch-mode -Dno-format -DfailIfNoTests=false -Dnative \
      -pl "${QUARKUS_MODULES}" -Dquarkus.native.native-image-xmx=8g | tee -a log.log
export TAG="${M_VERSION},${Q_VERSION}"
for bs in $(find . -name \*build-output-stats.json); do
    f=$(echo "$bs" | sed 's/\(.*\)-build-output-stats\.json/\1/g')
    ts="${f}-timing-stats.json"
    stat_id=$(curl -s -w '\n' -H "Content-Type: application/json" -H "token: $TOKEN" \
        --post302 --data "@$(pwd)/$bs" "${COLLECTOR_SERVER}/api/v1/image-stats/import?t=$TAG&runnerid=$RUNNER_INFO_ID" | jq .id)
    if [ -e "$ts" ]; then
        curl -s -w '\n' -H "Content-Type: application/json" -H "token: $TOKEN" \
            -X PUT --data "@$ts" "${COLLECTOR_SERVER}/api/v1/image-stats/$stat_id" > /dev/null
    fi
done

# Print the stats we just created:
echo "Stats for RUNNER_INFO_ID: ${RUNNER_INFO_ID}" | tee -a log.log
curl -s -H "token: $TOKEN" "${COLLECTOR_SERVER}/api/v1/image-stats/lookup/runner-info/id?key=${RUNNER_INFO_ID}" | jq | tee -a log.log
