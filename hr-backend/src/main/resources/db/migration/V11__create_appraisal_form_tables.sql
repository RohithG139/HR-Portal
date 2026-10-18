-- ============================================================
-- V11__create_appraisal_form_tables.sql — FRD-08 (Settings foundation) §9
--
-- `appraisal_form`, `form_section` and `form_question` are fully documented in
-- 02-data-model.md (lines 138-171) and FRD-08 v1.2's own "Database
-- Requirements" section, but — found while building this migration, not
-- previously known — were never actually created by V1__init.sql (confirmed:
-- no CREATE TABLE for any of the three in that file). The FRD-08 spec's own
-- §9 statement "all already in 02-data-model.md, no new table required"
-- described the design document, not the applied schema; this is a genuine
-- gap between the two, same kind of drift as D-09, caught here because the
-- Gate-1 seed migration (V12) failed against an empty Testcontainers database
-- with "relation hr.appraisal_form does not exist" the first time it ran.
--
-- DDL transcribed verbatim from 02-data-model.md; audit columns and the
-- surrogate bigint-identity PK follow the same convention as every other
-- table in V1__init.sql (R-DB-2).
-- ============================================================

create table appraisal_form (
  id bigint generated always as identity primary key,
  name varchar(100) not null unique,
  used_for varchar(12) not null check (used_for in ('STANDARD','LEADERSHIP','PROBATION','PROJECT')),
  status varchar(10) not null default 'DRAFT' check (status in ('DRAFT','ACTIVE','ARCHIVED')),
  overall_score_method varchar(20) not null default 'WEIGHTED_AVERAGE' check (overall_score_method in ('WEIGHTED_AVERAGE','MANAGER_DECIDES')),
  label_1 varchar(60) not null default 'Poor',
  label_2 varchar(60) not null default 'Below expectations',
  label_3 varchar(60) not null default 'Meets expectations',
  label_4 varchar(60) not null default 'Exceeds expectations',
  label_5 varchar(60) not null default 'Outstanding',
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);

create table form_section (
  id bigint generated always as identity primary key,
  form_id bigint not null references appraisal_form on delete cascade,
  name varchar(100) not null,
  by_self boolean not null default false,
  by_peer boolean not null default false,
  by_manager boolean not null default false,
  by_hr boolean not null default false,
  sort_order int not null default 0,
  check (by_self or by_peer or by_manager or by_hr),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);

create index ix_form_section_form on form_section(form_id);

create table form_question (
  id bigint generated always as identity primary key,
  section_id bigint not null references form_section on delete cascade,
  text varchar(200) not null,
  description varchar(300),
  answer_type varchar(10) not null check (answer_type in ('SCALE_1_5','YES_NO','TEXT')),
  required boolean not null default true,
  sort_order int not null default 0,
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);

create index ix_form_question_section on form_question(section_id);
