-- ============================================================
-- V13__create_report_tables_and_views.sql  — FRD-08 Reports (Gate 2)
--
-- Creates the tables and views that the 8 report endpoints (C31-C47)
-- read from.  All table DDL is taken verbatim from 02-data-model.md;
-- audit columns follow the V1__init.sql convention (R-DB-2).
--
-- These tables belong to future features (F03-F08) but must exist now
-- so the FRD-08 report queries and their Testcontainers tests work.
-- Future feature migrations will build on top of these tables — they
-- share the same DDL, so there is no conflict.
--
-- Flyway sets search_path = hr before running, so no hr. prefix needed
-- in this file (unlike JdbcTemplate queries in the application layer).
--
-- Creation order (respects FK dependencies):
--   1  client
--   2  project              → client, employee
--   3  project_assignment   → project, employee
--   4  client_feedback      → project, employee, document
--   5  employee_exit        → employee, document
--   6  development_plan     → employee
--   7  appraisal_round      → appraisal_form
--   8  appraisal            → appraisal_round, employee, job_title
--   9  pip                  → employee (source FKs added later)
--  10  review               → appraisal_form, employee, appraisal, project_assignment, contract
--  11  ALTER pip            → add FK to review
--  12  checkin              → development_plan, pip
--  13  ALTER pip            → add FK to checkin
-- Views:
--  14  v_bench
--  15  v_employee_allocation
--  16  v_appraisal_status
--  17  v_pip
--  18  v_upcoming_dates
--  19  v_unreturned_assets
-- ============================================================


-- ============================================================
-- 1. client
-- ============================================================
create table client (
  id       bigint       generated always as identity primary key,
  name     varchar(150) not null unique,
  industry varchar(80),
  address  varchar(250),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);


-- ============================================================
-- 2. project
-- ============================================================
create table project (
  id                 bigint       generated always as identity primary key,
  project_code       varchar(10)  not null unique,
  name               varchar(150) not null,
  client_id          bigint       not null references client,
  project_manager_id bigint       not null references employee,
  project_type       varchar(20)  not null
                       check (project_type in ('FIXED_PRICE','TIME_MATERIAL','INTERNAL')),
  status             varchar(10)  not null
                       check (status in ('PLANNED','ACTIVE','ON_HOLD','OVERDUE','COMPLETED','WITHDRAWN')),
  start_date         date         not null,
  end_date           date,
  work_mode          varchar(100),
  tech_stack         varchar(250),
  description        text,
  contract_reference varchar(60),
  withdrawal_date    date,
  withdrawal_reason  text,
  closed_at          timestamptz,
  check (end_date is null or end_date > start_date),
  check ((status = 'WITHDRAWN') = (withdrawal_date is not null and withdrawal_reason is not null)),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);


