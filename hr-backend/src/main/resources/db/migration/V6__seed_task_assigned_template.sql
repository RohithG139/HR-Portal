-- ============================================================
-- G-04 (FRD-00): "Every new task creates an in-app notification and an
-- e-mail to its owner." FRD-08's "E-mail templates" table names this
-- template "Task assigned", trigger "A task is assigned to a user",
-- recipient = the task owner (to_task_owner).
--
-- Body is a placeholder: the FRD gives no exact wording for this template
-- (same situation as SET_PASSWORD in V2__seed_settings.sql) — flagged here
-- for HR to edit later (F09, e-mail template management).
-- ============================================================

insert into email_template
    (code, name, trigger_description, subject, body,
     to_employee, to_manager, to_hr, to_it_admin, to_task_owner, to_peer,
     active)
values
(
    'TASK_ASSIGNED',
    'Task assigned',
    'A task is assigned to a user',
    'New task: {task_name}',
    -- placeholder body: the FRD gives no exact wording for this template.
    E'Hi {recipient_first_name},\n\nA new task "{task_name}" has been assigned to you.\n\nOpen it: {task_link}',
    false,  -- to_employee
    false,  -- to_manager
    false,  -- to_hr
    false,  -- to_it_admin
    true,   -- to_task_owner
    false,  -- to_peer
    true    -- active
);
