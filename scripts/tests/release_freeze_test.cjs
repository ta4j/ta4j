const assert = require('node:assert/strict');
const { test } = require('node:test');
const { synchronize, assertMergeAllowed } = require('../release/release_freeze.cjs');

function fixture() {
  const state = { locks: [], prs: [], statuses: new Map(), writes: [], comments: [], runs: [], jobs: [], retries: [], mutation: null };
  const endpoints = Object.fromEntries(['refs', 'prs', 'statuses', 'comments', 'runs', 'jobs'].map(name => [name, Object.assign(() => {}, { endpoint: name })]));
  const github = {
    rest: {
      git: { listMatchingRefs: endpoints.refs },
      pulls: { list: endpoints.prs, get: async ({ pull_number }) => {
        const pr = state.prs.find(item => item.number === pull_number);
        return { data: structuredClone(pr) };
      } },
      repos: { listCommitStatusesForRef: endpoints.statuses, createCommitStatus: async args => {
        state.writes.push(args);
        state.statuses.set(args.sha, [{ ...args, creator: { login: 'github-actions[bot]' } }, ...(state.statuses.get(args.sha) || [])]);
        if (state.mutation) { const mutate = state.mutation; state.mutation = null; mutate(); }
      } },
      issues: { listComments: endpoints.comments, createComment: async args => state.comments.push(args),
        updateComment: async () => {}, deleteComment: async () => {} },
      actions: { listWorkflowRuns: endpoints.runs, listJobsForWorkflowRun: endpoints.jobs,
        reRunJobForWorkflowRun: async args => { state.retries.push(args.job_id); state.runs[0].run_attempt++; } }
    },
    paginate: async (endpoint, args) => {
      const name = endpoint.endpoint;
      if (name === 'refs') return state.locks.map(ref => ({ ref }));
      if (name === 'prs') return structuredClone(state.prs);
      if (name === 'statuses') return state.statuses.get(args.ref) || [];
      if (name === 'runs') return state.runs;
      if (name === 'jobs') return state.jobs;
      return [];
    }
  };
  const context = { repo: { owner: 'fixture', repo: 'ta4j' }, runId: 123, eventName: 'workflow_run', payload: { repository: { default_branch: 'master' } } };
  const core = { info: () => {}, warning: () => {} };
  const pr = (number, extra = {}) => ({ number, state: 'open', base: { ref: 'master', sha: 'base' }, head: { ref: 'feature/x', sha: `head${number}` },
    merge_commit_sha: `merge${number}`, labels: [], user: { login: 'contributor' }, ...extra });
  const sync = () => synchronize({ github, context, core });
  return { state, github, context, core, pr, sync };
}

test('automatic freeze blocks already-green ordinary AND trusted release PRs on head and merge SHAs', async () => {
  const f = fixture();
  f.state.locks.push('refs/heads/release-freeze/1.2.3');
  f.state.prs.push(f.pr(1), f.pr(2, { head: { ref: 'release/1.2.3', sha: 'head2' }, user: { login: 'TheCookieLab' }, labels: [{ name: 'release' }] }));
  await f.sync();
  assert.deepEqual(new Set(f.state.writes.map(x => x.sha)), new Set(['head1', 'merge1', 'head2', 'merge2']));
  assert(f.state.writes.every(x => x.state === 'failure' && x.context === 'Build and Verify'));
});

test('manual PR freeze keeps its existing trusted-release exemption and ignores forged labels', async () => {
  const f = fixture();
  f.state.prs.push(f.pr(1, { head: { ref: 'release/1.2.3', sha: 'head1' }, user: { login: 'TheCookieLab' }, labels: [{ name: 'release' }] }),
    f.pr(2, { labels: [{ name: 'release' }] }));
  await f.sync();
  assert.deepEqual(new Set(f.state.writes.map(x => x.sha)), new Set(['head2', 'merge2']));
});

test('new heads and test-merge SHAs are rediscovered before acquisition barrier returns', async () => {
  const f = fixture(); f.state.locks.push('refs/heads/release-freeze/1.2.3'); f.state.prs.push(f.pr(1));
  f.state.mutation = () => { f.state.prs[0].head.sha = 'newhead'; f.state.prs[0].merge_commit_sha = 'newmerge'; };
  await f.sync();
  assert.equal(f.state.statuses.get('newhead')[0].state, 'failure');
  assert.equal(f.state.statuses.get('newmerge')[0].state, 'failure');
});

