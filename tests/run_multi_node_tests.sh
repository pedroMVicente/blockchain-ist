#!/usr/bin/env bash
set -euo pipefail

################################################
### CONFIGURATION
TESTS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MN_DIR="${TESTS_DIR}/multi-node"
INPUTS="${MN_DIR}/inputs"
EXPECTED="${MN_DIR}/outputs"
TEST_OUTPUTS="${MN_DIR}/test-outputs"
LOGS="${MN_DIR}/logs"
MVN_ROOT_DIR="$(cd -- "${TESTS_DIR}/.." && pwd)"
MVN_ROOT_POM="${MVN_ROOT_DIR}/pom.xml"

SEQ_PORT=3001
NODE_A_PORT=2001
NODE_B_PORT=2002
BLOCK_SIZE=1
BLOCK_TIMEOUT=1

ORGA="localhost:${NODE_A_PORT}:OrgA"
ORGB="localhost:${NODE_B_PORT}:OrgB"
BOTH="${ORGA} ${ORGB}"

################################################
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'
################################################

PIDS=()

cleanup() {
    printf "\n${CYAN}Stopping services...${NC}\n"
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    printf "${CYAN}Done.${NC}\n"
}
trap cleanup EXIT INT TERM

wait_for_port() {
    local port=$1
    local timeout=${2:-30}
    local name=${3:-"service"}
    echo -n "  Waiting for ${name} on port ${port}..."
    for (( i=0; i<timeout; i++ )); do
        if (echo > /dev/tcp/localhost/$port) 2>/dev/null; then
            echo " ready"
            return 0
        fi
        sleep 1
    done
    echo " TIMEOUT"
    return 1
}

exec_client() {
    local client_args="$1"
    local input="$2"
    local output="$3"
    mvn --quiet -f "$MVN_ROOT_POM" -pl client exec:java \
        -Dexec.args="${client_args}" < "$input" > "$output" 2>/dev/null
}

################################################
### START SERVICES
################################################

rm -rf "$TEST_OUTPUTS" "$LOGS"
mkdir -p "$TEST_OUTPUTS" "$LOGS"

printf "${CYAN}Starting sequencer (port ${SEQ_PORT}, blockSize=${BLOCK_SIZE}, timeout=${BLOCK_TIMEOUT})...${NC}\n"
mvn --quiet -f "$MVN_ROOT_POM" -pl sequencer exec:java \
    -Dsequencer.port=${SEQ_PORT} \
    -Dblock.size=${BLOCK_SIZE} \
    -Dblock.timeout=${BLOCK_TIMEOUT} \
    > "${LOGS}/sequencer.log" 2>&1 &
PIDS+=($!)
wait_for_port $SEQ_PORT 30 "sequencer"

printf "${CYAN}Starting node OrgA (port ${NODE_A_PORT})...${NC}\n"
mvn --quiet -f "$MVN_ROOT_POM" -pl node exec:java \
    -Dnode.port=${NODE_A_PORT} \
    -Dnode.org=OrgA \
    -Dsequencer.host=localhost \
    -Dsequencer.port=${SEQ_PORT} \
    > "${LOGS}/node_a.log" 2>&1 &
PIDS+=($!)
wait_for_port $NODE_A_PORT 30 "node OrgA"

printf "${CYAN}Starting node OrgB (port ${NODE_B_PORT})...${NC}\n"
mvn --quiet -f "$MVN_ROOT_POM" -pl node exec:java \
    -Dnode.port=${NODE_B_PORT} \
    -Dnode.org=OrgB \
    -Dsequencer.host=localhost \
    -Dsequencer.port=${SEQ_PORT} \
    > "${LOGS}/node_b.log" 2>&1 &
PIDS+=($!)
wait_for_port $NODE_B_PORT 30 "node OrgB"

sleep 2

################################################
### TEST RUNNER
################################################

passed=0
failed=0

run_test() {
    local name="$1"
    local args="$2"
    local input="$3"
    local expected_out="$4"
    local actual="${TEST_OUTPUTS}/${name}.txt"

    exec_client "$args" "$input" "$actual"

    if diff -u "$expected_out" "$actual" > /dev/null 2>&1; then
        printf "${GREEN}[%s] PASSED${NC}\n" "$name"
        passed=$((passed+1))
    else
        printf "${RED}[%s] FAILED${NC}\n" "$name"
        diff -u "$expected_out" "$actual" || true
        echo ""
        failed=$((failed+1))
    fi
}

