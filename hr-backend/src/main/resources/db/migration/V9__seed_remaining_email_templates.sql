-- ============================================================
-- V9__seed_remaining_email_templates.sql — FRD-08 (Settings foundation) §9
--
-- Adds the 15 email_template rows needed to reach the FRD's full 21-template
-- set. 6 are already seeded (WELCOME_NEW_EMPLOYEE, SET_PASSWORD,
-- IT_EQUIPMENT_REQUEST, ONBOARDING_COMPLETED in V2; TASK_ASSIGNED in V6;
-- TASK_OVERDUE in V7); this migration adds the rest. 21 - 6 = 15.
--
-- Trigger descriptions are transcribed verbatim from FRD-08 v1.2's "E-mail
-- templates" table. Recipient role flags are NOT given by that table (it only
-- has Template/Sent-when columns) except where the FRD's own prose spells
-- the recipient out explicitly — flagged per-row below. Everywhere else,
-- recipients are a reasonable inference from the trigger's business meaning,
-- same [ASSUMPTION] treatment already used for SET_PASSWORD/TASK_ASSIGNED/
-- TASK_OVERDUE's placeholder bodies (F01 precedent) — editable by HR via this
-- same feature's e-mail template screen (S5) afterward. Bodies are placeholder
-- text for the same reason: the FRD gives exact wording for none of the 21
-- templates' bodies.
-- ============================================================

insert into hr.email_template
    (code, name, trigger_description, subject, body,
     to_employee, to_manager, to_hr, to_it_admin, to_task_owner, to_peer,
     active)
values
(
    -- [ASSUMPTION] recipient: the user requesting their own reset.
    'RESET_PASSWORD',
    'Reset password',
    'A user selects Forgot password',
    'Reset your password for CubeWorks HR',
    E'Hi {employee_first_name},\n\nWe received a request to reset your password. Use the link below to choose a new one:\n\n{task_link}\n\nIf you did not request this, you can ignore this e-mail.',
    true, false, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the manager performs the probation review.
    'PROBATION_REVIEW_DUE',
    'Probation review due',
    '14 days before probation ends',
    'Probation review due: {employee_name}',
    E'Hi {manager_name},\n\n{employee_name}''s probation period is ending soon. Please complete the probation review.\n\nOpen it: {task_link}',
    false, true, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: HR owns contract renewal/offboarding decisions.
    'CONTRACT_ENDING',
    'Contract ending',
    '60 days before a fixed-term contract ends',
    'Contract ending soon: {employee_name}',
    E'Hi HR,\n\n{employee_name}''s fixed-term contract is ending on {last_day}. Please review renewal or offboarding options.\n\nOpen it: {task_link}',
    false, false, true, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the employee is prompted to complete their self-review.
    'APPRAISAL_ROUND_OPENED',
    'Appraisal round opened',
    'HR starts a round',
    'Your appraisal round has started',
    E'Hi {employee_first_name},\n\nA new appraisal round has started. Please complete your self-review.\n\nOpen it: {task_link}',
    true, false, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the chosen peer is asked to complete the review.
    'PEER_REVIEW_REQUEST',
    'Peer review request',
    'An employee chooses peers',
    'Peer review request for {employee_name}',
    E'Hi,\n\n{employee_name} has asked you to complete a peer review.\n\nOpen it: {task_link}',
    false, false, false, false, false, true,
    true
),
(
    -- [ASSUMPTION] recipient: both the employee and their manager learn the outcome,
    -- same reasoning as ONBOARDING_COMPLETED (V2, F01 D-21).
    'APPRAISAL_FINALISED',
    'Appraisal finalised',
    'HR finalises an appraisal',
    'Appraisal finalised for {employee_name}',
    E'Hi {recipient_first_name},\n\nThe appraisal for {employee_name} has been finalised.\n\nView it: {task_link}',
    true, true, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the manager reviews the submitted plan.
    'DEV_PLAN_SUBMITTED',
    'Development plan submitted',
    'The employee submits a plan',
    'Development plan submitted: {employee_name}',
    E'Hi {manager_name},\n\n{employee_name} has submitted a development plan for your review.\n\nOpen it: {task_link}',
    false, true, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the employee is told the decision on their own plan.
    'DEV_PLAN_DECISION',
    'Development plan decision',
    'The manager approves or requests changes',
    'Update on your development plan',
    E'Hi {employee_first_name},\n\nYour manager has reviewed your development plan.\n\nOpen it: {task_link}',
    true, false, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: the employee being assigned.
    'PROJECT_ASSIGNED',
    'Project assigned',
    'An employee is assigned to a project',
    'You''ve been assigned to a project',
    E'Hi {employee_first_name},\n\nYou have been assigned to a new project.\n\nOpen it: {task_link}',
    true, false, false, false, false, false,
    true
),
(
    -- Recipients confirmed by spec §11 AC-03 (verbatim FRD acceptance scenario):
    -- "the e-mail is sent to Kees [employee], to his manager Anouk Peters and to HR."
    'PIP_NOTICE',
    'PIP notice',
    'HR approves a PIP',
    'Performance Improvement Plan for {employee_name}',
    E'Hi {recipient_first_name},\n\nA Performance Improvement Plan has been started for {employee_name}.\n\nOpen it: {task_link}',
    true, true, true, false, false, false,
    true
),
(
    -- Recipients confirmed by the FRD's own trigger text: "(to HR and the manager)".
    'PIP_DISPUTED',
    'PIP disputed',
    'An employee disputes a PIP (to HR and the manager)',
    'PIP disputed: {employee_name}',
    E'Hi {recipient_first_name},\n\n{employee_name} has disputed their Performance Improvement Plan.\n\nOpen it: {task_link}',
    false, true, true, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: employee and manager, same pairing as PIP_NOTICE minus HR
    -- (the final outcome is primarily between employee and manager).
    'PIP_OUTCOME',
    'PIP outcome',
    'The final PIP review is confirmed',
    'PIP outcome confirmed: {employee_name}',
    E'Hi {recipient_first_name},\n\nThe final outcome of the Performance Improvement Plan for {employee_name} has been confirmed.\n\nOpen it: {task_link}',
    true, true, false, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: HR processes the resignation, the manager needs to know.
    'RESIGNATION_RECEIVED',
    'Resignation received',
    'An employee submits a resignation',
    'Resignation received: {employee_name}',
    E'Hi {recipient_first_name},\n\n{employee_name} has submitted their resignation, with last working day {last_day}.\n\nOpen it: {task_link}',
    false, true, true, false, false, false,
    true
),
(
    -- [ASSUMPTION] recipient: HR runs the exit process, IT admin removes equipment/access.
    'OFFBOARDING_STARTED',
    'Offboarding started',
    'Offboarding starts',
    'Offboarding started: {employee_name}',
    E'Hi {recipient_first_name},\n\nOffboarding has started for {employee_name}, last working day {last_day}.\n\nOpen it: {task_link}',
    false, false, true, true, false, false,
    true
),
(
    -- Recipient confirmed by the Timers & rules table: "Asset return reminder | ... | Employee | Yes".
    'ASSET_RETURN_REMINDER',
    'Asset return reminder',
    '3 days before the last working day',
    'Please return your company assets',
    E'Hi {employee_first_name},\n\nYour last working day is {last_day}. Please arrange to return any company assets you still hold.',
    true, false, false, false, false, false,
    true
);
