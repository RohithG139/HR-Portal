-- ============================================================
-- V15__job_title_grade_department.sql — FRD-08 S1 (D-01 reversed, user decision 2026-10-19)
--
-- Adds the Grade and Department the wireframe's Job titles table and
-- "Add job title" pop-up (m-job-add) show — FRD-08 v1.2 Appendix A #8's
-- own suggested fix ("add them back to the table").
--
-- Both columns are nullable: job titles seeded before this migration have
-- no grade/department and are shown as "—". The API requires both for
-- every new job title (JobTitleRequest).
--
-- grade uses the same fixed list as employee.grade (R-DB-2: varchar + CHECK).
-- department_id is ON DELETE RESTRICT (default), so a department used by a
-- job title cannot be removed (ADM-03 "This item is in use and cannot be removed.").
-- ============================================================

alter table job_title
  add column grade varchar(10)
    check (grade in ('JUNIOR','MEDIOR','SENIOR','SENIOR_I','SENIOR_II')),
  add column department_id bigint references department;

create index ix_job_title_department on job_title(department_id);
