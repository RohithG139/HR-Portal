# Generate Stack Agents

Scan this project's manifests and auto-generate any missing specialist agent files.

**Command:** `/generate-stack-agents`

---

## Steps

### Step 1 — Scan & Report

```bash
python scripts/agent_factory.py --scan .
```

Print the JSON output to the user so they can see what stacks were detected.

### Step 2 — Generate Missing Agents & Sync Settings

```bash
python scripts/agent_factory.py --generate . --dry-run
```

Show the dry-run preview first (which agents would be created, which settings would change).
Then ask the user to confirm before running without `--dry-run`.

After confirmation:

```bash
python scripts/agent_factory.py --generate .
```

### Step 3 — Summary

Report to the user:
- Which agent `.md` files were created (or already existed)
- Which permissions were added to `.claude/settings.json`
- Updated routing rules (mention the new agents in context of what tasks they handle)

---

## Notes

- Generation is **idempotent** — safe to run multiple times; existing agents are never overwritten.
- For PHP/Laravel + Livewire projects: generates both `laravel-architect.md` and `livewire-architect.md`.
- After generation, `/implement-feature` will automatically route to the new specialist agents.
- To preview without writing: `python scripts/agent_factory.py --scan . --dry-run`
