-- ============================================================
-- V2__seed_settings.sql — F01 seed data
--
-- Inserts the reference data that the onboarding flow needs before
-- the application can be used for the first time.  All values are
-- transcribed verbatim from the spec (F01 §9) and the FRD; nothing
-- is invented (R-SRC-2).
--
-- Sections:
--   A  Departments (4 rows)
--   B  Job titles  (6 rows)
--   C  Checklist templates and items  (4 templates, 62 items total)
--   D  E-mail templates (4 rows)
--   E  Settings (4 rows)
--
-- Category choice for setting rows (see §E for full reasoning):
--   PASSWORD_MIN_LENGTH  — category='TIMER'.  Rationale: the table CHECK
--     `category = 'TIMER' or (value_int is not null and value_unit = 'DAYS')`
--     means that a TASK_DUE row MUST have value_unit='DAYS'.  A password
--     length is measured in characters, not days, so value_unit='CHARACTERS'
--     is required.  Only the TIMER category satisfies the constraint without
--     forcing a semantically wrong unit.
--     DATA-MODEL GAP: the setting table's two categories (TIMER, TASK_DUE)
--     do not accommodate platform/policy settings.  A third category such as
--     'POLICY' or 'SYSTEM' would be semantically correct.  This gap does not
--     affect runtime behaviour but should be addressed when the settings
--     feature (F09) is specced.
--   SET_PASSWORD_LINK_DAYS — category='TIMER'.  value_unit='DAYS' would also
--     satisfy TASK_DUE, but this is a time-period (link validity), not a task
--     due-date.  TIMER is the semantically closer category.
--   UNDO_WINDOW_SECONDS and ONBOARDING_OVERDUE — category='TIMER'.  Both are
--     genuine timers; no ambiguity.
-- ============================================================


-- ============================================================
-- A. Departments
-- Placeholder rows matching the wireframe; HR edits them later through
-- the Settings screen (F09).  D-12 decision.
-- ============================================================

insert into department (name) values
  ('Development'),
  ('Design'),
  ('QA'),
  ('Management & HR');


-- ============================================================
-- B. Job titles
-- Placeholder rows matching the wireframe.  D-12 decision.
-- ============================================================

insert into job_title (name) values
  ('Developer'),
  ('Senior Developer'),
  ('Lead Developer'),
  ('UX Designer'),
  ('QA Engineer'),
  ('Tech Lead');


-- ============================================================
-- C. Checklist templates (4) and their items (18 + 17 + 17 + 10 = 62)
--
-- All templates: kind='ONBOARDING', job_title_id=NULL (apply to all titles).
-- item_kind defaults to 'GENERIC' for every item (none specified otherwise).
-- sort_order = row position within its section, starting at 0.
-- ============================================================

-- C1. Development template (18 items)

with tmpl as (
    insert into checklist_template (name, kind, job_title_id)
    values ('Development', 'ONBOARDING', null)
    returning id
)
insert into checklist_template_item
    (template_id, name, section, owner_role, mandatory, due_offset_days, sort_order)