printf "\n${CYAN}=========================================${NC}\n"
printf "${CYAN}Running multi-node tests${NC}\n"
printf "${CYAN}=========================================${NC}\n\n"

# -----------------------------------------------------------------------
#  MN01 — Cross-org transfer + multi-node balance consistency
# -----------------------------------------------------------------------
run_test "mn01" "$BOTH" \
    "${INPUTS}/mn01.txt" "${EXPECTED}/mn01.txt"

# -----------------------------------------------------------------------
#  MN02 — Bidirectional cross-org transfers
# -----------------------------------------------------------------------
run_test "mn02" "$BOTH" \
    "${INPUTS}/mn02.txt" "${EXPECTED}/mn02.txt"

# -----------------------------------------------------------------------
#  MN03 — Cross-org error cases (insufficient balance + wrong owner)
# -----------------------------------------------------------------------
run_test "mn03" "$BOTH" \
    "${INPUTS}/mn03.txt" "${EXPECTED}/mn03.txt"

# -----------------------------------------------------------------------
#  MN04 — Concurrent clients on different nodes
# -----------------------------------------------------------------------
printf "${CYAN}[mn04] Setup phase...${NC}\n"
exec_client "$BOTH" "${INPUTS}/mn04_setup.txt" "${TEST_OUTPUTS}/mn04_setup.txt"
setup_ok=0
if ! diff -u "${EXPECTED}/mn04_setup.txt" "${TEST_OUTPUTS}/mn04_setup.txt" > /dev/null 2>&1; then
    setup_ok=1
fi

printf "${CYAN}[mn04] Concurrent phase (two clients in parallel)...${NC}\n"
exec_client "$ORGA" "${INPUTS}/mn04_a.txt" "${TEST_OUTPUTS}/mn04_a.txt" &
PID_A=$!
exec_client "$ORGB" "${INPUTS}/mn04_b.txt" "${TEST_OUTPUTS}/mn04_b.txt" &
PID_B=$!
wait $PID_A $PID_B || true

diff_a=0
diff_b=0
if ! diff -u "${EXPECTED}/mn04_a.txt" "${TEST_OUTPUTS}/mn04_a.txt" > /dev/null 2>&1; then
    diff_a=1
fi
if ! diff -u "${EXPECTED}/mn04_b.txt" "${TEST_OUTPUTS}/mn04_b.txt" > /dev/null 2>&1; then
    diff_b=1
fi

sleep 3

printf "${CYAN}[mn04] Verify phase...${NC}\n"
exec_client "$BOTH" "${INPUTS}/mn04_verify.txt" "${TEST_OUTPUTS}/mn04_verify.txt"
diff_v=0
if ! diff -u "${EXPECTED}/mn04_verify.txt" "${TEST_OUTPUTS}/mn04_verify.txt" > /dev/null 2>&1; then
    diff_v=1
fi

if [ $setup_ok -eq 0 ] && [ $diff_a -eq 0 ] && [ $diff_b -eq 0 ] && [ $diff_v -eq 0 ]; then
    printf "${GREEN}[mn04] PASSED${NC}\n"
    passed=$((passed+1))
else
    printf "${RED}[mn04] FAILED${NC}\n"
    [ $setup_ok -ne 0 ] && echo "  Setup diff:" && diff -u "${EXPECTED}/mn04_setup.txt" "${TEST_OUTPUTS}/mn04_setup.txt" || true
    [ $diff_a -ne 0 ] && echo "  Client A diff:" && diff -u "${EXPECTED}/mn04_a.txt" "${TEST_OUTPUTS}/mn04_a.txt" || true
    [ $diff_b -ne 0 ] && echo "  Client B diff:" && diff -u "${EXPECTED}/mn04_b.txt" "${TEST_OUTPUTS}/mn04_b.txt" || true
    [ $diff_v -ne 0 ] && echo "  Verify diff:" && diff -u "${EXPECTED}/mn04_verify.txt" "${TEST_OUTPUTS}/mn04_verify.txt" || true
    echo ""
    failed=$((failed+1))
fi

################################################
### SUMMARY
################################################

printf "\n${CYAN}=========================================${NC}\n"
printf "Results: ${GREEN}%d passed${NC}, ${RED}%d failed${NC}\n" "$passed" "$failed"
printf "${CYAN}=========================================${NC}\n"
echo "Test outputs: ${TEST_OUTPUTS}"
echo "Service logs: ${LOGS}"

if [ "$failed" -gt 0 ]; then
    exit 1
fi
