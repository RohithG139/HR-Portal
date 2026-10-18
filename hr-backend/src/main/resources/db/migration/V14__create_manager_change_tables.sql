-- ============================================================
-- V14__create_manager_change_tables.sql — FRD-01 (Wave 3, Transfer manager) Gate 1
--
-- `manager_change` is fully documented in 02-data-model.md (lines 356-375) and
-- FRD-01 v1.2 §5, but — found while building this migration, not previously
-- known — was never actually created by V1__init.sql, despite the FRD-01 spec's
-- own §9 saying it was "already in the baseline, all already migrated by F01's
-- gate 1." Same category of gap as D-09/the appraisal_form tables/the report
-- tables found in earlier waves: documented in the design doc, never applied.
--
-- `manager_change_task` is the table the FRD-01 spec's own D-W3 and
-- 02-data-model.md §6 item 14 both independently flag as missing: a single
-- BOOLEAN (`move_open_tasks`) cannot represent "HR ticks individual tasks to
-- move" (FRD-01's Transfer manager step 3, AC-W02). User-approved 2026-10-17.
--
-- `manager_change.project_id` references `project`, already created by
-- V13__create_report_tables_and_views.sql (Wave 2). `manager_change_task`'s
-- `engine_task_id` column (named to match the FRD-01 spec's own DDL, §9)
-- is a bigint FK to engine_task's real surrogate PK `id` (added by
-- V3__engine_task_surrogate_id.sql) — an unqualified `references engine_task`
-- resolves to that primary key, not the varchar natural-key column that
-- happens to share the same name.
-- ============================================================

create table manager_change (
  id                bigint       generated always as identity primary key,
  scope             varchar(8)   not null check (scope in ('LINE','PROJECT')),
  employee_id       bigint       references employee,          -- LINE: who moves
  project_id        bigint       references project,           -- PROJECT: whose PM changes
  parent_id         bigint       references manager_change,    -- LINE rows created by a PROJECT change
  from_manager_id   bigint       references employee,          -- snapshot at request time
  to_manager_id     bigint       not null references employee,
  effective_date    date         not null,
  reason            text,
  move_open_tasks   boolean      not null default true,
  status            varchar(10)  not null default 'SCHEDULED'
                      check (status in ('SCHEDULED','APPLIED','CANCELLED')),
  applied_at        timestamptz,
  requested_by      bigint       not null references employee,
  check (num_nonnulls(employee_id, project_id) = 1),
  check ((scope = 'LINE') = (employee_id is not null)),
  check (parent_id is null or scope = 'LINE'),
  check (scope = 'LINE' or reason is not null),
  check (from_manager_id is distinct from to_manager_id),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);

create index ix_manager_change_due on manager_change(effective_date) where status = 'SCHEDULED';

create table manager_change_task (
  change_id      bigint not null references manager_change on delete cascade,
  engine_task_id bigint not null references engine_task,
  primary key (change_id, engine_task_id)
);
