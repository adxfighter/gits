#!/usr/bin/env bash
# Builds gits-sandbox-java and proves its isolation with hostile programs (sandbox/java/selftest).
# Each case runs with exactly the flags gits-runner uses (see sandbox/java/README.md).
set -uo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1  # Git Bash on Windows: do not rewrite /work, /tmp in docker arguments

IMAGE="${SANDBOX_IMAGE:-gits-sandbox-java:local}"
RUNTIME="${SANDBOX_RUNTIME:-runc}"
TIMEOUT_SECONDS="${SANDBOX_TIMEOUT:-30}"
LOOP_TIMEOUT_SECONDS=10
CASES_DIR=sandbox/java/selftest
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SANDBOX_FLAGS=(
  --rm -i
  --network=none
  --hostname localhost  # the JUnit XML report resolves the hostname; without DNS this would stall ~15 s
  --read-only
  --tmpfs /work:rw,nosuid,nodev,size=64m,mode=1777
  --tmpfs /tmp:rw,nosuid,nodev,size=16m,mode=1777
  --memory=768m --memory-swap=768m
  --cpus=1
  --pids-limit=128
  --cap-drop=ALL
  --security-opt=no-new-privileges
  --user 10001:10001
  --label gits.sandbox=true
  --runtime "$RUNTIME"
)

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "Building $IMAGE"
  docker build -q -t "$IMAGE" sandbox/java >/dev/null || { echo "image build failed" >&2; exit 1; }
fi

failures=0
pass() { echo "PASS  $1 ($2)"; }
fail() { echo "FAIL  $1: $2"; failures=$((failures + 1)); }

# run_case <name> <timeout> -> sets RC, OUT, ELAPSED_MS, TIMED_OUT
run_case() {
  local name=$1 limit=$2 container="gits-selftest-$1-$$"
  tar -C "$CASES_DIR/$name" -cf "$WORK/$name.tar" src
  OUT="$WORK/$name.out"
  local start; start=$(date +%s%N)
  docker run --name "$container" "${SANDBOX_FLAGS[@]}" "$IMAGE" <"$WORK/$name.tar" >"$OUT" 2>&1 &
  local pid=$! waited=0
  TIMED_OUT=0
  while kill -0 "$pid" 2>/dev/null; do
    if (( waited >= limit * 5 )); then
      docker kill "$container" >/dev/null 2>&1
      TIMED_OUT=1
      break
    fi
    sleep 0.2
    waited=$((waited + 1))
  done
  wait "$pid"; RC=$?
  ELAPSED_MS=$(( ($(date +%s%N) - start) / 1000000 ))
}

report_attr() { sed -n 's/.*<testsuite [^>]*'"$1"'="\([0-9]*\)".*/\1/p' "$OUT" | head -1; }

expect_all_pass() {
  local name=$1
  run_case "$name" "$TIMEOUT_SECONDS"
  local tests failures_ errors
  tests=$(report_attr tests); failures_=$(report_attr failures); errors=$(report_attr errors)
  if (( TIMED_OUT )); then fail "$name" "timed out"; return; fi
  if [[ -z "$tests" || "$tests" == 0 ]]; then fail "$name" "no JUnit report: $(tail -c 1500 "$OUT")"; return; fi
  if [[ "$failures_" != 0 || "$errors" != 0 ]]; then
    fail "$name" "tests=$tests failures=$failures_ errors=$errors: $(sed -n '/===GITS-OUTPUT-BEGIN===/,/===GITS-OUTPUT-END===/p' "$OUT" | tail -c 2500)"
    return
  fi
  pass "$name" "$tests tests, ${ELAPSED_MS} ms"
}

# 1. Normal code: compiles, JUnit + AssertJ + Mockito work, and it is fast enough
expect_all_pass ok-simple
if (( ELAPSED_MS > 6000 )); then
  echo "WARN  ok-simple took ${ELAPSED_MS} ms (target: <= 4000 ms on a warm machine)"
fi

# 2. Compilation error is reported with the marker and exit code 2
run_case compile-error "$TIMEOUT_SECONDS"
if [[ $RC == 2 ]] && grep -q '===GITS-COMPILE-ERROR===' "$OUT" && grep -q 'incompatible types' "$OUT"; then
  pass compile-error "exit 2, javac message"
else
  fail compile-error "rc=$RC output: $(tail -c 800 "$OUT")"
fi

# 3-8. Hostile programs whose tests pass only when the sandbox blocks them
expect_all_pass network-blocked
expect_all_pass fork-bomb
expect_all_pass memory-bomb
expect_all_pass fs-readonly
expect_all_pass no-secrets

# 9. Infinite loop is stopped by the caller's timeout
run_case infinite-loop "$LOOP_TIMEOUT_SECONDS"
if (( TIMED_OUT )); then pass infinite-loop "killed after ${LOOP_TIMEOUT_SECONDS}s"; else fail infinite-loop "finished by itself: rc=$RC"; fi

# 10. Output flood is capped and ends the run
run_case output-flood "$TIMEOUT_SECONDS"
size=$(wc -c <"$OUT")
if (( ! TIMED_OUT )) && (( size < 70000 )) && grep -q '===GITS-REPORT-END===' "$OUT"; then
  pass output-flood "output capped at ${size} bytes"
else
  fail output-flood "timed_out=$TIMED_OUT size=$size"
fi

leftovers=$(docker ps -aq --filter label=gits.sandbox=true --filter name=gits-selftest- | wc -l)
if (( leftovers > 0 )); then fail cleanup "$leftovers selftest containers left"; else pass cleanup "no containers left"; fi

if (( failures > 0 )); then
  echo "sandbox-selftest: $failures check(s) FAILED"
  exit 1
fi
echo "sandbox-selftest: all checks passed"
