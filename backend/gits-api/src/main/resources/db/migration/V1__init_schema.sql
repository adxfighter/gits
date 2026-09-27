-- GITS v1.0 initial schema. Rationale: docs/adr/0002-db-schema.md

-- Accounts --------------------------------------------------------------

CREATE TABLE company (
    id          uuid PRIMARY KEY,
    name        varchar(200) NOT NULL,
    created_at  timestamptz  NOT NULL
);

CREATE TABLE app_user (
    id             uuid PRIMARY KEY,
    company_id     uuid REFERENCES company (id) ON DELETE RESTRICT,
    email          varchar(320) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    role           varchar(20)  NOT NULL CHECK (role IN ('EMPLOYER', 'ADMIN')),
    display_name   varchar(200),
    enabled        boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT app_user_employer_has_company CHECK (role <> 'EMPLOYER' OR company_id IS NOT NULL)
);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (lower(email));

-- Task bank -------------------------------------------------------------

CREATE TABLE task_template (
    id                uuid PRIMARY KEY,
    code              varchar(20)  NOT NULL UNIQUE,
    title             varchar(300) NOT NULL,
    competencies      jsonb        NOT NULL DEFAULT '[]',
    base_level        varchar(10)  NOT NULL CHECK (base_level IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    difficulty_model  jsonb        NOT NULL DEFAULT '{}',
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL
);

CREATE TABLE task_variant (
    id                 uuid PRIMARY KEY,
    template_id        uuid         NOT NULL REFERENCES task_template (id) ON DELETE RESTRICT,
    code               varchar(30)  NOT NULL UNIQUE,
    kind               varchar(20)  NOT NULL CHECK (kind IN ('TASK', 'CALIBRATION')),
    level              varchar(10)  NOT NULL CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    domain             varchar(100),
    difficulty_params  jsonb        NOT NULL DEFAULT '{}',
    statement_md       text         NOT NULL,
    time_limit_min     integer      NOT NULL CHECK (time_limit_min BETWEEN 1 AND 120),
    content_hash       varchar(64)     NOT NULL,
    status             varchar(20)  NOT NULL CHECK (status IN ('VALIDATED', 'DISABLED')),
    validation_report  jsonb,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL
);
CREATE INDEX task_variant_template_idx ON task_variant (template_id);
CREATE INDEX task_variant_selectable_idx ON task_variant (kind, level) WHERE status = 'VALIDATED';

CREATE TABLE task_file (
    id          uuid PRIMARY KEY,
    variant_id  uuid          NOT NULL REFERENCES task_variant (id) ON DELETE CASCADE,
    kind        varchar(20)   NOT NULL CHECK (kind IN ('STARTER', 'SOLUTION', 'VISIBLE_TEST', 'HIDDEN_TEST', 'READONLY')),
    path        varchar(500)  NOT NULL,
    content     text          NOT NULL,
    editable    boolean       NOT NULL,
    CONSTRAINT task_file_path_uq UNIQUE (variant_id, kind, path)
);

-- Invites and consent ---------------------------------------------------

CREATE TABLE invite (
    id               uuid PRIMARY KEY,
    company_id       uuid         NOT NULL REFERENCES company (id) ON DELETE RESTRICT,
    created_by       uuid         NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    candidate_label  varchar(200) NOT NULL,
    target_level     varchar(10)  NOT NULL CHECK (target_level IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    token_hash       varchar(64)     NOT NULL UNIQUE,
    status           varchar(20)  NOT NULL CHECK (status IN ('CREATED', 'STARTED', 'COMPLETED', 'EXPIRED', 'REVOKED')),
    expires_at       timestamptz  NOT NULL,
    created_at       timestamptz  NOT NULL,
    used_at          timestamptz
);
CREATE INDEX invite_company_idx ON invite (company_id, created_at DESC);

CREATE TABLE consent (
    id           uuid PRIMARY KEY,
    invite_id    uuid         NOT NULL REFERENCES invite (id) ON DELETE CASCADE,
    version      integer      NOT NULL,
    accepted_at  timestamptz  NOT NULL,
    ip_hash      varchar(64),
    user_agent   varchar(500),
    CONSTRAINT consent_invite_version_uq UNIQUE (invite_id, version)
);

-- Assessment sessions ---------------------------------------------------

CREATE TABLE assessment_session (
    id              uuid PRIMARY KEY,
    invite_id       uuid         NOT NULL UNIQUE REFERENCES invite (id) ON DELETE CASCADE,
    status          varchar(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'FINISHED', 'EXPIRED')),
    time_limit_min  integer      NOT NULL,
    random_seed     bigint       NOT NULL,
    started_at      timestamptz  NOT NULL,
    finished_at     timestamptz
);
CREATE INDEX assessment_session_active_idx ON assessment_session (started_at) WHERE status = 'IN_PROGRESS';

CREATE TABLE session_task (
    id             uuid PRIMARY KEY,
    session_id     uuid         NOT NULL REFERENCES assessment_session (id) ON DELETE CASCADE,
    variant_id     uuid         NOT NULL REFERENCES task_variant (id) ON DELETE RESTRICT,
    order_no       integer      NOT NULL,
    kind           varchar(20)  NOT NULL CHECK (kind IN ('TASK', 'CALIBRATION')),
    status         varchar(20)  NOT NULL CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED')),
    current_code   jsonb,
    code_saved_at  timestamptz,
    started_at     timestamptz,
    submitted_at   timestamptz,
    CONSTRAINT session_task_order_uq UNIQUE (session_id, order_no)
);
CREATE INDEX session_task_variant_idx ON session_task (variant_id);

-- Code execution queue --------------------------------------------------

CREATE TABLE run_job (
    id               uuid PRIMARY KEY,
    session_task_id  uuid         NOT NULL REFERENCES session_task (id) ON DELETE CASCADE,
    mode             varchar(10)  NOT NULL CHECK (mode IN ('RUN', 'SUBMIT')),
    status           varchar(10)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'ERROR', 'TIMEOUT')),
    payload          jsonb        NOT NULL,
    worker_id        varchar(100),
    created_at       timestamptz  NOT NULL,
    started_at       timestamptz,
    finished_at      timestamptz
);
-- Queue scan: oldest queued jobs first, see RunJobRepository#claimQueued
CREATE INDEX run_job_queue_idx ON run_job (created_at) WHERE status = 'QUEUED';
CREATE INDEX run_job_session_task_idx ON run_job (session_task_id, created_at DESC);

