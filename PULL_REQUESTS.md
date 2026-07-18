# ta4j Pull Request Overrides

These repo-local instructions override the user-level PR defaults for ta4j. Apply only the ID-targeted directives below; unscoped prose is additive.

Override PR.RULE.PUSH_POLICY:
ta4j is public. Existing non-default upstream branches may be pushed without asking, but the first push of a new remote branch requires explicit approval. Any push to `master` or the default branch also requires explicit approval.

Extend PR.RULE.CHANGELOG_STYLE:
For ta4j PRs, user-visible behavior changes, public API changes, bug fixes, build/release behavior changes, and documentation changes that affect user or maintainer workflows require an entry in `CHANGELOG.md` under `Unreleased`. Skip only for changes that are exclusively test-only, internal cleanup with no user/maintainer-visible behavior change, or documentation-only edits that do not change documented behavior or workflow expectations. If a changelog entry is intentionally skipped for a non-trivial change, state the reason in the PR notes or handoff.

Disable PDL.TASK.PR_MERGE:
Terminal condition is a ready, non-draft PR with green local verification, acceptable CI, a clean final PR sweep, no unresolved conversations, and explicit human merge handoff. Do not merge into `master` automatically.

Override PDL.TASK.LINEAR_CLOSEOUT:
Unless a human merge happens in the same run, move the source issue/document to review or ready-for-merge state, not done. Mark done only after the PR is actually merged.

Extend PDL.TASK.HANDOFF_REPORT:
Report that ta4j is public and the PR is intentionally left open for public viewing and human merge. Before final handoff, delete temporary ignored PRD/checklist files under `.agents/plans/` unless the user explicitly asks to retain them. If one is intentionally retained, call out the path and reason in the handoff.

Require QAS.TASK.PR_HANDOFF:
Immediately after opening a ta4j PR, post `@coderabbitai full review` as a PR comment. Do not substitute the incremental `@coderabbitai review` command. Treat the PR as not ready for terminal handoff until CodeRabbit finishes the full review or explicitly reports that it cannot review the PR.

Override QAS.TASK.MERGE_POLICY:
ta4j requires a human viewing opportunity before `master` changes. Automation prepares, verifies, monitors, fixes feedback, resolves conversations, and hands off the ready PR.

Require QAS.TASK.FINAL_PR_SWEEP:
Before handoff, perform one live final sweep for top-level comments, actionable reviews, and unresolved review threads.
