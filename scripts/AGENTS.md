# AGENTS instructions for ta4j/scripts

- `run-full-build-quiet.sh` must enforce a default 3-minute timeout (override via `QUIET_BUILD_TIMEOUT_SECONDS`).
- Keep the quiet-build log filtering behavior intact (full log in `.agents/logs`, summarized stdout).
- Use `mktemp` templates with trailing `XXXXXX` to remain portable on macOS.
