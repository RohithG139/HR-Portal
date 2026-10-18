-- ============================================================
-- V1__init.sql — CubeWorks HR baseline schema (schema hr)
--
-- Scope: exactly the 17 tables needed for F01 (Create employee →
--        Onboarding).  Every other table in 02-data-model.md belongs
--        to F02–F09 and is NOT created here.
--
-- Conventions applied to every table (R-DB-2):
--   PK   — bigint generated always as identity  (exception: engine_task
--           uses varchar PK; setting uses a natural varchar PK — verbatim)
--   Audit — created_at / created_by / updated_at / updated_by / version
--           appended after the verbatim DDL block for each table.
--           created_by / updated_by are nullable on every table:
--             • system-generated rows (engine callbacks, Flyway seed, batch
--               jobs) carry no user context;
--             • the very first employee row has no prior employee creator.
--   Enums — varchar + CHECK
--   FKs   — ON DELETE RESTRICT (default) unless stated otherwise
--
-- Circular-FK note (department / job_title):
--   department.created_by → employee, but employee → department.
--   job_title.created_by  → employee, but employee → job_title.
--   Resolution: create department and job_title first without created_by /
--   updated_by; create employee; then ALTER TABLE to add those two columns.
--   This keeps all FK constraints declared (no deferred tricks).
--
-- Table creation order (respects all FK dependencies):
--   1  department (partial — no created_by/updated_by yet)
--   2  job_title  (partial — no created_by/updated_by yet)
--   3  employee   → then ALTER department + job_title
--   4  contract
--   5  document
--   6  checklist_template
--   7  checklist_template_item
--   8  email_template
--   9  setting
--  10  onboarding_draft
--  11  onboarding
--  12  asset
--  13  checklist_item
--  14  engine_task
--  15  notification
--  16  email_outbox
--  17  audit_log
--
-- G-09 confirmation: none of these 17 tables contains a salary, bank-
-- account or ID-document-number column.  Verified against 02-data-model.md.
-- ============================================================


-- ============================================================
-- 1. department
-- created_by / updated_by are added after employee is created (circular FK)
-- ============================================================

create table department (
  id          bigint      generated always as identity primary key,
  name        varchar(100) not null unique,
  -- audit columns (R-DB-2) — created_by / updated_by deferred (see file header)
  created_at  timestamptz  not null default now(),
  updated_at  timestamptz  not null default now(),
  version     integer      not null default 0
);


-- ============================================================
-- 2. job_title
-- independent of department (keeps employee.department_id 3NF)
-- created_by / updated_by deferred for the same reason as department
-- ============================================================

create table job_title (
  id          bigint       generated always as identity primary key,
  name        varchar(100) not null unique,
  -- audit columns (R-DB-2) — created_by / updated_by deferred (see file header)
  created_at  timestamptz  not null default now(),
  updated_at  timestamptz  not null default now(),
  version     integer      not null default 0
);


-- ============================================================
-- 3. employee
-- ============================================================