CREATE TABLE run_result (
    id               uuid PRIMARY KEY,
    run_job_id       uuid         NOT NULL UNIQUE REFERENCES run_job (id) ON DELETE CASCADE,
    compiled         boolean      NOT NULL,
    compile_output   text,
    tests_total      integer      NOT NULL DEFAULT 0,
    tests_passed     integer      NOT NULL DEFAULT 0,
    test_cases       jsonb        NOT NULL DEFAULT '[]',
    duration_ms      bigint,
    stdout_trunc     text,
    stderr_trunc     text,
    created_at       timestamptz  NOT NULL
);

-- Telemetry and results -------------------------------------------------

CREATE TABLE telemetry_batch (
    id               uuid PRIMARY KEY,
    session_task_id  uuid              NOT NULL REFERENCES session_task (id) ON DELETE CASCADE,
    seq              integer           NOT NULL CHECK (seq >= 0),
    client_ts_start  double precision  NOT NULL,
    client_ts_end    double precision  NOT NULL,
    events           jsonb             NOT NULL,
    flags            jsonb             NOT NULL DEFAULT '{}',
    received_at      timestamptz       NOT NULL,
    CONSTRAINT telemetry_batch_seq_uq UNIQUE (session_task_id, seq)
);

CREATE TABLE session_indicators (
    id               uuid PRIMARY KEY,
    session_task_id  uuid         NOT NULL UNIQUE REFERENCES session_task (id) ON DELETE CASCADE,
    indicators       jsonb        NOT NULL,
    trust_level      varchar(10)  NOT NULL CHECK (trust_level IN ('GREEN', 'YELLOW', 'RED')),
    computed_at      timestamptz  NOT NULL
);

CREATE TABLE session_score (
    id                 uuid PRIMARY KEY,
    session_id         uuid          NOT NULL UNIQUE REFERENCES assessment_session (id) ON DELETE CASCADE,
    per_task           jsonb         NOT NULL,
    preliminary_score  numeric(5, 2) NOT NULL CHECK (preliminary_score BETWEEN 0 AND 100),
    computed_at        timestamptz   NOT NULL
);

-- Audit -----------------------------------------------------------------

CREATE TABLE audit_log (
    id         uuid PRIMARY KEY,
    actor      varchar(320) NOT NULL,
    action     varchar(100) NOT NULL,
    entity     varchar(100) NOT NULL,
    entity_id  uuid,
    details    jsonb        NOT NULL DEFAULT '{}',
    at         timestamptz  NOT NULL
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity, entity_id);