-- ============================================================
-- 3. project_assignment
-- ============================================================
create table project_assignment (
  id             bigint    generated always as identity primary key,
  project_id     bigint    not null references project,
  employee_id    bigint    not null references employee,
  role_on_project varchar(80) not null,
  allocation_pct smallint  not null check (allocation_pct between 1 and 100),
  start_date     date      not null,
  end_date       date,
  end_reason     varchar(20)
                   check (end_reason in ('PROJECT_COMPLETED','PROJECT_WITHDRAWN',
                          'ENDED_EARLY','EMPLOYEE_LEAVING')),
  check (end_date is null or end_date >= start_date),
  check (end_reason is null or end_date is not null),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create index ix_assignment_emp on project_assignment(employee_id, end_date);


-- ============================================================
-- 4. client_feedback
-- ============================================================
create table client_feedback (
  id                    bigint    generated always as identity primary key,
  project_id            bigint    not null references project,
  applies_to            varchar(10) not null check (applies_to in ('TEAM','PERSON')),
  employee_id           bigint    references employee,
  contact_name          varchar(150) not null,
  contact_role          varchar(80),
  received_date         date      not null,
  collected_via         varchar(10) not null
                          check (collected_via in ('CALL','EMAIL','MEETING','SURVEY')),
  rating                smallint  not null check (rating between 1 and 5),
  comments              text      not null,
  attachment_document_id bigint   references document,
  recorded_by           bigint    not null references employee,
  check ((applies_to = 'PERSON') = (employee_id is not null)),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);


-- ============================================================
-- 5. employee_exit
-- ============================================================
create table employee_exit (
  id                         bigint    generated always as identity primary key,
  employee_id                bigint    not null references employee,
  exit_type                  varchar(20) not null
                               check (exit_type in ('RESIGNATION','END_OF_CONTRACT',
                                      'TERMINATION','PIP_NOT_MET','PROBATION_NOT_PASSED')),
  initiated_by               bigint    references employee,
  initiated_by_role          varchar(10) not null
                               check (initiated_by_role in ('EMPLOYEE','MANAGER','HR','SYSTEM')),
  notice_date                date      not null,
  requested_last_day         date,
  last_working_day           date      not null,
  message                    text,
  letter_document_id         bigint    references document,
  notes                      text,
  status                     varchar(12) not null default 'REQUESTED'
                               check (status in ('REQUESTED','APPROVED','REJECTED',
                                      'IN_PROGRESS','CLOSED')),
  approved_by                bigint    references employee,
  approved_at                timestamptz,
  closed_at                  timestamptz,
  interview_needed           boolean   not null default true,
  interview_planned_on       date,
  interview_held_on          date,
  interview_held_by          bigint    references employee,
  interview_main_reason      varchar(20)
                               check (interview_main_reason in
                                      ('RELOCATION','CAREER_GROWTH','COMPENSATION','OTHER')),
  interview_experience_rating smallint check (interview_experience_rating between 1 and 5),
  interview_improvements     text,
  interview_would_recommend  text,
  check (last_working_day >= notice_date),
  check (requested_last_day is null or requested_last_day > notice_date),
  check (exit_type = 'RESIGNATION'
         or (requested_last_day is null and message is null and letter_document_id is null)),
  check ((initiated_by_role = 'SYSTEM') = (initiated_by is null)),
  check (interview_held_on is null
         or (interview_held_by is not null and interview_main_reason is not null)),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create unique index ux_exit_active on employee_exit(employee_id) where status <> 'REJECTED';


-- ============================================================
-- 6. development_plan
-- ============================================================
create table development_plan (
  id                  bigint      generated always as identity primary key,
  employee_id         bigint      not null references employee,
  period_label        varchar(40),
  status              varchar(20) not null default 'DRAFT'
                        check (status in ('DRAFT','SUBMITTED','CHANGES_REQUESTED',
                               'ACTIVE','PAUSED','PERFORMED','CLOSED','CANCELLED')),
  submitted_at        timestamptz,
  evaluation_text     text,
  evaluation_outcome  varchar(15)
                        check (evaluation_outcome in ('PLAN_CLOSED','FURTHER_NEEDS')),
  evaluated_by        bigint      references employee,
  evaluated_at        timestamptz,
  previous_plan_id    bigint      references development_plan,
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create unique index ux_plan_open on development_plan(employee_id)
  where status not in ('CLOSED','CANCELLED');


-- ============================================================
-- 7. appraisal_round
-- ============================================================
create table appraisal_round (
  id                   bigint    generated always as identity primary key,
  name                 varchar(100) not null unique,
  review_period_start  date      not null,
  review_period_end    date      not null,
  standard_form_id     bigint    not null references appraisal_form,
  leadership_form_id   bigint    references appraisal_form,
  self_peer_due        date      not null,
  manager_due          date      not null,
  hr_due               date      not null,
  eligibility_date     date      not null,
  peer_mode            varchar(10) not null default 'REQUIRED'
                         check (peer_mode in ('REQUIRED','OPTIONAL','OFF')),
  peer_anonymous       boolean   not null default true,
  status               varchar(8)  not null default 'DRAFT'
                         check (status in ('DRAFT','OPEN','CLOSED')),
  started_at           timestamptz,
  started_by           bigint    references employee,
  closed_at            timestamptz,
  closed_by            bigint    references employee,
  check (review_period_end > review_period_start),
  check (self_peer_due < manager_due and manager_due < hr_due),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
-- APR-01: at most one open round at a time
create unique index ux_one_open_round on appraisal_round((true)) where status = 'OPEN';


-- ============================================================
-- 8. appraisal
-- ============================================================
create table appraisal (
  id                   bigint      generated always as identity primary key,
  round_id             bigint      not null references appraisal_round,
  employee_id          bigint      not null references employee,
  status               varchar(10) not null default 'OPEN'
                         check (status in ('OPEN','FINALISED','CANCELLED')),
  outcome              varchar(20)
                         check (outcome in ('PROMOTED','NO_CHANGE','UNDER_PERFORMING')),
  final_summary        text,
  finalised_by         bigint      references employee,
  finalised_at         timestamptz,
  promo_job_title_id   bigint      references job_title,
  promo_grade          varchar(10)
                         check (promo_grade in ('JUNIOR','MEDIOR','SENIOR','SENIOR_I','SENIOR_II')),
  promo_effective_date date,
  promo_notify_mode    varchar(15)
                         check (promo_notify_mode in ('AFTER_MEETING','IMMEDIATELY')),
  promo_applied_at     timestamptz,
  unique (round_id, employee_id),
  check (finalised_by is null or finalised_by <> employee_id),
  check (status <> 'FINALISED'
         or (outcome is not null and final_summary is not null and finalised_by is not null)),
  check ((outcome = 'PROMOTED') =
         (promo_job_title_id is not null and promo_grade is not null
          and promo_effective_date is not null and promo_notify_mode is not null)),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);


-- ============================================================
-- 9. pip — FK constraints for source_review_id and source_checkin_id
--    are added via ALTER TABLE after review and checkin are created.
-- ============================================================
create table pip (
  id                        bigint      generated always as identity primary key,
  employee_id               bigint      not null references employee,
  manager_id                bigint      not null references employee,
  source_review_id          bigint,     -- FK added after review is created (see ALTER TABLE below)
  source_checkin_id         bigint,     -- FK added after checkin is created (see ALTER TABLE below)
  reason                    text        not null,
  proposed_start_date       date        not null,
  start_date                date,
  checkin_days              int         not null check (checkin_days > 0),
  duration_days             int         not null check (duration_days > 0),
  extension_days            int         not null check (extension_days > 0),
  status                    varchar(15) not null default 'DRAFT'
                              check (status in ('DRAFT','IN_HR_REVIEW','REJECTED','NOTICE_SENT',
                                     'DISPUTED','ACTIVE','FINAL_REVIEW','EXTENDED','CLOSED')),
  hr_goals_measurable       boolean     not null default false,
  hr_support_documented     boolean     not null default false,
  hr_informal_conversation  boolean     not null default false,
  notice_sent_at            timestamptz,
  dispute_reason            text,
  disputed_at               timestamptz,
  acknowledged_at           timestamptz,
  outcome                   varchar(25)
                              check (outcome in ('PASSED','NOT_MET','CLOSED_EMPLOYEE_LEAVING')),
  closed_at                 timestamptz,
  check (num_nonnulls(source_review_id, source_checkin_id) <= 1),
  check ((dispute_reason is null) = (disputed_at is null)),
  check ((status = 'CLOSED') = (outcome is not null)),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create unique index ux_pip_open on pip(employee_id)
  where status not in ('REJECTED','CLOSED');


-- ============================================================
-- 10. review
-- ============================================================
create table review (
  id               bigint      generated always as identity primary key,
  review_type      varchar(10) not null
                     check (review_type in ('SELF','PEER','MANAGER','PROBATION','PROJECT')),
  form_id          bigint      not null references appraisal_form,
  reviewer_id      bigint      not null references employee,
  appraisal_id     bigint      references appraisal,
  assignment_id    bigint      references project_assignment,
  contract_id      bigint      references contract,
  due_date         date        not null,
  status           varchar(10) not null default 'DRAFT'
                     check (status in ('REQUESTED','DRAFT','SUBMITTED',
                            'DECLINED','REPLACED','CANCELLED')),
  nominated_by     bigint      references employee,
  decline_reason   text,
  replaced_by_id   bigint      references review,
  engine_task_id   varchar(64) unique,
  outcome          varchar(25),
  submitted_at     timestamptz,
  hr_decision      varchar(10) check (hr_decision in ('APPROVED','REJECTED')),
  hr_decided_by    bigint      references employee,
  hr_decided_at    timestamptz,
  check (num_nonnulls(appraisal_id, assignment_id, contract_id) = 1),
  check ((review_type in ('SELF','PEER','MANAGER')) = (appraisal_id is not null)),
  check ((review_type = 'PROJECT') = (assignment_id is not null)),
  check ((review_type = 'PROBATION') = (contract_id is not null)),
  check (outcome is null
         or (review_type = 'MANAGER'   and outcome in ('PROMOTE','MEETS','BELOW'))
         or (review_type = 'PROBATION' and outcome in ('CONFIRM','END'))
         or (review_type = 'PROJECT'   and outcome in ('DELIVERED','PERFORMANCE_CONCERN'))),
  check (review_type = 'PEER'
         or (nominated_by is null and status not in ('REQUESTED','DECLINED','REPLACED'))),
  check (status <> 'DECLINED' or decline_reason is not null),
  check (status <> 'SUBMITTED' or submitted_at is not null),
  check (review_type not in ('MANAGER','PROBATION','PROJECT')
         or status <> 'SUBMITTED' or outcome is not null),
  check (hr_decision is null or (review_type = 'PROBATION' and outcome = 'END')),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create unique index ux_review_self_mgr on review(appraisal_id, review_type)
  where review_type in ('SELF','MANAGER');
create unique index ux_review_peer on review(appraisal_id, reviewer_id)
  where review_type = 'PEER' and status in ('REQUESTED','DRAFT','SUBMITTED');
create unique index ux_review_project on review(assignment_id)
  where review_type = 'PROJECT';

-- 11. Wire pip → review (added after review exists)
alter table pip add constraint fk_pip_src_review
  foreign key (source_review_id) references review;


-- ============================================================
-- 12. checkin
-- ============================================================
create table checkin (
  id              bigint      generated always as identity primary key,
  checkin_type    varchar(15) not null
                    check (checkin_type in ('PLAN_PROGRESS','PIP_CHECKIN','PIP_FINAL')),
  plan_id         bigint      references development_plan,
  pip_id          bigint      references pip,
  seq_no          smallint    not null,
  engine_task_id  varchar(64) unique,
  notes           text,
  employee_comment text,
  outcome         varchar(12),
  done_by         bigint      references employee,
  done_at         timestamptz,
  hr_signoff_by   bigint      references employee,
  hr_signoff_at   timestamptz,
  check (num_nonnulls(plan_id, pip_id) = 1),
  check ((checkin_type = 'PLAN_PROGRESS') = (plan_id is not null)),
  check (outcome is null
         or (checkin_type = 'PLAN_PROGRESS'
             and outcome in ('CONTINUE','PERFORMED','SKILL_GAP'))
         or (checkin_type = 'PIP_FINAL'
             and outcome in ('PASSED','EXTEND','NOT_MET'))),
  check (checkin_type = 'PIP_FINAL'
         or (hr_signoff_by is null and hr_signoff_at is null)),
  check (checkin_type <> 'PIP_FINAL' or seq_no in (1,2)),
  check (checkin_type <> 'PIP_FINAL'
         or seq_no = 1 or outcome is distinct from 'EXTEND'),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);
create unique index ux_checkin_plan on checkin(plan_id, seq_no)
  where plan_id is not null;
create unique index ux_checkin_pip on checkin(pip_id, checkin_type, seq_no)
  where pip_id is not null;

-- 13. Wire pip → checkin (added after checkin exists)
alter table pip add constraint fk_pip_src_checkin
  foreign key (source_checkin_id) references checkin;


-- ============================================================
-- 14. v_bench — employees with no active project assignment
--     Bench since: day after most-recently-ended assignment, or
--     earliest contract start date if never assigned.
-- ============================================================
create view v_bench as
with bench_base as (
  select
    e.id                                          as employee_id,
    (e.first_name || ' ' || e.last_name)          as employee_name,
    jt.name                                       as job_title,
    e.manager_id,
    case when m.id is not null
         then (m.first_name || ' ' || m.last_name)
         end                                      as manager_name,
    greatest(
      (select max(pa.end_date + 1)
       from project_assignment pa
       where pa.employee_id = e.id
         and pa.end_date is not null
         and pa.end_date < current_date),
      (select min(c.start_date)
       from contract c
       where c.employee_id = e.id)
    )                                             as bench_since,
    (select p.name
     from project_assignment pa2
     join project p on p.id = pa2.project_id
     where pa2.employee_id = e.id
     order by coalesce(pa2.end_date, pa2.start_date) desc
     limit 1)                                     as last_project_name
  from employee e
  join job_title jt on jt.id = e.job_title_id
  left join employee m on m.id = e.manager_id
  where e.stage <> 'ARCHIVED'
    and not exists (
      select 1 from project_assignment pa
      where pa.employee_id = e.id
        and pa.start_date <= current_date
        and (pa.end_date is null or pa.end_date >= current_date)
    )
)
select
  employee_id,
  employee_name,
  job_title,
  manager_id,
  manager_name,
  bench_since,
  (current_date - bench_since)::int as days_on_bench,
  last_project_name
from bench_base;


-- ============================================================
-- 15. v_employee_allocation — current allocation per active employee
-- ============================================================
create view v_employee_allocation as
select
  e.id                                         as employee_id,
  (e.first_name || ' ' || e.last_name)         as employee_name,
  jt.name                                      as job_title,
  coalesce(sum(pa.allocation_pct), 0)::int     as total_allocation_pct,
  case
    when coalesce(sum(pa.allocation_pct), 0) = 0   then 'ON_BENCH'
    when coalesce(sum(pa.allocation_pct), 0) > 100 then 'OVER_ALLOCATED'
    when coalesce(sum(pa.allocation_pct), 0) = 100 then 'FULLY_ALLOCATED'
    else 'PARTLY_ALLOCATED'
  end                                          as allocation_status,
  string_agg(p.name, ', ' order by pa.start_date) as projects
from employee e
join job_title jt on jt.id = e.job_title_id
left join project_assignment pa
  on pa.employee_id = e.id
  and pa.start_date <= current_date
  and (pa.end_date is null or pa.end_date >= current_date)
left join project p on p.id = pa.project_id
where e.stage not in ('ARCHIVED', 'OFFBOARDING')
group by e.id, e.first_name, e.last_name, jt.name;


-- ============================================================
-- 16. v_appraisal_status — self/peer/manager/client status per employee
--     for the currently open appraisal round (status = 'OPEN').
-- ============================================================
create view v_appraisal_status as
select
  a.id                                                          as appraisal_id,
  a.employee_id,
  (e.first_name || ' ' || e.last_name)                         as employee_name,
  e.manager_id,
  case when mgr.id is not null
       then (mgr.first_name || ' ' || mgr.last_name)
       end                                                      as manager_name,
  (select r.status from review r
   where r.appraisal_id = a.id and r.review_type = 'SELF'
   limit 1)                                                     as self_status,
  (select count(*) from review r
   where r.appraisal_id = a.id and r.review_type = 'PEER'
     and r.status = 'SUBMITTED')                                as peers_submitted,
  (select count(*) from review r
   where r.appraisal_id = a.id and r.review_type = 'PEER'
     and r.status not in ('DECLINED','CANCELLED','REPLACED'))   as peers_total,
  (select r.status from review r
   where r.appraisal_id = a.id and r.review_type = 'MANAGER'
   limit 1)                                                     as manager_review_status,
  (select r.outcome from review r
   where r.appraisal_id = a.id and r.review_type = 'MANAGER'
   limit 1)                                                     as manager_review_outcome,
  (select round(avg(cf.rating)::numeric, 1)
   from client_feedback cf
   where cf.employee_id = a.employee_id
     and cf.applies_to = 'PERSON')                              as client_feedback_avg,
  (select count(*)
   from client_feedback cf
   where cf.employee_id = a.employee_id
     and cf.applies_to = 'PERSON')                              as client_feedback_count,
  a.outcome                                                     as result,
  a.status                                                      as appraisal_status
from appraisal a
join appraisal_round ar on ar.id = a.round_id and ar.status = 'OPEN'
join employee e on e.id = a.employee_id
left join employee mgr on mgr.id = e.manager_id;


-- ============================================================
-- 17. v_pip — pip rows with derived columns used by the report
-- ============================================================
create view v_pip as
select
  p.id                                                               as pip_id,
  p.employee_id,
  (e.first_name || ' ' || e.last_name)                              as employee_name,
  p.manager_id,
  case when m.id is not null
       then (m.first_name || ' ' || m.last_name)
       end                                                           as manager_name,
  p.status,
  p.start_date,
  p.proposed_start_date,
  p.duration_days,
  p.checkin_days,
  case
    when p.source_review_id  is not null then 'PROJECT_REVIEW'
    when p.source_checkin_id is not null then 'SKILL_GAP'
    else 'DIRECT'
  end                                                                as source,
  (p.disputed_at is not null)                                        as disputed,
  exists (
    select 1 from checkin c
    where c.pip_id = p.id
      and c.checkin_type = 'PIP_FINAL'
      and c.outcome = 'EXTEND'
  )                                                                  as extended,
  case when p.start_date is not null
       then (p.start_date + p.duration_days)::date
       end                                                           as final_review_date,
  case
    when p.status = 'ACTIVE' and p.start_date is not null
    then ((p.start_date + p.duration_days)::date - current_date)
    end                                                              as days_left,
  (select count(*)
   from checkin c
   where c.pip_id = p.id
     and c.checkin_type = 'PIP_FINAL'
     and c.outcome = 'EXTEND')                                       as extension_count
from pip p
join employee e on e.id = p.employee_id
left join employee m on m.id = p.manager_id;


-- ============================================================
-- 18. v_upcoming_dates — events in the next 90 days
--     Covers: probation ends, contract ends, PIP final reviews.
-- ============================================================
create view v_upcoming_dates as

-- Probation period ending
select
  'PROBATION'::text                                              as event_type,
  e.id                                                           as employee_id,
  (e.first_name || ' ' || e.last_name)                          as employee_name,
  (c.start_date + (c.probation_months * interval '1 month'))::date as event_date,
  (((c.start_date + (c.probation_months * interval '1 month'))::date) - current_date)
                                                                 as days_until,
  'MANAGER'::text                                                as owner_role,
  e.manager_id                                                   as owner_id,
  'UPCOMING'::text                                               as event_status,
  c.id                                                           as source_id
from contract c
join employee e on e.id = c.employee_id
where c.is_current
  and c.probation_months > 0
  and (c.start_date + (c.probation_months * interval '1 month'))::date
      between current_date and current_date + 90
  and e.stage <> 'ARCHIVED'

union all

-- Fixed-term contract ending
select
  'CONTRACT'::text                                               as event_type,
  e.id                                                           as employee_id,
  (e.first_name || ' ' || e.last_name)                          as employee_name,
  c.end_date                                                     as event_date,
  (c.end_date - current_date)                                    as days_until,
  'HR'::text                                                     as owner_role,
  null::bigint                                                   as owner_id,
  'UPCOMING'::text                                               as event_status,
  c.id                                                           as source_id
from contract c
join employee e on e.id = c.employee_id
where c.is_current
  and c.end_date is not null
  and c.end_date between current_date and current_date + 90
  and e.stage <> 'ARCHIVED'

union all

-- PIP reaching final review within 90 days
select
  'PIP'::text                                                    as event_type,
  p.employee_id,
  (e.first_name || ' ' || e.last_name)                          as employee_name,
  (p.start_date + p.duration_days)::date                         as event_date,
  ((p.start_date + p.duration_days)::date - current_date)        as days_until,
  'MANAGER_HR'::text                                             as owner_role,
  p.manager_id                                                   as owner_id,
  p.status                                                       as event_status,
  p.id                                                           as source_id
from pip p
join employee e on e.id = p.employee_id
where p.status in ('ACTIVE','EXTENDED','FINAL_REVIEW')
  and p.start_date is not null
  and (p.start_date + p.duration_days)::date
      between current_date and current_date + 90;


-- ============================================================
-- 19. v_unreturned_assets — assets in RETURN_DUE or NOT_RETURNED status
-- ============================================================
create view v_unreturned_assets as
select
  a.id                                                    as asset_id,
  a.asset_number,
  (a.asset_type || ' - ' || a.model)                      as asset_description,
  a.status                                                as asset_status,
  a.employee_id,
  case when e.id is not null
       then (e.first_name || ' ' || e.last_name)
       end                                                as employee_name,
  ee.last_working_day,
  a.return_due_date
from asset a
left join employee e on e.id = a.employee_id
left join employee_exit ee
  on ee.employee_id = a.employee_id
  and ee.status <> 'REJECTED'
where a.status in ('RETURN_DUE','NOT_RETURNED');
