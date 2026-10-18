-- ============================================================
-- V3__engine_task_surrogate_id.sql
--
-- Adds a surrogate bigint primary key to engine_task so the
-- undo endpoint (A37) can accept a stable numeric taskId in the
-- URL path: POST /api/tasks/{taskId}/undo.
--
-- The original engine_task_id varchar(64) remains unique (NOT NULL)
-- and continues to be the key used for engine API calls; it is just
-- no longer the primary key.
--
-- Execution order:
--   1. Drop the existing varchar PK.
--   2. Add id bigint GENERATED ALWAYS AS IDENTITY (auto-populated
--      for any existing rows via ALTER COLUMN).
--   3. Add the new PK constraint on id.
--   4. Add a UNIQUE constraint on engine_task_id to preserve uniqueness
--      and keep the existing index semantics.
-- ============================================================

-- Step 1: drop the varchar primary key
ALTER TABLE hr.engine_task DROP CONSTRAINT engine_task_pkey;

-- Step 2: add the surrogate identity column
ALTER TABLE hr.engine_task ADD COLUMN id bigint GENERATED ALWAYS AS IDENTITY;

-- Step 3: make it the new primary key
ALTER TABLE hr.engine_task ADD PRIMARY KEY (id);

-- Step 4: restore uniqueness on the engine-assigned task id
ALTER TABLE hr.engine_task ADD CONSTRAINT uq_engine_task_engine_task_id UNIQUE (engine_task_id);
