// Trusted default-branch controller. Never loads PR code or artifacts.
const STATUS = 'Build and Verify';
const PREFIX = 'Release freeze:';
const REF_PREFIX = 'refs/heads/release-freeze/';
const NOTICE = '<!-- ta4j:release-freeze-notice -->';

/** Retain the manual PR exemption only for configured authors, labels and release branches. */
function trustedRelease(pr, authors) {
  return pr.head?.ref?.startsWith('release/') && authors.has(pr.user?.login?.toLowerCase()) &&
    pr.labels?.some(label => label.name === 'release');
}

/** Discover all freezes and refresh every open PR head and test-merge SHA. */
async function snapshot(github, context) {
  const { owner, repo } = context.repo;
  const base = context.payload.repository.default_branch;
  const authors = new Set((process.env.RELEASE_FREEZE_AUTHORS || 'TheCookieLab').split(',').map(x => x.trim().toLowerCase()));
  const refs = await github.paginate(github.rest.git.listMatchingRefs, { owner, repo, ref: 'heads/release-freeze/' });
  const locks = refs.filter(ref => ref.ref.startsWith(REF_PREFIX)).map(ref => ref.ref).sort();
  const listed = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', base, per_page: 100 });
  // Refresh every head and test-merge SHA; list responses can be stale.
  const prs = [];
  for (const candidate of listed) {
    const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: candidate.number });
    if (pr.state === 'open' && pr.base.ref === base) prs.push(pr);
  }
  const releases = prs.filter(pr => trustedRelease(pr, authors));
  return { base, locks, prs, releases, authors };
}

/** Automatic freezes block every PR; a manual release PR retains its existing exemption. */
function blocked(state, pr) {
  return state.locks.length > 0 || (state.releases.length > 0 && !trustedRelease(pr, state.authors));
}

/** Both SHA planes can be authoritative for GitHub required checks. */
function shas(pr) {
  return [...new Set([pr.head.sha, pr.merge_commit_sha].filter(Boolean))];
}

/** Compare discovery snapshots independently of API ordering. */
function key(state) {
  return JSON.stringify([state.locks, state.prs.map(pr => [pr.number, pr.head.sha, pr.merge_commit_sha, blocked(state, pr)]).sort()]);
}

/** Read the latest controller-owned overlay without interpreting unrelated CI statuses. */
async function previousStatus(github, context, sha) {
  const statuses = await github.paginate(github.rest.repos.listCommitStatusesForRef,
    { ...context.repo, ref: sha, per_page: 100 });
  return statuses.find(status => status.context === STATUS && status.description?.startsWith(PREFIX) &&
    status.creator?.login === 'github-actions[bot]');
}

/** Update only the freeze commit-status plane; never replace the actual CI CheckRun. */
async function setStatus(github, context, sha, frozen) {
  const previous = await previousStatus(github, context, sha);
  const desired = frozen ? 'failure' : 'success';
  if ((!frozen && !previous) || previous?.state === desired) return;
  await github.rest.repos.createCommitStatus({ ...context.repo, sha, context: STATUS, state: desired,
    description: `${PREFIX} ${frozen ? 'active; merging is paused' : 'clear; actual CI must also pass'}`,
    target_url: `https://github.com/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}` });
}

/** Keep existing freeze notices useful; notice failures never weaken status enforcement. */
async function notices(github, context, state, core) {
  for (const pr of state.prs) {
    try {
      const comments = await github.paginate(github.rest.issues.listComments,
        { ...context.repo, issue_number: pr.number, per_page: 100 });
      const existing = comments.find(comment => comment.user?.type === 'Bot' && comment.body?.includes(NOTICE));
      const frozen = blocked(state, pr);
      const body = `${NOTICE}\nRelease freeze is active on ${state.base}. Merging is paused until the release is reconciled.\n` +
        [...state.locks.map(ref => `- ${ref}`), ...state.releases.map(item => `- Release PR #${item.number}`)].join('\n');
      if (!frozen && existing) await github.rest.issues.deleteComment({ ...context.repo, comment_id: existing.id });
      else if (frozen && existing && existing.body !== body) await github.rest.issues.updateComment({ ...context.repo, comment_id: existing.id, body });
      else if (frozen && !existing) await github.rest.issues.createComment({ ...context.repo, issue_number: pr.number, body });
    } catch (error) {
      core.warning(`Release notice for #${pr.number}: ${error.message}`);
    }
  }
}

/** Recover a freeze-failed final gate once while preserving successful test lanes. */
async function retryAggregate(github, context, state, core) {
  // At most one aggregate-only retry per exact run. Never rerun Maven/script jobs.
  for (const pr of state.prs) {
    if (blocked(state, pr)) continue;
    const previous = await previousStatus(github, context, pr.head.sha);
    if (previous?.state !== 'success') continue;
    const runs = await github.paginate(github.rest.actions.listWorkflowRuns,
      { ...context.repo, workflow_id: 'test.yml', event: 'pull_request', head_sha: pr.head.sha, per_page: 100 });
    const run = runs[0];
    if (!run || run.status !== 'completed' || run.run_attempt !== 1 || run.head_sha !== pr.head.sha) continue;
    if (!run.pull_requests?.some(item => item.number === pr.number && item.head.sha === pr.head.sha && item.base.sha === pr.base.sha)) continue;
    const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun,
      { ...context.repo, run_id: run.id, filter: 'latest', per_page: 100 });
    const aggregate = jobs.find(job => job.name === STATUS);
    if (!aggregate || aggregate.conclusion !== 'failure' || !jobs.every(job => job.name === STATUS ||
        ['success', 'skipped', 'neutral'].includes(job.conclusion))) continue;
    try {
      // The rerun aggregate also compares original scope outputs with live head/base/merge.
      await github.rest.actions.reRunJobForWorkflowRun({ ...context.repo, job_id: aggregate.id });
      core.info(`Requested one aggregate-only retry for #${pr.number}; successful test jobs are retained.`);
    } catch (error) {
      core.warning(`Aggregate retry for #${pr.number}: ${error.message}; rerun that gate manually.`);
    }
  }
}

/** Reconcile stable targets under the callers shared short concurrency group. */
async function synchronize({ github, context, core, withNotices = false }) {
  // All callers use the same short concurrency group, including acquire/release jobs.
  for (let attempt = 0; attempt < 3; attempt++) {
    const before = await snapshot(github, context);
    for (const pr of before.prs) {
      for (const sha of shas(pr)) await setStatus(github, context, sha, blocked(before, pr));
    }
    const after = await snapshot(github, context);
    if (key(before) !== key(after)) continue;
    if (withNotices) await notices(github, context, after, core);
    await retryAggregate(github, context, after, core);
    core.info(`audit:release_freeze locks=${after.locks.length} releases=${after.releases.length} prs=${after.prs.length}`);
    return after;
  }
  throw new Error('Release freeze targets kept changing; refusing an incomplete merge barrier');
}

/** Reject a frozen or changed PR before its required aggregate can pass. */
async function assertMergeAllowed({ github, context }) {
  if (context.eventName !== 'pull_request') return;
  const state = await snapshot(github, context);
  const pr = state.prs.find(item => item.number === context.payload.pull_request.number);
  if (!pr || pr.head.sha !== process.env.TESTED_HEAD || pr.base.sha !== process.env.TESTED_BASE ||
      pr.merge_commit_sha !== process.env.TESTED_MERGE || blocked(state, pr)) {
    throw new Error('release-freeze: merging is paused or this PR head changed; retry the aggregate after reconciliation');
  }
}

module.exports = { synchronize, assertMergeAllowed, trustedRelease };
