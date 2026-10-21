-- ============================================================
-- V16: add 'HELD' to email_outbox.status (2026-10-21)
--
-- Engine-triggered onboarding e-mails: "Welcome new employee" is now written HELD instead of
-- PENDING inside the same business transaction as the employee record (R-ARC-6's guarantee is
-- unaffected — still durably queued atomically with the domain change). The case engine's
-- "Release onboarding emails" process task calls back once the employee's case actually starts,
-- and that callback (or OnboardingEmailReleaseScheduler's fallback, if the callback never
-- arrives) is what flips the row to PENDING so EmailOutboxSenderJob picks it up.
--
-- Postgres auto-named the original CHECK constraint (no explicit name given in V1__init.sql);
-- the default naming convention is <table>_<column>_check.
-- ============================================================

alter table email_outbox
  drop constraint email_outbox_status_check;

alter table email_outbox
  add constraint email_outbox_status_check
  check (status in ('PENDING','SENT','FAILED','HELD'));
