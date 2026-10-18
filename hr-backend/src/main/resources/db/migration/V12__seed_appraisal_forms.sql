-- ============================================================
-- V11__seed_appraisal_forms.sql — FRD-08 (Settings foundation) §9
--
-- Minimum viable seed for the Standard, Leadership and Probation appraisal
-- forms: each status='ACTIVE' with at least one section and one question, so
-- FRD-05/FRD-06's own stated pre-condition ("at least one active appraisal
-- form exists") is met before those features are built (F01 never touched
-- these tables at all — V2__seed_settings.sql seeds no appraisal_form rows,
-- confirmed on inspection, spec §12 D-05).
--
-- Exact section/question wording is not given by the FRD (it names the forms
-- and their "used for" values, nothing more) — this content is a reasonable
-- starting set, [ASSUMPTION], editable by HR afterward via this same
-- feature's form editor (S4), same placeholder-content precedent as F01's
-- own checklist templates (F01 spec D-12).
--
-- label_1..label_5 are left at their table defaults (Poor / Below
-- expectations / Meets expectations / Exceeds expectations / Outstanding),
-- which already match the FRD's own rating-scale wording.
-- ============================================================

-- Standard — used by most employees.
with form as (
    insert into hr.appraisal_form (name, used_for, status, overall_score_method)
    values ('Standard', 'STANDARD', 'ACTIVE', 'WEIGHTED_AVERAGE')
    returning id
),
sec1 as (
    insert into hr.form_section (form_id, name, by_self, by_peer, by_manager, by_hr, sort_order)
    select id, 'Results & KPIs', true, false, true, false, 0 from form
    returning id
),
sec2 as (
    insert into hr.form_section (form_id, name, by_self, by_peer, by_manager, by_hr, sort_order)
    select id, 'Core competencies', true, true, true, false, 1 from form
    returning id
)
insert into hr.form_question (section_id, text, description, answer_type, required, sort_order)
select id, 'Did the employee meet their KPI targets this period?', null, 'SCALE_1_5', true, 0 from sec1
union all
select id, 'How well does the employee demonstrate the core competencies expected for their role?', null, 'SCALE_1_5', true, 0 from sec2;

-- Leadership — tech leads & managers.
with form as (
    insert into hr.appraisal_form (name, used_for, status, overall_score_method)
    values ('Leadership', 'LEADERSHIP', 'ACTIVE', 'WEIGHTED_AVERAGE')
    returning id
),
sec1 as (
    insert into hr.form_section (form_id, name, by_self, by_peer, by_manager, by_hr, sort_order)
    select id, 'Leadership & team impact', true, false, true, false, 0 from form
    returning id
)
insert into hr.form_question (section_id, text, description, answer_type, required, sort_order)
select id, 'How effectively does this person lead and develop their team?', null, 'SCALE_1_5', true, 0 from sec1;

-- Probation — probation reviews.
with form as (
    insert into hr.appraisal_form (name, used_for, status, overall_score_method)
    values ('Probation', 'PROBATION', 'ACTIVE', 'MANAGER_DECIDES')
    returning id
),
sec1 as (
    insert into hr.form_section (form_id, name, by_self, by_peer, by_manager, by_hr, sort_order)
    select id, 'Probation outcome', false, false, true, false, 0 from form
    returning id
)
insert into hr.form_question (section_id, text, description, answer_type, required, sort_order)
select id, 'Has the employee met the expectations of the role during probation?', null, 'YES_NO', true, 0 from sec1;
