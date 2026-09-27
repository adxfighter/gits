#!/usr/bin/env bash
# End-to-end check of employer login, invite creation and candidate entry through nginx with real cookies.
# Requires a running stack (scripts/up.sh) and DEMO_EMPLOYER_* in .env.
set -euo pipefail
cd "$(dirname "$0")/.."

env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'; }
base="http://localhost:$(env_value WEB_PORT)/api"
email="$(env_value DEMO_EMPLOYER_EMAIL)"
password="$(env_value DEMO_EMPLOYER_PASSWORD)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$1" | tail -1; }
# status <jar> <method> <path> [json-body]  -> prints HTTP status, body in $work/body
status() {
  local jar=$1 method=$2 path=$3 data=${4:-}
  local args=(-s -o "$work/body" -w '%{http_code}' -b "$jar" -c "$jar" -X "$method" "$base$path"
              -H "X-XSRF-TOKEN: $(xsrf "$jar")")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' --data "$data")
  curl "${args[@]}"
}
expect() { local want=$1 got=$2 what=$3; [[ "$got" == "$want" ]] || fail "$what: expected $want, got $got ($(cat "$work/body"))"; echo "ok  $what ($got)"; }

employer="$work/employer.jar"; candidate="$work/candidate.jar"
touch "$employer" "$candidate"

expect 204 "$(status "$employer" GET /auth/csrf)" "employer gets CSRF cookie"
expect 401 "$(status "$employer" GET /auth/me)" "anonymous /auth/me"
login_body=$(printf '{"email":"%s","password":"%s"}' "$email" "$password")
expect 200 "$(status "$employer" POST /auth/login "$login_body")" "employer login"
expect 200 "$(status "$employer" GET /auth/me)" "employer /auth/me"
expect 201 "$(status "$employer" POST /invites '{"candidateLabel":"Smoke test","targetLevel":"MIDDLE"}')" "create invite"
token=$(sed -E 's/.*"link":"[^"]*\/c\/([^"]+)".*/\1/' "$work/body")
[[ ${#token} -eq 43 ]] || fail "unexpected invite token in $(cat "$work/body")"

expect 204 "$(status "$candidate" GET /auth/csrf)" "candidate gets CSRF cookie"
expect 200 "$(status "$candidate" POST /candidate/enter "{\"token\":\"$token\"}")" "candidate enters by link"
grep -q 'GITS_CANDIDATE' "$candidate" || fail "no candidate cookie stored"
expect 200 "$(status "$candidate" GET /candidate/me)" "candidate /me"
expect 403 "$(status "$candidate" GET /candidate/session)" "no access before consent"
expect 204 "$(status "$candidate" POST /candidate/consent)" "accept consent"
expect 404 "$(status "$candidate" GET /candidate/session)" "past consent gate (endpoint arrives in P07)"
expect 401 "$(status "$candidate" GET /invites)" "candidate cannot use employer API"
# Same browser: candidate cookie (path /api/candidate) and employer session must coexist
expect 200 "$(status "$employer" POST /candidate/enter "{\"token\":\"$token\"}")" "candidate enters in the employer browser"
expect 200 "$(status "$employer" GET /candidate/me)" "candidate /me in the employer browser"
expect 200 "$(status "$employer" GET /auth/me)" "employer session still valid in the same browser"
echo "smoke-auth: all checks passed"