select t.id, v.name, v.section, v.owner_role, v.mandatory, v.due_offset_days, v.sort_order
from   tmpl t
cross join (values
    -- DOCUMENTS_ADMIN (4 items, sort_order 0-3)
    ('Contract signed',          'DOCUMENTS_ADMIN',        'HR',       true,  -5, 0),
    ('ID checked',               'DOCUMENTS_ADMIN',        'HR',       true,  -5, 1),
    ('Policies acknowledged',    'DOCUMENTS_ADMIN',        'HR',       true,  -5, 2),
    ('Profile complete',         'DOCUMENTS_ADMIN',        'HR',       false, -5, 3),
    -- EQUIPMENT_ISSUED (3 items, sort_order 0-2)
    ('Laptop (MacBook Pro 14")', 'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 0),
    ('Monitor',                  'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 1),
    ('Access badge',             'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 2),
    -- ACCOUNTS_INSTALLATIONS (6 items, sort_order 0-5)
    ('Microsoft 365',            'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 0),
    ('Slack',                    'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 1),
    ('VPN',                      'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 2),
    ('GitHub',                   'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 3),
    ('Jira / Confluence',        'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 4),
    ('IDE + Docker',             'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 5),
    -- ORIENTATION (5 items, sort_order 0-4)
    ('Welcome session',          'ORIENTATION',            'HR',       true,   0, 0),
    ('Buddy assigned',           'ORIENTATION',            'HR',       true,   0, 1),
    ('Team introduction',        'ORIENTATION',            'HR',       true,   0, 2),
    ('Codebase walkthrough',     'ORIENTATION',            'HR',       false,  0, 3),
    ('First-week plan',          'ORIENTATION',            'HR',       false,  0, 4)
) as v(name, section, owner_role, mandatory, due_offset_days, sort_order);


-- C2. Design template (17 items)

with tmpl as (
    insert into checklist_template (name, kind, job_title_id)
    values ('Design', 'ONBOARDING', null)
    returning id
)
insert into checklist_template_item
    (template_id, name, section, owner_role, mandatory, due_offset_days, sort_order)
select t.id, v.name, v.section, v.owner_role, v.mandatory, v.due_offset_days, v.sort_order
from   tmpl t
cross join (values
    -- DOCUMENTS_ADMIN (4 items, sort_order 0-3)
    ('Contract signed',          'DOCUMENTS_ADMIN',        'HR',       true,  -5, 0),
    ('ID checked',               'DOCUMENTS_ADMIN',        'HR',       true,  -5, 1),
    ('Policies acknowledged',    'DOCUMENTS_ADMIN',        'HR',       true,  -5, 2),
    ('Profile complete',         'DOCUMENTS_ADMIN',        'HR',       false, -5, 3),
    -- EQUIPMENT_ISSUED (3 items, sort_order 0-2)
    ('Laptop (MacBook Pro 14")', 'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 0),
    ('Monitor',                  'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 1),
    ('Access badge',             'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 2),
    -- ACCOUNTS_INSTALLATIONS (5 items, sort_order 0-4)
    ('Microsoft 365',            'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 0),
    ('Slack',                    'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 1),
    ('VPN',                      'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 2),
    ('Figma licence',            'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 3),
    ('Jira / Confluence',        'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 4),
    -- ORIENTATION (5 items, sort_order 0-4)
    ('Welcome session',          'ORIENTATION',            'HR',       true,   0, 0),
    ('Buddy assigned',           'ORIENTATION',            'HR',       true,   0, 1),
    ('Team introduction',        'ORIENTATION',            'HR',       true,   0, 2),
    ('Design system intro',      'ORIENTATION',            'HR',       false,  0, 3),
    ('First-week plan',          'ORIENTATION',            'HR',       false,  0, 4)
) as v(name, section, owner_role, mandatory, due_offset_days, sort_order);


-- C3. QA template (17 items)

with tmpl as (
    insert into checklist_template (name, kind, job_title_id)
    values ('QA', 'ONBOARDING', null)
    returning id
)
insert into checklist_template_item
    (template_id, name, section, owner_role, mandatory, due_offset_days, sort_order)
select t.id, v.name, v.section, v.owner_role, v.mandatory, v.due_offset_days, v.sort_order
from   tmpl t
cross join (values
    -- DOCUMENTS_ADMIN (4 items, sort_order 0-3)
    ('Contract signed',          'DOCUMENTS_ADMIN',        'HR',       true,  -5, 0),
    ('ID checked',               'DOCUMENTS_ADMIN',        'HR',       true,  -5, 1),
    ('Policies acknowledged',    'DOCUMENTS_ADMIN',        'HR',       true,  -5, 2),
    ('Profile complete',         'DOCUMENTS_ADMIN',        'HR',       false, -5, 3),
    -- EQUIPMENT_ISSUED (4 items, sort_order 0-3)
    ('Laptop (Dell XPS 15)',     'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 0),
    ('Monitor',                  'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 1),
    ('Test phone',               'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 2),
    ('Access badge',             'EQUIPMENT_ISSUED',       'IT_ADMIN', false,  0, 3),
    -- ACCOUNTS_INSTALLATIONS (5 items, sort_order 0-4)
    ('Microsoft 365',            'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 0),
    ('Slack',                    'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 1),
    ('VPN',                      'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 2),
    ('Jira / Confluence',        'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 3),
    ('Test management tool',     'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', false,  0, 4),
    -- ORIENTATION (4 items, sort_order 0-3)
    ('Welcome session',          'ORIENTATION',            'HR',       true,   0, 0),
    ('Buddy assigned',           'ORIENTATION',            'HR',       true,   0, 1),
    ('Team introduction',        'ORIENTATION',            'HR',       true,   0, 2),
    ('Test process walkthrough', 'ORIENTATION',            'HR',       false,  0, 3)
) as v(name, section, owner_role, mandatory, due_offset_days, sort_order);


-- C4. General template (10 items)

with tmpl as (
    insert into checklist_template (name, kind, job_title_id)
    values ('General', 'ONBOARDING', null)
    returning id
)
insert into checklist_template_item
    (template_id, name, section, owner_role, mandatory, due_offset_days, sort_order)
select t.id, v.name, v.section, v.owner_role, v.mandatory, v.due_offset_days, v.sort_order
from   tmpl t
cross join (values
    -- DOCUMENTS_ADMIN (4 items, sort_order 0-3)
    ('Contract signed',          'DOCUMENTS_ADMIN',        'HR',       true,  -5, 0),
    ('ID checked',               'DOCUMENTS_ADMIN',        'HR',       true,  -5, 1),
    ('Policies acknowledged',    'DOCUMENTS_ADMIN',        'HR',       true,  -5, 2),
    ('Profile complete',         'DOCUMENTS_ADMIN',        'HR',       false, -5, 3),
    -- EQUIPMENT_ISSUED (2 items, sort_order 0-1)
    ('Laptop',                   'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 0),
    ('Access badge',             'EQUIPMENT_ISSUED',       'IT_ADMIN', true,   0, 1),
    -- ACCOUNTS_INSTALLATIONS (2 items, sort_order 0-1)
    ('Microsoft 365',            'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 0),
    ('Slack',                    'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', true,   0, 1),
    -- ORIENTATION (2 items, sort_order 0-1)
    ('Welcome session',          'ORIENTATION',            'HR',       true,   0, 0),
    ('Team introduction',        'ORIENTATION',            'HR',       true,   0, 1)
) as v(name, section, owner_role, mandatory, due_offset_days, sort_order);


-- ============================================================
-- D. E-mail templates (4)
--
-- Bodies transcribed verbatim from the spec §9 / FRD-08 wireframe.
-- The SET_PASSWORD body is marked as a placeholder (see comment below).
-- Recipient flags per spec §9 and F01 decision D-21.
-- ============================================================

insert into email_template
    (code, name, trigger_description, subject, body,
     to_employee, to_manager, to_hr, to_it_admin, to_task_owner, to_peer,
     active)
values
(
    'WELCOME_NEW_EMPLOYEE',
    'Welcome new employee',
    'Employee record created',
    'Welcome to CubeWorks, {employee_first_name}!',
    E'Hi {employee_first_name},\n\nWelcome to CubeWorks! You start on {start_date}. Your manager is {manager_name} and your buddy is {buddy_name}.\n\nYour work account details will follow from IT.',
    true,   -- to_employee
    false,  -- to_manager
    false,  -- to_hr
    false,  -- to_it_admin
    false,  -- to_task_owner
    false,  -- to_peer
    true    -- active
),
(
    'SET_PASSWORD',
    'Set your password',
    'Employee record created, or HR resends the account e-mail',
    'Set your password for CubeWorks HR',
    -- placeholder body: the FRD gives no exact wording for this template,
    -- unlike the other three which are transcribed verbatim from the wireframe.
    E'Hi {employee_first_name},\n\nWelcome to CubeWorks HR. Set your password to activate your account:\n\n{task_link}\n\nThis link is valid for {link_valid_days} days.',
    true,   -- to_employee
    false,  -- to_manager
    false,  -- to_hr
    false,  -- to_it_admin
    false,  -- to_task_owner
    false,  -- to_peer
    true    -- active
),
(
    'IT_EQUIPMENT_REQUEST',
    'IT equipment request',
    'HR sends the IT & equipment request',
    'Equipment request: {employee_name} starts {start_date}',
    E'Hi IT,\n\nPlease prepare the following for {employee_name} ({job_title}) by {ready_by}:\n\n{equipment_list}\n{accounts_list}\n\nOpen request: {task_link}',
    false,  -- to_employee
    false,  -- to_manager
    false,  -- to_hr
    true,   -- to_it_admin
    false,  -- to_task_owner
    false,  -- to_peer
    true    -- active
),
(
    'ONBOARDING_COMPLETED',
    'Onboarding completed',
    'Milestone Employee onboarded',
    'Onboarding completed for {employee_name}',
    E'Hi {recipient_first_name},\n\nAll onboarding items for {employee_name} are complete.',
    true,   -- to_employee  (D-21: Employee + Manager)
    true,   -- to_manager   (D-21)
    false,  -- to_hr
    false,  -- to_it_admin
    false,  -- to_task_owner
    false,  -- to_peer
    true    -- active
);


-- ============================================================
-- E. Settings (4)
--
-- Category decisions (full reasoning in file header):
--   ONBOARDING_OVERDUE    — TIMER  (genuine timer, 3 days after start date, per FRD-02 §6 and
--                                   FRD-08's Timers & rules table: "Onboarding overdue | 3 days
--                                   after start date | Task owner | No" — corrected from an
--                                   earlier 1-day/can_disable=true misread, D-20 superseded)
--   PASSWORD_MIN_LENGTH   — TIMER  (only category compatible with value_unit='CHARACTERS';
--                                   TASK_DUE would require value_unit='DAYS' per the table CHECK)
--   SET_PASSWORD_LINK_DAYS — TIMER (link validity period; semantically a duration not a task deadline)
--   UNDO_WINDOW_SECONDS   — TIMER  (genuine timer)
-- ============================================================

insert into setting
    (code, category, module, label, value_int, value_unit, recipient_role, can_disable, enabled)
values
(
    'ONBOARDING_OVERDUE',
    'TIMER',
    'Onboarding',
    'Onboarding overdue',
    3,
    'DAYS',
    'HR',
    false,  -- can_disable: FRD-08 lists this rule as "Can be switched off: No"
    true    -- enabled
),
(
    -- DATA-MODEL GAP: category='TIMER' is used here for want of a better fit.
    -- 'TASK_DUE' would require value_unit='DAYS' (table CHECK), which is wrong for
    -- a character-count setting.  A 'POLICY' or 'SYSTEM' category would be correct.
    -- Flagged for resolution when F09 (Settings) is specced.
    'PASSWORD_MIN_LENGTH',
    'TIMER',
    'Platform',
    'Minimum password length',
    12,
    'CHARACTERS',
    null,   -- recipient_role: not applicable (platform policy, no notification target)
    false,  -- can_disable: a platform security policy that cannot be turned off
    true    -- enabled: check (can_disable or enabled) = false OR true = true ✓
),
(
    'SET_PASSWORD_LINK_DAYS',
    'TIMER',
    'Platform',
    'Set-password link validity',
    7,
    'DAYS',
    'NEW_USER',
    false,  -- can_disable
    true    -- enabled
),
(
    'UNDO_WINDOW_SECONDS',
    'TIMER',
    'Platform',
    'Undo window',
    10,
    'SECONDS',
    'TASK_OWNER',
    false,  -- can_disable: the undo window is always active (G-03)
    true    -- enabled
);