create table employee (
  id               bigint       generated always as identity primary key,
  employee_code    varchar(10)  not null unique,               -- E-0031 format; also equals the CaseFabric case id
  case_id          varchar(64)  unique,                        -- nullable: NULL only between db-commit and engine case-start
  first_name       varchar(100) not null,
  last_name        varchar(100) not null,
  private_email    varchar(254) not null unique,               -- ONB-09: uniqueness enforced here and in Spring (409 DUPLICATE_PRIVATE_EMAIL)
  work_email       varchar(254) unique,                        -- nullable: created by IT after onboarding starts
  mobile_phone     varchar(30)  not null,
  date_of_birth    date         not null,
  nationality      varchar(60),                                -- nullable: not required in every jurisdiction
  street           varchar(150) not null,
  postcode         varchar(12)  not null,
  city             varchar(100) not null,
  emergency_name   varchar(150),                               -- nullable: employee may supply emergency contact details later (WS-03)
  emergency_relationship varchar(60),                         -- nullable: same
  emergency_phone  varchar(30),                               -- nullable: same
  job_title_id     bigint       not null references job_title,
  department_id    bigint       not null references department,
  grade            varchar(10)  not null
                     check (grade in ('JUNIOR','MEDIOR','SENIOR','SENIOR_I','SENIOR_II')),   -- [ASSUMPTION] fixed list
  manager_id       bigint       references employee,           -- nullable: the top-level manager has no manager
  buddy_id         bigint       references employee,           -- nullable: buddy assigned during onboarding; optional
  app_role         varchar(10)  not null
                     check (app_role in ('HR','MANAGER','EMPLOYEE','IT_ADMIN')),
  idp_subject      varchar(64)  unique,                        -- nullable: Keycloak sub; NULL until Keycloak account is created
  account_status   varchar(12)  not null default 'NOT_INVITED'
                     check (account_status in ('NOT_INVITED','INVITED','ACTIVE','DISABLED')),
  first_login_at   timestamptz,                                -- nullable: NULL until the employee activates their account
  stage            varchar(12)  not null default 'ONBOARDING'
                     check (stage in ('ONBOARDING','ACTIVE','PIP','OFFBOARDING','ARCHIVED')),   -- cache of engine state
  stage_since      date,                                       -- nullable: not meaningful until stage first transitions
  archived_at      timestamptz,                                -- nullable: G-10; NULL while not archived
  -- domain invariants: an employee cannot be their own manager or buddy ----
  check (manager_id is null or manager_id <> id),
  check (buddy_id   is null or buddy_id   <> id),
  -- audit columns (R-DB-2)
  -- created_by / updated_by are self-references: nullable because the very
  -- first employee row has no prior employee record to attribute creation to.
  created_at       timestamptz  not null default now(),
  created_by       bigint       references employee,           -- nullable self-reference: bootstrap problem
  updated_at       timestamptz  not null default now(),
  updated_by       bigint       references employee,           -- nullable self-reference: same
  version          integer      not null default 0
);

-- B-Tree index: manager_id drives the WS-02 "manager sees only own team"
-- query and the manager dropdown (A02).  Unindexed FK scan on the employee
-- table would be O(n) for every manager page load.
-- No CONCURRENTLY here: V1 creates an empty table; there is nothing to lock.
create index ix_employee_manager on employee(manager_id);

-- Back-fill the audit FK columns on department and job_title now that
-- employee exists.  These are separate ALTER TABLE statements because
-- PostgreSQL requires the referenced table to already exist.
alter table department add column created_by bigint references employee;   -- nullable: no employee exists at seed time
alter table department add column updated_by bigint references employee;   -- nullable: same

alter table job_title  add column created_by bigint references employee;   -- nullable: no employee exists at seed time
alter table job_title  add column updated_by bigint references employee;   -- nullable: same


-- ============================================================
-- 4. contract
-- one row per period; renew/convert adds a row
-- ============================================================

create table contract (
  id                bigint    generated always as identity primary key,
  employee_id       bigint    not null references employee,
  contract_type     varchar(10) not null check (contract_type in ('PERMANENT','FIXED_TERM')),
  start_date        date      not null,
  end_date          date,                                      -- nullable: PERMANENT contracts have no end date (enforced by CHECK below)
  hours_per_week    smallint  not null check (hours_per_week between 1 and 40),
  probation_months  smallint  not null check (probation_months in (0,1,2)),   -- probation end computed in v_contract view (3NF)
  is_current        boolean   not null default true,
  -- domain invariants ---
  check ((contract_type = 'PERMANENT') = (end_date is null)),  -- PERMANENT ↔ no end date; FIXED_TERM must have one
  check (end_date is null or end_date > start_date),           -- end must be strictly after start when set
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: system-assisted creation carries no operator context
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);

-- Partial unique index: at most one "current" contract per employee.
-- Non-current (historical) rows are excluded so the constraint is semantically
-- correct — an employee can have many past contracts but only one current one.
-- No CONCURRENTLY: initial empty table.
create unique index ux_contract_current on contract(employee_id) where is_current;


