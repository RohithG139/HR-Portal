-- ============================================================
-- V17: Probation review (FRD-02 probation half, F02) — 2026-10-21
--
-- 1. review_answer — documented in 02-data-model.md §2.6 and FRD-02 v1.2 §5, never
--    created by V1..V16 (same design-doc vs applied-schema gap as V11/V13). DDL
--    transcribed from 02-data-model.md + the standard audit columns (R-DB-2; FRD-02 v1.2 §5:
--    "Every table also carries the audit columns ... added by the migration").
-- 2. ix_review_contract — listed in FRD-02 v1.2 §5's review indexes, missing from V13.
-- 3. v_contract — 02-data-model.md §2.9 (ONB-01: probation end = start + months), never
--    created. The probation-review scheduler reads probation_end_date from it.
-- 4. Probation appraisal form content — V12 seeded one YES_NO placeholder question
--    ([ASSUMPTION] there). FRD-02 §2 "The manager rates quality of work, collaboration and
--    learning" and WF-02 p-mg-probation give the three 1–5 questions exactly. No
--    review_answer rows can reference the old question (the table is created above).
-- ============================================================

create table review_answer (
  review_id   bigint not null references review on delete cascade,
  question_id bigint not null references form_question,
  rating      smallint check (rating between 1 and 5),
  yes_no      boolean,
  text_answer text,
  comment     text,
  primary key (review_id, question_id),
  check (num_nonnulls(rating, yes_no, text_answer) <= 1),
  created_at  timestamptz not null default now(),
  created_by  bigint      references employee,
  updated_at  timestamptz not null default now(),
  updated_by  bigint      references employee,
  version     integer     not null default 0
);

create index ix_review_contract on review(contract_id) where contract_id is not null;
create index ix_review_reviewer on review(reviewer_id);

create view v_contract as
select c.*,
       (c.start_date + (c.probation_months * interval '1 month'))::date as probation_end_date
from contract c;

-- Probation form: replace the placeholder question with the three FRD/WF-02 ratings.
delete from form_question q
using form_section s, appraisal_form f
where q.section_id = s.id and s.form_id = f.id
  and f.used_for = 'PROBATION' and f.name = 'Probation'
  and q.text = 'Has the employee met the expectations of the role during probation?';

update form_section s set name = 'Probation review'
from appraisal_form f
where s.form_id = f.id and f.used_for = 'PROBATION' and f.name = 'Probation'
  and s.name = 'Probation outcome';

insert into form_question (section_id, text, description, answer_type, required, sort_order)
select s.id, v.text, null, 'SCALE_1_5', true, v.sort_order
from form_section s
join appraisal_form f on f.id = s.form_id
cross join (values ('Quality of work', 0),
                   ('Collaboration & communication', 1),
                   ('Learning & independence', 2)) as v(text, sort_order)
where f.used_for = 'PROBATION' and f.name = 'Probation' and s.name = 'Probation review';

-- Summary: FRD-02 §4.7 stores the probation review in review + review_answer only (no summary
-- column on review), so the manager's summary is the form's TEXT question, answered in
-- review_answer.text_answer. Its "required" message is FRD-02 §4's
-- "Add a summary and choose an outcome." (spec D-P3).
insert into form_question (section_id, text, description, answer_type, required, sort_order)
select s.id, 'Summary', null, 'TEXT', true, 3
from form_section s
join appraisal_form f on f.id = s.form_id
where f.used_for = 'PROBATION' and f.name = 'Probation' and s.name = 'Probation review';
