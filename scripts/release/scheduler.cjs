// GitHub operations use the authenticated Octokit client supplied by github-script.
// Return only state needed by policy; never persist API bodies or credentials.
const fs = require('node:fs');

const SHA = /^[0-9a-f]{40}$/;
const VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

async function repositoryState({ github, context, sourceSha }) {
  if (!SHA.test(sourceSha)) throw new Error('Invalid candidate SHA');
  const defaultBranch = context.payload.repository.default_branch;
  const [branch, runs, refs, pulls] = await Promise.all([
    github.rest.git.getRef({ ...context.repo, ref: `heads/${defaultBranch}` }),
    github.paginate(github.rest.actions.listWorkflowRuns, { ...context.repo, workflow_id: 'test.yml',
      branch: defaultBranch, event: 'push', head_sha: sourceSha, per_page: 100 }),
    github.paginate(github.rest.git.listMatchingRefs, { ...context.repo, ref: 'heads/release-' }),
    github.paginate(github.rest.pulls.list, { ...context.repo, state: 'open', base: defaultBranch, per_page: 100 }),
  ]);
  // Do not substitute a PR, another workflow, an older successful attempt or a
  // nearby commit when the canonical exact-source run is absent or still pending.
  const candidates = runs.filter(run => run.head_sha === sourceSha && run.event === 'push' &&
    run.head_branch === defaultBranch && run.path === '.github/workflows/test.yml');
  candidates.sort((a, b) => b.run_number - a.run_number || b.run_attempt - a.run_attempt);
  const latest = candidates[0];
  return {
    schemaVersion: 1, sourceSha, defaultBranch, defaultSha: branch.data.object.sha,
    ci: latest ? { runId: latest.id, status: latest.status, conclusion: latest.conclusion,
      sourceSha: latest.head_sha, runAttempt: latest.run_attempt } : null,
    refs: Object.fromEntries(refs.filter(ref => /^refs\/heads\/release-(freeze|pending|publishing|published)\//.test(ref.ref))
      .map(ref => [ref.ref, ref.object.sha])),
    // Labels can be missing or edited; a same-repository release branch still
    // owns a release in progress and must not be ignored for that reason.
    openReleasePullRequests: pulls.filter(pr => pr.head.repo?.full_name === context.payload.repository.full_name &&
      pr.head.ref.startsWith('release/')).map(pr => pr.number),
  };
}

async function collect({ github, context, sourceSha, directory }) {
  const state = await repositoryState({ github, context, sourceSha });
  fs.mkdirSync(directory, { recursive: true });
  fs.writeFileSync(`${directory}/repository-state.json`, JSON.stringify(state, null, 2) + '\n');
  return state;
}

async function releaseTagCommit({ github, context, tag }) {
  let object;
  try {
    object = (await github.rest.git.getRef({ ...context.repo, ref: `tags/${tag}` })).data.object;
  } catch (error) {
    if (error.status === 404) return null;
    throw error;
  }
  // Peel annotated tags as well as lightweight tags; a tag object's SHA is not
  // the released commit. Bound nesting so corrupt tag state fails closed.
  for (let depth = 0; depth < 8; depth++) {
    if (!object || !SHA.test(object.sha)) throw new Error('Invalid release tag state');
    if (object.type === 'commit') return object.sha;
    if (object.type !== 'tag') throw new Error('Release tag does not identify a commit');
    object = (await github.rest.git.getTag({ ...context.repo, tag_sha: object.sha })).data.object;
  }
  throw new Error('Release tag nesting exceeds the validation limit');
}

async function handoff({ github, context, sourceSha, version, dryRun }) {
  if (!SHA.test(sourceSha) || !VERSION.test(version) || !['true', 'false'].includes(dryRun)) {
    throw new Error('Invalid release handoff');
  }
  const state = await repositoryState({ github, context, sourceSha });
  if (state.defaultSha !== sourceSha) throw new Error('Assessed source is stale; run a new assessment');
  if (state.ci?.sourceSha !== sourceSha || state.ci.status !== 'completed' || state.ci.conclusion !== 'success') {
    throw new Error('The newest exact-source verification is not successful; reassess after verification');
  }
  const ref = state.defaultBranch;
  const title = `Prepare release ${version} at ${sourceSha} (dryRun=${dryRun})`;
  const runs = await github.paginate(github.rest.actions.listWorkflowRuns, { ...context.repo,
    workflow_id: 'prepare-release.yml', branch: ref, event: 'workflow_dispatch', head_sha: sourceSha, per_page: 100 });
  const matching = runs.filter(run => run.head_sha === sourceSha && run.head_branch === ref &&
    run.event === 'workflow_dispatch' && run.path === '.github/workflows/prepare-release.yml' && run.display_title === title);
  matching.sort((a, b) => b.run_number - a.run_number || b.run_attempt - a.run_attempt);
  if (matching.length) {
    const previous = matching[0];
    if (previous.status === 'completed' && previous.conclusion !== 'success') {
      throw new Error('Matching preparation already failed or stopped; inspect and recover that preparation run');
    }
    return { status: 'already_dispatched', runId: previous.id };
  }
  if (Object.keys(state.refs).some(name => name.startsWith('refs/heads/release-freeze/')) ||
      state.openReleasePullRequests.length) {
    throw new Error('Another release owns the preparation lifecycle; inspect its freeze or release pull request');
  }
  // Recheck publication journals too: they outlive runs and may remain after an
  // interrupted release even if someone removed its freeze ref.
  const completedTags = new Map();
  for (const [name, sha] of Object.entries(state.refs)) {
    if (!/^refs\/heads\/release-(pending|publishing)\//.test(name)) continue;
    const previousVersion = name.split('/').at(-1);
    const published = state.refs[`refs/heads/release-published/${previousVersion}`];
    if (!VERSION.test(previousVersion) || !SHA.test(published || '')) {
      throw new Error('Previous release publication is incomplete; recover it before another preparation');
    }
    if (!completedTags.has(previousVersion)) {
      completedTags.set(previousVersion, await releaseTagCommit({ github, context, tag: previousVersion }));
    }
    let release = sha;
    if (name.startsWith('refs/heads/release-pending/')) {
      const commit = await github.rest.git.getCommit({ ...context.repo, commit_sha: sha });
      release = commit.data.parents?.length === 1 ? commit.data.parents[0].sha : null;
    }
    if (release !== published || completedTags.get(previousVersion) !== published) {
      throw new Error('Previous release publication evidence disagrees; reconcile it before another preparation');
    }
  }
  for (const tag of [version, `v${version}`]) {
    if (await releaseTagCommit({ github, context, tag })) throw new Error('Planned release tag already exists');
  }
  // Recheck head after the read-only guards. Preparation verifies sourceSha
  // again before acquiring its durable freeze; no API check can remove that race.
  const { data: head } = await github.rest.git.getRef({ ...context.repo, ref: `heads/${ref}` });
  if (head.object.sha !== sourceSha) throw new Error('Assessed source changed during handoff; run a new assessment');
  await github.rest.actions.createWorkflowDispatch({ ...context.repo, workflow_id: 'prepare-release.yml', ref,
    inputs: { releaseVersion: version, dryRun, sourceSha } });
  return { status: 'dispatched' };
}

async function notify({ github, context, body, discussionNumber }) {
  const number = Number(discussionNumber || 1414);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error('Invalid release discussion number');
  const { repository } = await github.graphql(`query($owner:String!, $repo:String!, $number:Int!) {
    repository(owner:$owner,name:$repo) { discussion(number:$number) { id } }
  }`, { ...context.repo, number });
  if (!repository?.discussion?.id) throw new Error('Release discussion was not found');
  await github.graphql(`mutation($id:ID!, $body:String!) {
    addDiscussionComment(input:{discussionId:$id,body:$body}) { comment { id } }
  }`, { id: repository.discussion.id, body: '<!-- ta4j:post-type=release-scheduler;run=real -->\n' + body });
}

module.exports = { collect, handoff, notify };