-- ============================================================
-- 5. document
-- ============================================================

create table document (
  id            bigint      generated always as identity primary key,
  employee_id   bigint      references employee,               -- nullable: NULL for company POLICY docs and uploads before the employee row exists
  doc_type      varchar(30) not null
                  check (doc_type in ('CONTRACT','POLICY','POLICY_ACK','RESIGNATION_LETTER',
                         'PIP_ATTACHMENT','DISPUTE_ATTACHMENT','CLIENT_FEEDBACK_ATTACHMENT',
                         'FINAL_DOCUMENT','OTHER')),
  file_name     varchar(255) not null,
  content_type  varchar(100) not null,
  size_bytes    bigint       not null check (size_bytes > 0),
  storage_key   varchar(500) not null unique,
  uploaded_by   bigint       not null references employee,
  uploaded_at   timestamptz  not null default now(),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: system-initiated uploads carry no user context
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 6. checklist_template
-- ============================================================

create table checklist_template (
  id             bigint      generated always as identity primary key,
  name           varchar(100) not null unique,
  kind           varchar(12)  not null check (kind in ('ONBOARDING','OFFBOARDING')),
  job_title_id   bigint       references job_title,            -- nullable: NULL means the template applies regardless of job title
  copied_from_id bigint       references checklist_template,   -- nullable: NULL for original templates; set when a copy is made (ADM-04)
  archived_at    timestamptz,                                  -- nullable: ADM-04; NULL while the template is still active
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: seeded before any employee exists
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 7. checklist_template_item
-- ============================================================

create table checklist_template_item (
  id                  bigint  generated always as identity primary key,
  template_id         bigint  not null references checklist_template on delete cascade,
  name                varchar(150) not null,
  section             varchar(30)  not null
                        check (section in ('DOCUMENTS_ADMIN','EQUIPMENT_ISSUED','ACCOUNTS_INSTALLATIONS',
                               'ORIENTATION','RETURN_ASSETS','REMOVE_ACCESS','HR_HANDOVER','REASSIGN_TEAM')),
  owner_role          varchar(10)  not null
                        check (owner_role in ('HR','IT_ADMIN','MANAGER','EMPLOYEE','SYSTEM')),
  item_kind           varchar(20)  not null default 'GENERIC'
                        check (item_kind in ('GENERIC','POLICY_ACK','PROFILE','EQUIPMENT_CONFIRM',
                               'EQUIPMENT','ACCOUNT','EXIT_INTERVIEW','ASSIGNMENTS_END')),
  mandatory           boolean  not null default true,
  due_offset_days     int      not null,
  policy_document_id  bigint   references document,            -- nullable: only POLICY_ACK items link to a policy document (enforced by CHECK below)
  sort_order          int      not null default 0,
  -- domain invariant: a policy document may only be attached to a POLICY_ACK item ---
  check (policy_document_id is null or item_kind = 'POLICY_ACK'),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: seeded before any employee exists
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 8. email_template
-- was template + email_template_recipient (consolidated in v2)
-- ============================================================

create table email_template (
  id                   bigint       generated always as identity primary key,
  code                 varchar(40)  not null unique,           -- referenced as FK from email_outbox
  name                 varchar(100) not null unique,
  trigger_description  varchar(200) not null,
  subject              varchar(200) not null,
  body                 text         not null,
  cc                   varchar(1000),                          -- nullable: most templates have no CC address
  to_employee          boolean not null default false,
  to_manager           boolean not null default false,
  to_hr                boolean not null default false,
  to_it_admin          boolean not null default false,
  to_task_owner        boolean not null default false,
  to_peer              boolean not null default false,
  active               boolean not null default true,
  -- domain invariant: every template must address at least one recipient ---
  check (to_employee or to_manager or to_hr or to_it_admin or to_task_owner or to_peer),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: seeded before any employee exists
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 9. setting
-- was timer_rule + task_type_setting (consolidated in v2)
-- Uses a natural varchar PK (code) per 02-data-model.md — the only table
-- in this migration that deviates from the bigint identity PK convention.
-- ============================================================

create table setting (
  code           varchar(60)  primary key,                     -- e.g. ONBOARDING_OVERDUE, UNDO_WINDOW_SECONDS
  category       varchar(10)  not null check (category in ('TIMER','TASK_DUE')),
  module         varchar(30)  not null,
  label          varchar(100) not null,
  value_int      int          check (value_int >= 0),          -- nullable: text-only settings have no integer value
  value_unit     varchar(12)  check (value_unit in ('DAYS','SECONDS','MINUTES','CHARACTERS')),   -- nullable: text-only settings have no unit
  value_text     varchar(20)  check (value_text in ('QUARTERLY','HALF_YEARLY','YEARLY')),        -- nullable: only KPI_CYCLE uses this
  recipient_role varchar(15)  check (recipient_role in
                   ('MANAGER','HR','MANAGER_HR','EMPLOYEE','TASK_OWNER','IT_ADMIN','ALL_USERS','NEW_USER','USER')),   -- nullable: platform/policy settings address no specific role
  can_disable    boolean  not null default false,
  enabled        boolean  not null default true,
  -- domain invariants ---
  -- A setting that cannot be disabled must always remain enabled.
  check (can_disable or enabled),
  -- TASK_DUE settings always express an integer number of days; TIMER settings
  -- are more flexible (e.g. SECONDS, MINUTES, CHARACTERS for policy settings).
  check (category = 'TIMER' or (value_int is not null and value_unit = 'DAYS')),
  -- The password length must be at least 8 characters (NIST SP 800-63B minimum)
  -- and at most 64 characters (Keycloak policy practical maximum).
  check (code <> 'PASSWORD_MIN_LENGTH' or value_int between 8 and 64),
  -- value_text is meaningful only for the KPI_CYCLE setting.
  check ((code = 'KPI_CYCLE') = (value_text is not null)),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: seeded before any employee exists
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);
-- The React route per task type moves to frontend code (it is UI config, not data).


-- ============================================================
-- 10. onboarding_draft
-- F01 Q13: unvalidated step-1 form ("Save draft"); deleted on Save & continue
-- ============================================================

create table onboarding_draft (
  id                   bigint  generated always as identity primary key,
  first_name           varchar(100) not null,                  -- the only fields checked on Save draft
  last_name            varchar(100) not null,
  form                 jsonb        not null,                  -- raw step-1 form as typed; never read by business logic
  contract_document_id bigint       references document,       -- nullable: the file may not be uploaded before Save draft is clicked
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: HR operator; nullable in case the session context is unavailable
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 11. onboarding
-- was onboarding + it_request (consolidated in v2); created on Save & continue
-- ============================================================

create table onboarding (
  id                       bigint  generated always as identity primary key,
  employee_id              bigint  not null unique references employee,
  template_id              bigint  not null references checklist_template,
  id_checked               boolean not null default false,
  contract_signed_received boolean not null default false,
  right_to_work_verified   boolean not null default false,
  status                   varchar(12) not null default 'IN_PROGRESS'
                             check (status in ('IN_PROGRESS','COMPLETED','CANCELLED')),
  completed_at  timestamptz,                                   -- nullable: NULL while IN_PROGRESS or CANCELLED
  completed_by  bigint      references employee,               -- nullable: NULL while IN_PROGRESS or CANCELLED
  it_laptop_model      varchar(60),                            -- nullable: populated when HR fills in the IT request
  it_keyboard_layout   varchar(30),                            -- nullable: optional per FRD D-16
  it_ready_by          date,                                   -- nullable: populated when HR fills in the IT request
  it_delivery_method   varchar(20)
                         check (it_delivery_method in ('OFFICE_DAY1','SHIP_HOME')),  -- nullable: populated with the IT request
  it_notes      text,                                          -- nullable: optional free-text notes for IT
  it_status     varchar(12) not null default 'NOT_SENT'
                  check (it_status in ('NOT_SENT','SENT','IN_PROGRESS','COMPLETED')),
  it_sent_at    timestamptz,                                   -- nullable: NULL until HR sends the IT request
  -- domain invariants ---
  -- "Confirm that the ID was checked and the contract is signed." (FRD-02 §5 Verification card).
  -- The service validates first (400 VALIDATION_FAILED); the DB enforces the invariant as a last resort.
  check (id_checked and contract_signed_received),
  -- completed_at is set exactly when status becomes COMPLETED, never otherwise.
  check ((status = 'COMPLETED') = (completed_at is not null)),
  -- Laptop model, ready-by date and delivery method become mandatory once IT has been notified.
  check (it_status = 'NOT_SENT' or (it_laptop_model is not null and it_ready_by is not null and it_delivery_method is not null)),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: created in the Save & continue transaction
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);
-- Accessories and accounts requested from IT become checklist_item rows
-- (section EQUIPMENT_ISSUED / ACCOUNTS_INSTALLATIONS, owner IT_ADMIN) on "Send to IT".


-- ============================================================
-- 12. asset
-- history via audit_log (entity_type = 'ASSET')
-- ============================================================

create table asset (
  id             bigint      generated always as identity primary key,
  asset_number   varchar(20) not null unique,
  asset_type     varchar(20) not null
                   check (asset_type in ('LAPTOP','MONITOR','PHONE','ACCESS_BADGE',
                          'DOCKING_STATION','HEADSET','KEYBOARD_MOUSE','OTHER')),
  model          varchar(100) not null,
  serial_number  varchar(60),                                  -- nullable: some assets (e.g. access badges) have no serial number
  status         varchar(15) not null default 'IN_STORAGE'
                   check (status in ('IN_STORAGE','ISSUED','RETURN_DUE','RETURNED','NOT_RETURNED')),
  employee_id    bigint      references employee,              -- nullable: NULL while IN_STORAGE; set to the assigned employee when ISSUED
  issued_on      date,                                         -- nullable: NULL while IN_STORAGE
  return_due_date date,                                        -- nullable: NULL until a return is scheduled (offboarding)
  returned_on    date,                                         -- nullable: NULL until the asset is physically returned
  return_condition varchar(10)
                   check (return_condition in ('GOOD','DAMAGED')),   -- nullable: recorded only when the asset is returned
  -- domain invariants ---
  -- An asset in ISSUED, RETURN_DUE or NOT_RETURNED state must have an assigned employee.
  check (status not in ('ISSUED','RETURN_DUE','NOT_RETURNED') or employee_id is not null),
  -- Return condition is mandatory when the asset has been returned.
  check (status <> 'RETURNED' or return_condition is not null),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: IT admin bulk-import paths carry no user context
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 13. checklist_item
-- onboarding + offboarding; also replaces account_access
-- ============================================================

create table checklist_item (
  id               bigint  generated always as identity primary key,
  employee_id      bigint  not null references employee,
  phase            varchar(12) not null check (phase in ('ONBOARDING','OFFBOARDING')),
  template_item_id bigint  references checklist_template_item,   -- nullable: NULL for ad-hoc "+ Add item" rows
  name             varchar(150) not null,                        -- snapshot of the template item; template edits do not alter running cases (02-data-model.md §5)
  section          varchar(30)  not null,                        -- snapshot: same reason
  owner_role       varchar(10)  not null,                        -- snapshot: same reason
  item_kind        varchar(20)  not null default 'GENERIC',
  mandatory        boolean      not null,                        -- snapshot: same reason
  due_date         date         not null,
  status           varchar(10)  not null default 'OPEN'
                     check (status in ('OPEN','DONE','CANCELLED')),
  done_at          timestamptz,                                  -- nullable: NULL while OPEN or CANCELLED
  done_by          bigint       references employee,             -- nullable: NULL while OPEN or CANCELLED
  asset_id         bigint       references asset,                -- nullable: only EQUIPMENT items link to an asset record
  removes_item_id  bigint       references checklist_item,       -- nullable: offboarding ACCOUNT items point to the onboarding item they supersede; NULL for all other items
  -- domain invariants ---
  -- done_at and done_by are always set together when an item is ticked.
  check ((status = 'DONE') = (done_at is not null and done_by is not null)),
  -- Only offboarding items may reference an onboarding item to remove.
  check (removes_item_id is null or phase = 'OFFBOARDING'),
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                   -- nullable: template-instantiated items carry no user context
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                   -- nullable: same
  version     integer     not null default 0
);

-- Partial index: supports the My Tasks query "give me all OPEN checklist items
-- for this employee in this phase for this owner role".
-- DONE and CANCELLED items are excluded — once onboarding completes most items
-- are done; including them would inflate the index by ~80% with no benefit to
-- the hot-path query.
-- No CONCURRENTLY: V1 creates an empty table; there is nothing to lock.
create index ix_checklist_open on checklist_item(employee_id, phase, owner_role) where status = 'OPEN';


-- ============================================================
-- 14. engine_task
-- was engine_task + pending_completion (consolidated in v2)
-- PK is varchar(64) — the engine's own task id (not an identity column).
-- The verbatim DDL already includes created_at (no default); the remaining
-- 4 audit columns are appended below.
-- ============================================================

create table engine_task (
  engine_task_id    varchar(64)  primary key,
  employee_id       bigint       not null references employee,   -- case lookup via employee.case_id
  task_name         varchar(100) not null,                       -- setting(category=TASK_DUE, code=task_name) drives the due-days lookup
  owner_employee_id bigint       references employee,            -- nullable: unassigned tasks have no owner yet
  due_date          date         not null,                       -- G-03
  related_entity    varchar(30),                                 -- nullable: not all tasks relate to a specific entity type
  related_id        bigint,                                      -- nullable: same
  created_at        timestamptz  not null,                       -- set explicitly by the engine/task-watcher; no DEFAULT: the caller owns the timestamp
  completed_at      timestamptz,                                 -- nullable: NULL while the task is open
  notified_at       timestamptz,                                 -- nullable: G-04; NULL until the first due-date notification is sent
  overdue_reminder_at timestamptz,                               -- nullable: G-05; NULL until the overdue reminder fires
  pending_by        bigint       references employee,            -- nullable: undo-window actor; NULL while not in a pending-completion state
  pending_output    jsonb,                                       -- nullable: NULL while not in a pending-completion state
  pending_execute_at timestamptz,                                -- nullable: NULL while not in a pending-completion state
  pending_status    varchar(10)
                      check (pending_status in ('PENDING','UNDONE','EXECUTED','FAILED')),   -- nullable: NULL when no pending completion is active
  -- domain invariants ---
  -- output and status are always set together (both or neither).
  check ((pending_status is null) = (pending_output is null)),
  -- When a pending completion is active, the actor and execution time are required.
  check (pending_status is null or (pending_by is not null and pending_execute_at is not null)),
  -- audit columns (R-DB-2): created_at already declared above; remaining 4 appended here.
  created_by   bigint      references employee,                  -- nullable: engine callbacks carry no user context
  updated_at   timestamptz not null default now(),
  updated_by   bigint      references employee,                  -- nullable: same
  version      integer     not null default 0
);

-- Partial index: the My Tasks query (A36) fetches only open (uncompleted) engine
-- tasks for a given owner.  Completed tasks are never surfaced in My Tasks again.
-- No CONCURRENTLY: V1 creates an empty table.
create index ix_engine_task_open on engine_task(owner_employee_id) where completed_at is null;

-- Partial index: the undo-window scheduler polls for PENDING completions whose
-- pending_execute_at has passed.  UNDONE / EXECUTED / FAILED rows are historical
-- and never need to appear in this scan.
-- No CONCURRENTLY: V1 creates an empty table.
create index ix_engine_task_pending on engine_task(pending_execute_at) where pending_status = 'PENDING';


-- ============================================================
-- 15. notification
-- The verbatim DDL already includes created_at (with DEFAULT now()); the
-- remaining 4 audit columns are appended below.
-- ============================================================

create table notification (
  id           bigint      generated always as identity primary key,
  recipient_id bigint      not null references employee,
  title        varchar(200) not null,
  link_route   varchar(200),                                    -- nullable: some notifications have no navigable target in the UI
  employee_id  bigint      references employee,                 -- nullable: platform alerts are not tied to a specific employee
  read_at      timestamptz,                                     -- nullable: NULL while the notification is unread
  created_at   timestamptz not null default now(),              -- present in verbatim DDL; not duplicated in the audit-column block below
  -- audit columns (R-DB-2): created_at already declared above; remaining 4 appended here.
  created_by   bigint      references employee,                 -- nullable: notifications are system-generated; no user context available
  updated_at   timestamptz not null default now(),
  updated_by   bigint      references employee,                 -- nullable: same; effectively immutable after creation
  version      integer     not null default 0
);

-- Partial index: the notification bell count query (A38 GET /api/notifications/count)
-- only counts unread notifications per recipient.
-- Excluding read rows keeps this index small; a full index would contain every
-- historical notification ever sent.
-- No CONCURRENTLY: V1 creates an empty table.
create index ix_notification_unread on notification(recipient_id) where read_at is null;


-- ============================================================
-- 16. email_outbox
-- ============================================================

create table email_outbox (
  id            bigint      generated always as identity primary key,
  template_code varchar(40) not null references email_template(code),   -- FK on the natural code column (email_template.code is UNIQUE)
  to_address    varchar(254) not null,
  cc            varchar(1000),                                           -- nullable: most outgoing e-mails have no CC
  subject       varchar(200) not null,                                   -- rendered snapshot: stored as actually sent (deliberate 3NF copy — 02-data-model.md §5)
  body          text         not null,                                   -- rendered snapshot: same reason
  status        varchar(10)  not null default 'PENDING'
                  check (status in ('PENDING','SENT','FAILED')),
  attempts      int          not null default 0,
  sent_at       timestamptz,                                             -- nullable: NULL while PENDING or still FAILED
  employee_id   bigint       references employee,                        -- nullable: password-reset e-mails may be sent before the employee row exists
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                           -- nullable: outbox rows are written inside the business transaction; the operator is captured in audit_log
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                           -- nullable: same
  version     integer     not null default 0
);


-- ============================================================
-- 17. audit_log
-- G-06, WS-05, ADM-05; also the asset-event history
-- Rows are immutable after insert; updated_at / updated_by / version are
-- structurally present (convention applies to every table) but are never
-- written after the initial INSERT.
-- ============================================================

create table audit_log (
  id           bigint  generated always as identity primary key,
  employee_id  bigint  references employee,                    -- nullable: platform-level changes may not relate to a specific employee row
  entity_type  varchar(40) not null,
  entity_id    bigint,                                         -- nullable: setting uses a varchar PK; those rows set entity_id = null
  action       varchar(40) not null,
  changed_by   bigint      references employee,                -- nullable: system-initiated changes (batch jobs, engine callbacks) have no user
  changed_at   timestamptz not null default now(),             -- when the business change happened (distinct from created_at below)
  before_value jsonb,                                          -- nullable: INSERT actions have no before-state
  after_value  jsonb,                                          -- nullable: DELETE actions have no after-state
  -- audit columns (R-DB-2) --------------------------------
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,                 -- nullable: system writes carry no user context
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,                 -- nullable: rows are immutable; this column is never written after INSERT
  version     integer     not null default 0
);

-- B-Tree composite index: the History tab query (A35) is always scoped to one
-- employee and ordered by time descending.  The leading employee_id column
-- handles the equality predicate; changed_at DESC covers the ORDER BY without
-- a post-sort step.
-- No CONCURRENTLY: V1 creates an empty table.
create index ix_audit_employee on audit_log(employee_id, changed_at desc);

-- B-Tree composite index: asset-history queries (entity_type='ASSET') and
-- per-entity audit trails use equality on both columns.
-- No CONCURRENTLY: V1 creates an empty table.
create index ix_audit_entity on audit_log(entity_type, entity_id);
