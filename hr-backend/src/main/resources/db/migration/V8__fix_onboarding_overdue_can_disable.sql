-- ============================================================
-- FRD-08 (Settings foundation), §12 D-09 — user-approved 2026-10-16.
--
-- V2__seed_settings.sql (already applied, F01) seeded ONBOARDING_OVERDUE with
-- can_disable = false, based on the pre-v1.2 draft's best reading of the
-- garbled v1.1 "Timers & rules" table. FRD v1.2's clean table instead reads
-- "Onboarding overdue | 3 days after start date | HR | Yes" — switchable off.
-- V2 is an already-applied migration and is never edited (CLAUDE.md); this
-- forward migration corrects the one column that was wrong.
-- ============================================================

update hr.setting
set can_disable = true
where code = 'ONBOARDING_OVERDUE';
