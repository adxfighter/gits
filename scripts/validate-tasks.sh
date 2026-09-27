#!/usr/bin/env bash
# Validates the task bank in sandbox containers and writes validation.json next to each variant.
#   scripts/validate-tasks.sh                      all variants
#   scripts/validate-tasks.sh --variant T01        one template (or --variant T01-v03 for one variant)
#   scripts/validate-tasks.sh --changed-since origin/main   only variants changed since a git ref (CI)
# Extra options (--runs, --parallel, --image, --runtime) are passed to the validator.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

JAR=backend/gits-taskbank/target/gits-taskbank.jar
IMAGE="${SANDBOX_IMAGE:-gits-sandbox-java:local}"
JAVA_OPTS="-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

changed_since=""
args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --changed-since) changed_since="$2"; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Building sandbox image $IMAGE"
  docker build -q -t "$IMAGE" sandbox/java >/dev/null
fi
if [[ ! -f "$JAR" || "${REBUILD:-0}" == "1" ]]; then
  (cd backend && mvn -B -q -pl gits-taskbank -am package -DskipTests)
fi

if [[ -z "$changed_since" ]]; then
  java $JAVA_OPTS -jar "$JAR" validate tasks/java "${args[@]}"
  exit $?
fi

# Variant codes (T01-v03) touched since the ref, excluding validation.json-only changes
mapfile -t variants < <(git diff --name-only "$changed_since" -- tasks/java \
  | grep -v '/validation.json$' \
  | sed -nE 's#^tasks/java/(T[0-9]{2}|CAL)-[^/]+/variants/(v[0-9]{2})/.*#\1-\2#p' | sort -u)
if [[ ${#variants[@]} -eq 0 ]]; then
  echo "No changed variants since $changed_since"
  exit 0
fi
status=0
for variant in "${variants[@]}"; do
  java $JAVA_OPTS -jar "$JAR" validate tasks/java --variant "$variant" "${args[@]}" || status=1
done
exit $status
