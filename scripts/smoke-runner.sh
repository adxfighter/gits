#!/usr/bin/env bash
# End-to-end check of the containerised runner: inserts a task and a SUBMIT job straight into PostgreSQL,
# waits for the runner to execute it in a sandbox container and checks the stored result.
# Requires a running stack (scripts/up.sh) and the sandbox image.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'; }
DB_USER="$(env_value POSTGRES_USER)"
DB_NAME="$(env_value POSTGRES_DB)"
psql() { docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -qtA -U "$DB_USER" -d "$DB_NAME" "$@"; }

suffix="$(date +%s)$RANDOM"
job_id="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || powershell.exe -NoProfile -Command '[guid]::NewGuid().ToString()' | tr -d '\r')"

psql <<SQL
BEGIN;
INSERT INTO company (id, name, created_at) VALUES (gen_random_uuid(), 'smoke-runner-$suffix', now());
INSERT INTO app_user (id, company_id, email, password_hash, role, created_at)
  SELECT gen_random_uuid(), id, 'smoke-$suffix@test.local', '{noop}x', 'EMPLOYER', now() FROM company WHERE name = 'smoke-runner-$suffix';
INSERT INTO task_template (id, code, title, base_level, created_at, updated_at)
  VALUES (gen_random_uuid(), 'S$suffix', 'smoke', 'JUNIOR', now(), now());
INSERT INTO task_variant (id, template_id, code, kind, level, statement_md, time_limit_min, content_hash, status, created_at, updated_at)
  SELECT gen_random_uuid(), id, 'S$suffix-v01', 'TASK', 'JUNIOR', '#', 20, repeat('0', 64), 'VALIDATED', now(), now()
  FROM task_template WHERE code = 'S$suffix';
INSERT INTO task_file (id, variant_id, kind, path, content, editable)
  SELECT gen_random_uuid(), id, 'STARTER', 'src/main/java/demo/Twice.java',
         'package demo; public class Twice { public static int of(int x) { return x + x + 1; } }', true
  FROM task_variant WHERE code = 'S$suffix-v01';
INSERT INTO task_file (id, variant_id, kind, path, content, editable)
  SELECT gen_random_uuid(), id, 'HIDDEN_TEST', 'src/test/java/demo/TwiceTest.java',
         'package demo; import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
          class TwiceTest { @Test void two() { assertEquals(4, Twice.of(2)); } @Test void zero() { assertEquals(0, Twice.of(0)); } }', false
  FROM task_variant WHERE code = 'S$suffix-v01';
INSERT INTO invite (id, company_id, created_by, candidate_label, target_level, token_hash, status, expires_at, created_at)
  SELECT gen_random_uuid(), c.id, u.id, 'smoke', 'JUNIOR', md5('$suffix') || md5('x$suffix'), 'STARTED', now() + interval '1 day', now()
  FROM company c JOIN app_user u ON u.company_id = c.id WHERE c.name = 'smoke-runner-$suffix';
INSERT INTO assessment_session (id, invite_id, status, time_limit_min, random_seed, started_at)
  SELECT gen_random_uuid(), i.id, 'IN_PROGRESS', 90, 1, now() FROM invite i WHERE i.token_hash = md5('$suffix') || md5('x$suffix');
INSERT INTO session_task (id, session_id, variant_id, order_no, kind, status)
  SELECT gen_random_uuid(), s.id, v.id, 1, 'TASK', 'IN_PROGRESS'
  FROM assessment_session s JOIN invite i ON i.id = s.invite_id, task_variant v
  WHERE i.token_hash = md5('$suffix') || md5('x$suffix') AND v.code = 'S$suffix-v01';
INSERT INTO run_job (id, session_task_id, mode, status, payload, created_at)
  SELECT '$job_id', st.id, 'SUBMIT', 'QUEUED',
         jsonb_build_object('src/main/java/demo/Twice.java', 'package demo; public class Twice { public static int of(int x) { return 2 * x; } }'),
         now()
  FROM session_task st JOIN task_variant v ON v.id = st.variant_id WHERE v.code = 'S$suffix-v01';
COMMIT;
SQL
echo "queued job $job_id"

for _ in $(seq 1 60); do
  status="$(psql -c "SELECT status FROM run_job WHERE id = '$job_id'" | tr -d '[:space:]')"
  case "$status" in DONE|ERROR|TIMEOUT) break ;; esac
  sleep 1
done
result="$(psql -c "SELECT r.compiled || ' ' || r.tests_passed || '/' || r.tests_total || ' ' || (r.test_cases::text LIKE '%Скрытый тест 1%') FROM run_result r WHERE r.run_job_id = '$job_id'" | tr -d '\r')"
echo "status=$status result=$result"
[[ "$status" == "DONE" && "$result" == "true 2/2 true" ]] || { docker compose logs --tail 40 runner; echo "smoke-runner: FAILED" >&2; exit 1; }
echo "smoke-runner: runner executed the job in a sandbox container"
