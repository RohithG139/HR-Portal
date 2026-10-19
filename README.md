# CubeWorks HR

CubeWorks HR manages the employee lifecycle — onboarding, projects, development and KPIs,
appraisals, PIPs and offboarding — on top of the [CaseFabric](https://casefabric.com) case
engine. Each employee is one CaseFabric case; the engine decides what happens next (stages,
tasks, timers, milestones, case roles), and a Spring Boot backend owns all business data in
PostgreSQL. React talks only to Spring Boot — never to the engine or to Keycloak directly.

```
React (hr-frontend) ──REST/JSON──► Spring Boot BFF (hr-backend) ──REST──► CaseFabric engine
                                          │                                    │
                                          ▼                                    ▼
                          Postgres #1: hr_app (schema hr)      Postgres #2: case-engine-eventstore / -query
                                          ▲
                                     Keycloak (login/users/roles)
```

See [CLAUDE.md](CLAUDE.md) for the full architecture, data model and engineering rules, and
[.claude/docs/architecture/](.claude/docs/architecture/) for the detailed data model and case
file reference.

## Repository layout

```
casefabric-new/
├── docker-compose.yml        This app's infra + dev containers: hr-db-postgres, Keycloak, hr-backend, hr-frontend
├── infra/postgres-initdb/    Init script that creates schema `hr` in hr_app
├── infra/keycloak/           Realm import (cubeworks-hr-realm.json) + login theme
├── hr-backend/               Spring Boot 3 / Java 21 backend (Maven)
├── hr-frontend/              React 19 / TypeScript / Vite frontend
├── casefabric/getting-started/  CaseFabric engine — vendored source (its own docker-compose.yml, never edited)
└── .claude/                  Claude Code config, agents, specs and architecture docs for this project
```

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Compose v2) — the
  recommended way to run everything below.
- Git.
- Only needed if you prefer running services directly on the host instead of in Docker:
  - Java 21 and Maven (or use the bundled `./mvnw`)
  - Node.js 22+ and npm

## 1. Clone

```bash
git clone https://github.com/RohithG139/HR-Portal.git casefabric-new
cd casefabric-new
```

The CaseFabric engine source (`casefabric/getting-started/`) is vendored directly in this
repo, so no separate clone is needed. It still behaves as its own independent
project in every other sense — its `docker-compose.yml` is never edited from here (see
[CLAUDE.md §1.3](CLAUDE.md)) and it runs as a separate compose stack (step 2 below).

## 2. Start the CaseFabric engine stack

```bash
docker compose -f casefabric/getting-started/docker-compose.yml up -d
```

This starts (all on `localhost`):

| Service | Port |
|---|---|
| CaseFabric engine (via nginx) | 2027 |
| CaseFabric IDE / model editor | 2081 |
| CaseFabric engine UI | 3317 |
| PostgreSQL (`case-engine-eventstore`, `case-engine-query`) | 5431 |
| Mailcatcher (all outgoing dev e-mail; SMTP 1025) | 1080 |

## 3. Start the HR app stack

From the repo root:

```bash
docker compose up -d
```

This builds and starts:

| Service | Port | URL |
|---|---|---|
| hr-frontend (Vite dev server, HMR) | 5173 | http://localhost:5173 |
| hr-backend (Spring Boot) | 8080 | http://localhost:8080/api |
| PostgreSQL (`hr_app`, schema `hr`) | 5433 | — |
| Keycloak | 8180 | http://localhost:8180 |

Keycloak auto-imports the `cubeworks-hr` realm (roles, the `hr-frontend` OIDC client, and
four fixed test users) on every start — no manual setup needed. First start is slow
(Maven downloads dependencies into a named volume, Postgres initializes); watch progress
with:

```bash
docker compose logs -f hr-backend hr-frontend
```

Once `hr-backend` reports healthy, create the demo users' employee records (first run only —
without this, login loops back to the login page):

```bash
docker exec -i hr-db-postgres psql -U postgres -d hr_app < infra/dev-seed/seed-demo-users.sql
```

Then open **http://localhost:5173** and log in with one of:

| Username | Password | Role |
|---|---|---|
| `hr1` | `Password1234` | HR |
| `manager1` | `Password1234` | Manager |
| `employee1` | `Password1234` | Employee |
| `itadmin1` | `Password1234` | IT Admin |

### Applying Java source changes

The backend dev container does not hot-reload Java changes yet. After editing
`hr-backend` source, restart it to recompile:

```bash
docker compose restart hr-backend
```

Frontend changes hot-reload automatically through Vite.

### Stopping

```bash
docker compose down                                             # HR app stack
docker compose -f casefabric/getting-started/docker-compose.yml down   # engine stack
```

Add `-v` to also drop the named volumes (database data, Maven cache, node_modules) for a
clean slate.

## Alternative: running hr-backend / hr-frontend directly on the host

Keep `hr-db-postgres` and `keycloak` running in Docker (`docker compose up -d hr-db-postgres keycloak`
from the repo root), then run the apps on the host instead of in their dev containers — not at
the same time as the containerized versions (port 8080 / 5173 conflicts):

```bash
# backend
cd hr-backend
./mvnw spring-boot:run

# frontend (separate terminal)
cd hr-frontend
cp .env.example .env   # adjust if needed — defaults already match the Docker setup
npm install
npm run dev
```

## Running tests

```bash
# backend
cd hr-backend
./mvnw verify

# frontend
cd hr-frontend
npm test
npm run build
```

## Project documentation

- [CLAUDE.md](CLAUDE.md) — architecture, global engineering rules, way of working
- [.claude/docs/architecture/](.claude/docs/architecture/) — stack overview, full data model, case file reference
- [.claude/docs/features/](.claude/docs/features/) — per-feature specs and wireframes, feature status index
