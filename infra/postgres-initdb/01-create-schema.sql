-- hr_app is created by the POSTGRES_DB env var on first boot (docker-compose.yml).
-- This script only adds the schema that Flyway migrates into (CLAUDE.md R-DB-2: schema `hr`).
CREATE SCHEMA IF NOT EXISTS hr;