test('releasing one freeze never opens the barrier while another remains', async () => {
  const f = fixture(); f.state.prs.push(f.pr(1)); f.state.locks.push('refs/heads/release-freeze/1.2.3', 'refs/heads/release-freeze/1.2.4');
  await f.sync(); f.state.locks.shift(); await f.sync();
  assert(f.state.writes.every(x => x.state === 'failure'));
  f.state.locks = []; await f.sync();
  assert.equal(f.state.statuses.get('head1')[0].state, 'success');
  // Independent model of GitHub's same-name conjunction: status success cannot hide CI failure.
  const canMerge = check => check === 'success' && f.state.statuses.get('head1')[0].state === 'success';
  assert.equal(canMerge('failure'), false); assert.equal(canMerge('success'), true);
});

test('unchanged state does not flood commit statuses and inactive state never invents CI success', async () => {
  const f = fixture(); f.state.prs.push(f.pr(1)); await f.sync(); assert.equal(f.state.writes.length, 0);
  f.state.locks.push('refs/heads/release-freeze/prepare-123'); await f.sync(); await f.sync();
  assert.equal(f.state.writes.length, 2);
});

test('critical API errors fail closed instead of best-effort releasing the barrier', async () => {
  const f = fixture(); f.state.locks.push('refs/heads/release-freeze/1.2.3'); f.state.prs.push(f.pr(1));
  f.github.rest.repos.createCommitStatus = async () => { throw new Error('403 statuses denied'); };
  await assert.rejects(f.sync(), /403/);
  f.github.paginate = async () => { throw new Error('discovery incomplete'); };
  await assert.rejects(f.sync(), /discovery incomplete/);
});

test('required aggregate rejects freeze, moved head, moved base and moved test merge', async () => {
  const f = fixture(); f.context.eventName = 'pull_request'; f.state.prs.push(f.pr(1)); f.context.payload.pull_request = f.pr(1);
  Object.assign(process.env, { TESTED_HEAD: 'head1', TESTED_BASE: 'base', TESTED_MERGE: 'merge1' });
  await assertMergeAllowed(f);
  for (const field of ['TESTED_HEAD', 'TESTED_BASE', 'TESTED_MERGE']) {
    const prior = process.env[field]; process.env[field] = 'stale';
    await assert.rejects(assertMergeAllowed(f), /release-freeze/); process.env[field] = prior;
  }
  f.state.locks.push('refs/heads/release-freeze/prepare-123');
  await assert.rejects(assertMergeAllowed(f), /release-freeze/);
});

test('at most one exact-run aggregate retry preserves successful script/Maven jobs', async () => {
  const f = fixture(); f.state.prs.push(f.pr(1)); f.state.locks.push('refs/heads/release-freeze/1.2.3'); await f.sync();
  f.state.locks = [];
  f.state.runs = [{ id: 7, run_attempt: 1, head_sha: 'head1', status: 'completed', pull_requests: [{ number: 1, head: { sha: 'head1' }, base: { sha: 'base' } }] }];
  f.state.jobs = [{ id: 9, name: 'Build and Verify', conclusion: 'failure' }, { id: 10, name: 'Maven verification', conclusion: 'skipped' }, { id: 11, name: 'Script and action verification', conclusion: 'success' }];
  await f.sync(); await f.sync(); assert.deepEqual(f.state.retries, [9]);
  assert.equal(f.state.jobs[2].conclusion, 'success');
});

test('aggregate retry never reruns a failed test lane or an obsolete base', async () => {
  const f = fixture(); f.state.prs.push(f.pr(1)); f.state.locks.push('refs/heads/release-freeze/1.2.3'); await f.sync(); f.state.locks = [];
  f.state.runs = [{ id: 7, run_attempt: 1, head_sha: 'head1', status: 'completed', pull_requests: [{ number: 1, head: { sha: 'head1' }, base: { sha: 'oldbase' } }] }];
  f.state.jobs = [{ id: 9, name: 'Build and Verify', conclusion: 'failure' }, { name: 'Maven verification', conclusion: 'success' }];
  await f.sync(); assert.deepEqual(f.state.retries, []);
  f.state.runs[0].pull_requests[0].base.sha = 'base'; f.state.jobs[1].conclusion = 'failure';
  await f.sync(); assert.deepEqual(f.state.retries, []);
});

test('large PR sets and fork PRs are reconciled; no executable fork artifacts are loaded', async () => {
  const f = fixture(); f.state.locks.push('refs/heads/release-freeze/1.2.3');
  f.state.prs = Array.from({ length: 125 }, (_, i) => f.pr(i + 1, { head: { ref: 'fork/branch', sha: `forkhead${i}`, repo: { fork: true } } }));
  await f.sync(); assert.equal(f.state.writes.length, 250);
});
