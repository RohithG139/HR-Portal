# /create-migration

Generate a safe, zero-downtime database migration file for the detected ORM/framework.

## Usage

```
/create-migration [name]
```

**Arguments (`$ARGUMENTS`):**
- `name` — snake_case descriptor for the migration (e.g. `add_email_to_users`, `create_orders_table`)

## What Gets Generated

| ORM / Framework | Output |
|----------------|--------|
| Java + Flyway | `src/main/resources/db/migration/V<timestamp>__<name>.sql` |
| Java + Liquibase | `src/main/resources/db/changelog/changes/<timestamp>-<name>.xml` |
| Laravel | `database/migrations/<timestamp>_<name>.php` (via `php artisan make:migration`) |
| Python + Alembic | `alembic/versions/<hash>_<name>.py` (via `alembic revision`) |
| Python + Django | `<app>/migrations/<number>_<name>.py` (via `python manage.py makemigrations`) |
| Prisma | Updated `prisma/schema.prisma` + `npx prisma migrate dev --name <name>` |

## Execution Steps

### Step 0 — Parse Arguments

Extract `NAME` from `$ARGUMENTS`. If missing, use `migration` as default.

### Step 1 — Detect ORM

Scan project manifests to determine the ORM:

| Signal | ORM |
|--------|-----|
| `src/main/resources/db/migration/` exists | Flyway (Java) |
| `src/main/resources/db/changelog/` exists | Liquibase (Java) |
| `database/migrations/` exists + `composer.json` | Laravel Eloquent |
| `alembic.ini` or `alembic/` directory exists | SQLAlchemy + Alembic |
| `manage.py` exists | Django ORM |
| `prisma/schema.prisma` exists | Prisma |

Report detected ORM:
```
DETECTED ORM: [Flyway | Liquibase | Laravel | Alembic | Django | Prisma]
```

If ORM cannot be determined, ask: "Which migration tool does this project use?"

### Step 2 — Describe the Schema Change

Ask the user one focused question:

```
What schema change should this migration make?
Examples:
  - Add nullable column: users.last_login_at (TIMESTAMPTZ)
  - Create table: orders (id UUID PK, user_id FK, total NUMERIC(10,2), created_at TIMESTAMPTZ)
  - Add index: orders.user_id (B-Tree, non-unique)
  - Drop column: users.legacy_token
```

### Step 3 — Design with postgres-architect

Delegate to `@postgres-architect` to produce zero-downtime SQL:
- `ADD COLUMN` with `DEFAULT` only when safe (Postgres 11+ for non-volatile defaults)
- `CREATE INDEX CONCURRENTLY` for new indexes — never without `CONCURRENTLY`
- `ADD CONSTRAINT ... NOT VALID` + separate `VALIDATE CONSTRAINT` for FK constraints
- Include a rollback / `down` SQL block

### Step 4 — Generate Migration File

Route to the correct format:

**Laravel:**
```bash
php artisan make:migration <NAME>
```
Then fill the generated `up()` and `down()` methods with the SQL from Step 3.

**Flyway (Java):**
Write `src/main/resources/db/migration/V<YYYYMMDDHHMMSS>__<NAME>.sql` with forward SQL.
Include rollback SQL in a comment block at the bottom (`-- rollback:`).

**Alembic (Python):**
```bash
alembic revision --autogenerate -m "<NAME>"
```
Review the auto-generated file; patch `upgrade()` and `downgrade()` from Step 3.

**Django:**
```bash
python manage.py makemigrations --name <NAME>
```
Verify the generated migration matches the intended change.

**Prisma:**
Update `prisma/schema.prisma` with the schema change, then:
```bash
npx prisma migrate dev --name <NAME>
```

### Step 5 — Safety Checklist

Before finishing, verify:
- [ ] New indexes use `CONCURRENTLY` (Flyway/Alembic/raw SQL)
- [ ] No `ALTER TABLE ... ADD COLUMN ... DEFAULT <volatile>` on large tables
- [ ] Rollback / `down()` logic is present and correct
- [ ] No `DROP COLUMN` without a preceding deprecation migration

Report generated file path(s) and any safety notes.

## Examples

```bash
/create-migration add_email_verified_at_to_users
/create-migration create_audit_log_table
/create-migration add_index_orders_user_id
```

## Related

- Agent: [`postgres-architect`](.claude/agents/postgres-architect.md)
- Known pitfall: `CREATE INDEX CONCURRENTLY` cannot run inside a transaction — Flyway uses `-- nontransactional` marker.
