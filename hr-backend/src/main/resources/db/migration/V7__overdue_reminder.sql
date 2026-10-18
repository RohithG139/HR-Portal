-- ============================================================
-- G-05 (FRD-00): "An overdue reminder e-mail is sent 1 day after the due date
-- (configurable, FRD-08)." FRD-08's "Timers & rules" table independently
-- confirms: "Task overdue | 1 day after due | Task owner | Yes" (switchable
-- off, unlike the non-switchable ONBOARDING_OVERDUE timer in V2).
--
-- checklist_item already has no reminder-tracking column (engine_task got
-- overdue_reminder_at in V1 but it was never written to — see spec.md §7).
-- ============================================================

alter table hr.checklist_item add column overdue_reminder_at timestamptz;

comment on column hr.checklist_item.overdue_reminder_at is
    'G-05: timestamp when the overdue reminder e-mail was sent for this item; '
    'null means not yet overdue or not yet reminded.';

insert into hr.setting
    (code, category, module, label, value_int, value_unit, recipient_role, can_disable, enabled)
values
(
    'OVERDUE_REMINDER_DAYS',
    'TIMER',
    'Onboarding',
    'Overdue task reminder',
    1,
    'DAYS',
    'TASK_OWNER',
    true,   -- can_disable: FRD-08 lists this rule as "Can be switched off: Yes"
    true    -- enabled
);

-- G-05's e-mail template, named "Task overdue" by FRD-08 (trigger: "1 day
-- after the due date"). Body is a placeholder — same treatment as
-- TASK_ASSIGNED (V6) and SET_PASSWORD (V2): the FRD gives no exact wording.
insert into hr.email_template
    (code, name, trigger_description, subject, body,
     to_employee, to_manager, to_hr, to_it_admin, to_task_owner, to_peer,
     active)
values
(
    'TASK_OVERDUE',
    'Task overdue',
    '1 day after the due date',
    'Overdue: {task_name}',
    E'Hi {recipient_first_name},\n\nThe task "{task_name}" is now overdue.\n\nOpen it: {task_link}',
    false,  -- to_employee
    false,  -- to_manager
    false,  -- to_hr
    false,  -- to_it_admin
    true,   -- to_task_owner
    false,  -- to_peer
    true    -- active
);
