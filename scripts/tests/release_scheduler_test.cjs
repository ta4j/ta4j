'use strict';
// Exercise GitHub behavior with an in-memory Octokit fixture; never contact GitHub.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const scheduler = require('../release/scheduler.cjs');

const source = 'a'.repeat(40);
const later = 'b'.repeat(40);
const release = 'c'.repeat(40);
const snapshot = 'd'.repeat(40);
const annotated = 'e'.repeat(40);
const context = { repo: { owner: 'fixture', repo: 'ta4j' },
  payload: { repository: { full_name: 'fixture/ta4j', default_branch: 'master' } } };
const ci = { id: 100, head_sha: source, head_branch: 'master', event: 'push',
  path: '.github/workflows/test.yml', run_number: 10, run_attempt: 1, status: 'completed', conclusion: 'success' };
const preparation = { id: 200, head_sha: source, head_branch: 'master', event: 'workflow_dispatch',
  path: '.github/workflows/prepare-release.yml', run_number: 20, run_attempt: 1, status: 'queued', conclusion: null,
  display_title: `Prepare release 0.30.0 at ${source} (dryRun=false)` };

function fixture(options = {}) {
  const calls = [];
  const dispatched = [];
  const comments = [];
  let headsRead = 0;
  const github = { rest: { git: {}, actions: {}, pulls: {} } };
  github.rest.git.getRef = async ({ ref }) => {
    calls.push(['getRef', ref]);
    if (ref === 'heads/master') {
      const sha = (options.heads || [source])[headsRead++] || source;
      return { data: { object: { type: 'commit', sha } } };
    }
    if (options.tagError) throw Object.assign(new Error('fixture API failure'), { status: options.tagError });
    const object = options.tags?.[ref.slice('tags/'.length)];
    if (!object) throw Object.assign(new Error('fixture missing ref'), { status: 404 });
    return { data: { object } };
  };
  github.rest.git.getTag = async ({ tag_sha }) => ({ data: { object: options.annotatedTags?.[tag_sha] } });
  github.rest.git.getCommit = async ({ commit_sha }) => ({ data: { parents: options.parents?.[commit_sha] || [] } });
  github.rest.git.listMatchingRefs = () => {};
  github.rest.actions.listWorkflowRuns = () => {};
  github.rest.pulls.list = () => {};
  github.rest.actions.createWorkflowDispatch = async payload => {
    calls.push(['dispatch', payload]);
    dispatched.push(payload);
  };
  github.paginate = async (method, args) => {
    calls.push(['paginate', args]);
    if (method === github.rest.actions.listWorkflowRuns) {
      assert.equal(args.head_sha, source);
      assert.equal(args.branch, 'master');
      assert.equal(args.per_page, 100);
      if (args.workflow_id === 'test.yml') {
        assert.equal(args.event, 'push');
        if (options.ciError) throw new Error('fixture verification API failure');
        return options.runs || [ci];
      }
      assert.equal(args.workflow_id, 'prepare-release.yml');
      assert.equal(args.event, 'workflow_dispatch');
      return options.preparations || [];
    }
    if (method === github.rest.git.listMatchingRefs) {
      assert.equal(args.ref, 'heads/release-');
      return Object.entries(options.refs || {}).map(([ref, sha]) => ({ ref, object: { sha }, ignored: 'PRIVATE_FIXTURE' }));
    }
    assert.equal(method, github.rest.pulls.list);
    assert.equal(args.state, 'open');
    assert.equal(args.base, 'master');
    return options.pulls || [];
  };
  github.graphql = async (query, variables) => {
    calls.push(['graphql', variables]);
    if (query.startsWith('query')) return { repository: { discussion: options.noDiscussion ? null : { id: 'D_fixture' } } };
    comments.push(variables);
    return { addDiscussionComment: { comment: { id: 'C_fixture' } } };
  };
  return { github, calls, dispatched, comments };
}

