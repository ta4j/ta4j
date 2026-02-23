# AGENTS instructions for `ta4j/scripts`

Use this guide when editing helper scripts or when orchestrating worktree/process commands from this directory.

## Build script contract

- `run-full-build-quiet.sh` must enforce a default 3-minute timeout (`QUIET_BUILD_TIMEOUT_SECONDS` can override).
- Preserve quiet-build behavior: full log under `.agents/logs/` and concise aggregated stdout.
- Use `mktemp` templates ending in `XXXXXX` for macOS portability.

## Scoped AGENTS discovery helper

- `agents_for_target.sh` should work from repo root or workspace root.
- Accept either a class/file name or a path, and print prevailing `AGENTS.md` files in precedence order.

## Worktree/process operations

- For non-trivial work, prefer dedicated worktrees under `.agents/worktrees/`.
- Keep a living PRD/checklist during implementation so work can resume with minimal rediscovery.
- Use branch prefixes `feature/`, `bugfix/`, or `refactor/`.
