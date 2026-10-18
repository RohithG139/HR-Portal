-- ============================================================
-- V4__notification_add_message.sql
--
-- Adds a free-text message column to notification so that the
-- equipment-remark notification (A28/AC-13) can carry the full
-- remark text that IT admin needs to see.
--
-- The column is nullable: existing notifications and system-generated
-- notifications without a remark simply leave message NULL.
-- ============================================================

ALTER TABLE hr.notification ADD COLUMN message text;
