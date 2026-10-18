-- ============================================================
-- V10__seed_remaining_timers.sql — FRD-08 (Settings foundation) §9
--
-- Adds the 12 `setting` rows needed to reach the FRD's full 17-row "Timers &
-- rules" total. 5 are already seeded (ONBOARDING_OVERDUE, PASSWORD_MIN_LENGTH,
-- SET_PASSWORD_LINK_DAYS, UNDO_WINDOW_SECONDS in V2; OVERDUE_REMINDER_DAYS in
-- V7); this migration adds the rest. 17 - 5 = 12.
--
-- Every value, recipient and can-disable flag below is transcribed directly
-- from FRD-08 v1.2's clean "Timers & rules" table (spec §9, D-07 resolved) —
-- none of these 12 rows is an [ASSUMPTION]. `module` is this migration's own
-- UI-grouping label (not an FRD field, same free choice V2/V7 already made
-- for e.g. 'Platform', 'Onboarding').
-- ============================================================

insert into hr.setting
    (code, category, module, label, value_int, value_unit, value_text, recipient_role, can_disable, enabled)
values
(
    'BENCH_CHECK', 'TIMER', 'Bench', 'Bench check',
    7, 'DAYS', null, 'MANAGER',
    false, true
),
(
    'DEV_PLAN_PROGRESS_CHECK', 'TIMER', 'Development', 'Development plan progress check',
    14, 'DAYS', null, 'MANAGER',
    false, true
),
(
    'PIP_CHECKIN', 'TIMER', 'PIP', 'PIP check-in',
    14, 'DAYS', null, 'MANAGER',
    false, true
),
(
    'PIP_DURATION', 'TIMER', 'PIP', 'PIP duration',
    90, 'DAYS', null, 'MANAGER_HR',
    false, true
),
(
    'PIP_EXTENSION', 'TIMER', 'PIP', 'PIP extension (maximum once)',
    30, 'DAYS', null, 'MANAGER_HR',
    true, true    -- can_disable: FRD lists "Can be switched off: Yes"
),
(
    -- value_int/value_unit represent "days before end", per the same
    -- before-X convention as CONTRACT_RENEWAL_REMINDER/ASSET_RETURN_REMINDER
    -- below; the application computes the trigger date by subtracting this
    -- from the relevant end date (probation_end_date).
    'PROBATION_REVIEW', 'TIMER', 'Onboarding', 'Probation review',
    14, 'DAYS', null, 'MANAGER',
    false, true   -- can_disable: FRD lists "Can be switched off: No" (D-07 correction)
),
(
    'CONTRACT_RENEWAL_REMINDER', 'TIMER', 'Contract', 'Contract renewal reminder',
    60, 'DAYS', null, 'HR',       -- D-07 correction: HR only, not MANAGER_HR
    true, true
),
(
    'ASSET_RETURN_REMINDER', 'TIMER', 'Assets', 'Asset return reminder',
    3, 'DAYS', null, 'EMPLOYEE',
    true, true
),
(
    'RESET_PASSWORD_LINK_MINUTES', 'TIMER', 'Platform', 'Reset-password link valid for',
    60, 'MINUTES', null, 'USER',
    false, true
),
(
    -- value_text, not value_int/value_unit — the only setting that uses it
    -- (DB CHECK: (code = 'KPI_CYCLE') = (value_text is not null)).
    'KPI_CYCLE', 'TIMER', 'KPI', 'KPI cycle',
    null, null, 'QUARTERLY', 'MANAGER',   -- D-07 correction: MANAGER, not ALL_USERS
    false, true
),
(
    'NOTICE_PERIOD_PERMANENT', 'TIMER', 'Contract', 'Notice period – permanent contract',
    30, 'DAYS', null, 'HR',       -- D-07 correction: HR, not NULL
    false, true
),
(
    'NOTICE_PERIOD_FIXED_TERM', 'TIMER', 'Contract', 'Notice period – fixed-term contract',
    30, 'DAYS', null, 'HR',       -- D-07 correction: HR, not NULL
    false, true
);
