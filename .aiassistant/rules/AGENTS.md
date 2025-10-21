---
apply: always
---

AI assistants must discover, merge, and apply rules from all sources for consistent, context-aware behavior.

# Rule Sources & Priority (lowest → highest):

1. Root AGENTS.md (global defaults)
2. .cursor/rules/*.md (project-level supplements)
3. Nearest AGENTS.md (directory + subdirs recursively)

# Behavior:

- Search upward from current dir to root for all AGENTS.md files.
- Load .cursor/rules/*.md if directory exists.
- Merge all applicable rules; deeper/local overrides broader/global.
- Retain non-conflicting rules.
- Re-scan when directory or file context changes.
- Treat rules as binding behavioral policy, not documentation.
- Continue gracefully if some rule sources are missing.
- Keep internal awareness of which sources influence output.
