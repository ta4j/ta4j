# ta4j Pull Request Overrides

These repo-local instructions override the user-level PR defaults for ta4j. Apply only the ID-targeted directives below; unscoped prose is additive.

Override PR.RULE.PUSH_POLICY:
ta4j is public. Existing non-default upstream branches may be pushed without asking, but the first push of a new remote branch requires explicit approval. Any push to `master` or the default branch also requires explicit approval.

Disable PDL.TASK.PR_MERGE:
Terminal condition is a ready, non-draft PR with green local verification, acceptable CI, a clean final PR sweep, no unresolved conversations, and explicit human merge handoff. Do not merge into `master` automatically.

Override PDL.TASK.LINEAR_CLOSEOUT:
Unless a human merge happens in the same run, move the source issue/document to review or ready-for-merge state, not done. Mark done only after the PR is actually merged.

Extend PDL.TASK.HANDOFF_REPORT:
Report that ta4j is public and the PR is intentionally left open for public viewing and human merge.

Override QAS.TASK.MERGE_POLICY:
ta4j requires a human viewing opportunity before `master` changes. Automation prepares, verifies, monitors, fixes feedback, resolves conversations, and hands off the ready PR.

Require QAS.TASK.FINAL_PR_SWEEP:
Before handoff, perform one live final sweep for top-level comments, actionable reviews, and unresolved review threads.