async function collect(options = {}) {
  const mocked = fixture(options);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ta4j-scheduler-'));
  try {
    const state = await scheduler.collect({ github: mocked.github, context, sourceSha: source, directory });
    const saved = fs.readFileSync(path.join(directory, 'repository-state.json'), 'utf8');
    assert.deepEqual(JSON.parse(saved), state);
    assert.equal(saved.includes('PRIVATE_FIXTURE'), false);
    assert.deepEqual(fs.readdirSync(directory), ['repository-state.json']);
    return { ...mocked, state };
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

async function handoff(mocked, values = {}) {
  return scheduler.handoff({ github: mocked.github, context, sourceSha: source,
    version: '0.30.0', dryRun: 'false', ...values });
}

async function refuses(options, pattern, values) {
  const mocked = fixture(options);
  await assert.rejects(handoff(mocked, values), pattern);
  assert.equal(mocked.dispatched.length, 0);
}

test('collection persists only eligibility evidence and selects newest canonical attempt', async () => {
  const { state } = await collect({ runs: [ci, { ...ci, id: 101, run_attempt: 2, conclusion: 'failure', body: 'PRIVATE_FIXTURE' }],
    refs: { 'refs/heads/release-published/0.29.0': release, 'refs/heads/release-unrelated/foo': later },
    pulls: [{ number: 2, head: { repo: { full_name: 'fixture/ta4j' }, ref: 'release/0.30.0' }, labels: [], body: 'PRIVATE_FIXTURE' },
      { number: 3, head: { repo: { full_name: 'fork/ta4j' }, ref: 'release/0.30.0' }, labels: [{ name: 'release' }] }] });
  assert.deepEqual(state.ci, { runId: 101, status: 'completed', conclusion: 'failure', sourceSha: source, runAttempt: 2 });
  assert.deepEqual(state.openReleasePullRequests, [2]);
  assert.deepEqual(state.refs, { 'refs/heads/release-published/0.29.0': release });
  assert.deepEqual(Object.keys(state).sort(), ['schemaVersion', 'sourceSha', 'defaultBranch', 'defaultSha', 'ci', 'refs', 'openReleasePullRequests'].sort());
});

test('newer queued canonical run supersedes an older passing run', async () => {
  const { state } = await collect({ runs: [{ ...ci, id: 102, run_number: 11, status: 'queued', conclusion: null }, ci] });
  assert.equal(state.ci.runId, 102);
  assert.equal(state.ci.status, 'queued');
});

test('canonical CI accepts GitHub ref-qualified workflow paths with exact path boundaries', async () => {
  for (const ref of ['master', 'refs/heads/master', source]) {
    const { state } = await collect({ runs: [{ ...ci, path: `${ci.path}@${ref}` }] });
    assert.equal(state.ci.runId, ci.id);
    assert.equal(state.ci.conclusion, 'success');
  }
  for (const workflowPath of ['.github/workflows/other.yml@master', `${ci.path}.extra@master`, `${ci.path}@`, `${ci.path}@ `]) {
    const { state } = await collect({ runs: [{ ...ci, path: workflowPath }] });
    assert.equal(state.ci, null);
  }
});

test('different head, branch, workflow and PR CI cannot substitute for the candidate check', async () => {
  const runs = [{ ...ci, head_sha: later }, { ...ci, head_branch: 'feature/test' },
    { ...ci, path: '.github/workflows/other.yml' }, { ...ci, event: 'pull_request' }];
  const { state } = await collect({ runs });
  assert.equal(state.ci, null);
  await refuses({ runs }, /verification is not successful/);
});

test('missing, pending, failed, cancelled and API-failed CI prevent dispatch', async () => {
  for (const options of [{ runs: [] }, { ciError: true },
    ...['failure', 'cancelled', 'skipped', 'neutral', null].map(conclusion => ({ runs: [{ ...ci, conclusion }] })),
    { runs: [{ ...ci, status: 'in_progress', conclusion: null }] }]) {
    await refuses(options, /verification|API failure/);
  }
});

test('valid handoff dispatches exactly the checked source/version/dry-run value', async () => {
  for (const dryRun of ['true', 'false']) {
    const mocked = fixture();
    assert.deepEqual(await handoff(mocked, { dryRun }), { status: 'dispatched' });
    assert.deepEqual(mocked.dispatched, [{ owner: 'fixture', repo: 'ta4j', workflow_id: 'prepare-release.yml', ref: 'master',
      inputs: { releaseVersion: '0.30.0', dryRun, sourceSha: source } }]);
    assert.equal(mocked.calls.filter(([kind, ref]) => kind === 'getRef' && ref === 'heads/master').length, 2);
  }
});

test('malformed handoff fails before any GitHub request', async () => {
  for (const values of [{ sourceSha: 'master' }, { sourceSha: source.toUpperCase() }, { version: '01.2.3' },
    { version: '1.2' }, { version: '1.2.3; echo unsafe' }, { dryRun: '' }, { dryRun: 'yes' }, { dryRun: true }]) {
    const mocked = fixture();
    await assert.rejects(handoff(mocked, values), /Invalid release handoff/);
    assert.deepEqual(mocked.calls, []);
    assert.deepEqual(mocked.dispatched, []);
  }
});

test('source drift before or during handoff prevents dispatch', async () => {
  await refuses({ heads: [later] }, /stale/);
  await refuses({ heads: [source, later] }, /changed during handoff/);
});

test('active freeze or unlabeled same-repository release PR prevents dispatch', async () => {
  await refuses({ refs: { 'refs/heads/release-freeze/prepare-123': source } }, /Another release/);
  await refuses({ pulls: [{ number: 3, head: { repo: { full_name: 'fixture/ta4j' }, ref: 'release/0.30.0' }, labels: [] }] }, /Another release/);
});

test('accepted matching preparation is reused instead of dispatched again', async () => {
  for (const status of ['queued', 'in_progress', 'waiting', 'completed']) {
    const mocked = fixture({ preparations: [{ ...preparation, status, conclusion: status === 'completed' ? 'success' : null }],
      refs: { 'refs/heads/release-freeze/prepare-200': source } });
    assert.deepEqual(await handoff(mocked), { status: 'already_dispatched', runId: 200 });
    assert.equal(mocked.dispatched.length, 0);
  }
});

test('preparation deduplication accepts a ref-qualified path without matching another workflow', async () => {
  for (const ref of ['master', 'refs/heads/master', source]) {
    const mocked = fixture({ preparations: [{ ...preparation, path: `${preparation.path}@${ref}` }] });
    assert.deepEqual(await handoff(mocked), { status: 'already_dispatched', runId: preparation.id });
    assert.equal(mocked.dispatched.length, 0);
  }
  for (const workflowPath of ['.github/workflows/other.yml@master', `${preparation.path}.extra@master`, `${preparation.path}@`]) {
    const mocked = fixture({ preparations: [{ ...preparation, path: workflowPath }] });
    assert.equal((await handoff(mocked)).status, 'dispatched');
    assert.equal(mocked.dispatched.length, 1);
  }
});

test('failed or cancelled matching preparation requires recovery of that run', async () => {
  for (const conclusion of ['failure', 'cancelled', 'skipped', null]) {
    await refuses({ preparations: [{ ...preparation, status: 'completed', conclusion }] }, /recover that preparation run/);
  }
});

test('a different preparation identity is not treated as a duplicate', async () => {
  const mocked = fixture({ preparations: [{ ...preparation, head_sha: later },
    { ...preparation, path: '.github/workflows/other.yml' }, { ...preparation, event: 'push' },
    { ...preparation, display_title: preparation.display_title.replace('dryRun=false', 'dryRun=true') }] });
  assert.equal((await handoff(mocked)).status, 'dispatched');
  assert.equal(mocked.dispatched.length, 1);
  const yaml = fs.readFileSync(path.join(__dirname, '../../.github/workflows/prepare-release.yml'), 'utf8');
  assert.ok(yaml.includes("run-name: Prepare release ${{ inputs.releaseVersion || 'auto' }} at ${{ inputs.sourceSha || github.sha }} (dryRun=${{ inputs.dryRun }})"));
});

test('incomplete publication journal still blocks when freeze was removed', async () => {
  for (const kind of ['pending', 'publishing']) {
    await refuses({ refs: { [`refs/heads/release-${kind}/0.29.0`]: snapshot } }, /publication is incomplete/);
  }
});

test('completed publication requires the tag, published R and parent of pending S to agree', async () => {
  const options = { refs: { 'refs/heads/release-pending/0.29.0': snapshot,
    'refs/heads/release-publishing/0.29.0': release, 'refs/heads/release-published/0.29.0': release },
    tags: { '0.29.0': { type: 'tag', sha: annotated } }, annotatedTags: { [annotated]: { type: 'commit', sha: release } },
    parents: { [snapshot]: [{ sha: release }] } };
  const mocked = fixture(options);
  assert.equal((await handoff(mocked)).status, 'dispatched');
  await refuses({ ...options, parents: { [snapshot]: [{ sha: later }] } }, /evidence disagrees/);
  await refuses({ ...options, tags: {} }, /evidence disagrees/);
  await refuses({ ...options, refs: { ...options.refs, 'refs/heads/release-publishing/0.29.0': later } }, /evidence disagrees/);
});

test('existing plain, v-prefixed or annotated release tag prevents preparation', async () => {
  for (const tag of ['0.30.0', 'v0.30.0']) {
    await refuses({ tags: { [tag]: { type: 'commit', sha: release } } }, /tag already exists/);
    await refuses({ tags: { [tag]: { type: 'tag', sha: annotated } },
      annotatedTags: { [annotated]: { type: 'commit', sha: release } } }, /tag already exists/);
  }
  await refuses({ tagError: 403 }, /API failure/);
});

test('discussion update retains the cleanup marker and sends only the selected report', async () => {
  const mocked = fixture();
  await scheduler.notify({ github: mocked.github, context, body: 'Concise outcome', discussionNumber: '1414' });
  assert.deepEqual(mocked.comments, [{ id: 'D_fixture', body: '<!-- ta4j:post-type=release-scheduler;run=real -->\nConcise outcome' }]);
  for (const discussionNumber of ['0', '-1', 'not-a-number']) {
    const invalid = fixture();
    await assert.rejects(scheduler.notify({ github: invalid.github, context, body: 'report', discussionNumber }), /Invalid release discussion/);
    assert.equal(invalid.calls.length, 0);
  }
  const missing = fixture({ noDiscussion: true });
  await assert.rejects(scheduler.notify({ github: missing.github, context, body: 'report' }), /not found/);
  assert.equal(missing.comments.length, 0);
});
