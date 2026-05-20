# AGENTS instructions for `ta4j/scripts`

Use this guide when editing helper scripts or when orchestrating worktree/process commands from this directory.

## Build script contract

- `run-full-build-quiet.sh` must enforce a default 3-minute timeout (`QUIET_BUILD_TIMEOUT_SECONDS` can override).
- Preserve quiet-build behavior: full log under `.agents/logs/` and concise aggregated stdout.
- Use `mktemp` templates ending in `XXXXXX` for macOS portability.

## Scoped AGENTS discovery helper

- `agents_for_target.sh` should work from repo root or workspace root.
- Accept either a class/file name or a path, and print path-scoped `AGENTS.md` files in precedence order for matching targets from the current repo/workspace root.
- Keep the helper limited to path-scoped `AGENTS.md` discovery; agents must still load system, developer, user, and workflow-specific instructions separately.

## Worktree/process operations

- For non-trivial work, prefer dedicated worktrees under `.agents/worktrees/`.
- Keep a living PRD/checklist during implementation so work can resume with minimal rediscovery.
- Use branch prefixes `feature/`, `bugfix/`, or `refactor/`.

## Release helper scripts

- Keep release workflow business logic in reusable scripts under `scripts/release/` instead of copying large logic blocks into workflow YAML.
- When adding release helper behavior, add fixture coverage under `scripts/tests/` and keep GitHub Actions outputs/artifacts sanitized.
- Release helper scripts should emit concise `audit:` lines for workflow logs and write structured files that can be uploaded with `actions/upload-artifact`.
