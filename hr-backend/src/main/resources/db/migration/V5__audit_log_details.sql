-- ============================================================
-- V5__audit_log_details.sql
--
-- audit_log.details text
--
-- D-04/AC-24 requires History to show "done by HR on behalf of {owner}" when HR
-- ticks another role's checklist item. The before_value/after_value jsonb columns
-- capture state snapshots, not a human-readable circumstance note, so a nullable
-- text column is added for this. Most audit rows leave it null; it is only set
-- when the action needs supplementary context beyond the before/after snapshots
-- (see AuditLogService.record(..., details) and OnboardingService.tickItem).
-- ============================================================

ALTER TABLE hr.audit_log ADD COLUMN details text;
