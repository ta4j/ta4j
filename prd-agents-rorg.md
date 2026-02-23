What the paper claims, in one screen

The paper evaluates whether repository-level “context files” (AGENTS.md / CLAUDE.md) help coding agents solve real repo tasks. They test two complementary settings:
	•	SWE-bench Lite tasks from popular Python repos (which generally don’t have developer context files), using LLM-generated context files.
	•	A new benchmark they build, AGENTBENCH, from repos that do have developer-committed context files.  ￼

Core results:
	•	LLM-generated context files: slightly reduce success rates and increase cost by ~20%+ (more steps, more tokens).  ￼
	•	Developer-written context files: slightly improve success on average (paper summarizes ~+4% vs none) but still increase steps/cost.  ￼
	•	Behaviorally, context files cause agents to do more testing and broader exploration, and agents do follow the instructions (e.g., if you mention a tool, usage jumps).  ￼
	•	They conclude the main harm comes from unnecessary requirements: more instructions → more work → harder to finish within constraints.  ￼

⸻

Validity focus: how “agent instructions” were created and what they looked like

1) What exactly counts as a “context file” here?

They treat “context files” as repository files like AGENTS.md or CLAUDE.md meant to guide coding agents, similar to the emerging open format concept (“README for agents”).  ￼

2) How did they create the LLM-generated instructions?

This is one of the best-documented parts, because they put the actual init prompts in their harness code.

They generate LLM context files using prompts that mimic each agent’s recommended /init behavior. Concretely, their InitPlanner uses different “prompt_type” values (codex_agentsmd, claude_agentsmd, qwen_agentsmd, gemini_agentsmd).  ￼

Here’s what those prompts demand (this matters for validity):

Codex-style AGENTS.md prompt (repo contributor guide):
	•	Create AGENTS.md titled “Repository Guidelines”
	•	Concise (explicitly says “200–400 words is optimal”)
	•	Recommended sections include:
	•	project structure/module organization
	•	build/test/dev commands
	•	coding style and naming
	•	testing guidelines
	•	commit & PR guidelines (even suggests summarizing from git history)
	•	optional architecture overview, agent-specific instructions  ￼

Claude Code-style CLAUDE.md prompt:
	•	Create CLAUDE.md with:
	1.	common build/lint/test commands (including how to run a single test)
	2.	high-level architecture that “requires reading multiple files”
	•	Explicitly warns:
	•	don’t list easily discoverable file structure
	•	don’t include generic dev practices
	•	don’t make up sections unless supported by files read
	•	incorporate important parts from README / Cursor rules / Copilot instructions if present
	•	add a required header prefix  ￼

Qwen Code-style AGENTS.md prompt:
	•	Forces an exploration recipe: list files, read README, then iteratively read up to ~10 files
	•	Then produce a “comprehensive” AGENTS.md with overview, commands, conventions, etc.  ￼

Key validity implication: these prompts are not “random AGENTS.md writing.” They are very close to what agent vendors encourage: general repo guide + commands + conventions + sometimes an overview. That makes the LLM-generated condition a fair test of “follow the agent developer recommendations.”  ￼

3) How did they handle developer-written (human) instructions?

For AGENTBENCH, they select repos that already have context files at repo root (AGENTS.md or CLAUDE.md), then use the context file from the pre-patch repo state for each task instance.  ￼

They also report basic stats: across 138 AGENTBENCH instances, context files average ~641 words and ~9.7 sections (where “section” = between Markdown headers).  ￼

4) What kinds of instructions were inside, and how were they scoped?

From their analysis:
	•	Many context files contain repository overviews. Across 12 dev-provided context files in AGENTBENCH, 8 include a dedicated codebase overview and 4 enumerate directories/subdirectories.  ￼
	•	LLM-generated files frequently contain overviews too, even when prompts caution against it (Claude prompt warns; still, their classifier flags most generated files as having overviews).  ￼
	•	The “scope” is mostly repo-wide: a single root-level file intended to apply to all tasks. That is the exact pattern the paper evaluates.  ￼

They did not (in this paper) evaluate deeply scoped per-subdirectory context files as a primary condition—so if your workflow relies heavily on layered guidance, that’s a boundary on how directly you can generalize. (More on this with ta4j below.)

⸻

Validity check: are their methods reasonable?

What they did well
	•	Two complementary settings: popular repos with generated context + niche repos with real committed context. That directly addresses the “benchmarks don’t have AGENTS.md” problem.  ￼
	•	Behavioral instrumentation: they don’t just report success; they show changes in tool use (more tests, grep/read/write) and reasoning tokens (models spend more “thinking” when context files exist).  ￼
	•	Instruction-following sanity check: they show that when a tool is mentioned in context, agents use it much more—so “it didn’t help” isn’t explained by “agents ignored it.”  ￼
	•	Redundancy ablation: they remove markdown/docs after generating the context file, and then context files help (LLM-generated context files improve ~2.7% on average in that setting). That’s a strong clue that a lot of AGENTS.md benefit is simply “better docs / condensed docs,” not magic alignment.  ￼

Main threats to validity (the ones that matter for “should I do this for ta4j?”)
	1.	Language/toolchain bias (Python-heavy):
They explicitly note the evaluation is heavily Python, and that models may already “know” common Python tooling patterns, which could mute any benefit from context. They recommend future work on less represented languages/toolchains.  ￼
Why you should care: ta4j is Java/Maven—still common, but the exact project conventions can be much more repo-specific than “run pytest,” so context could matter more if it’s concise and accurate.
	2.	Metric mismatch (success rate = tests pass):
Their main metric is “patch makes tests pass.”  ￼
But many AGENTS.md rules aim at things like:

	•	style/format consistency
	•	changelog discipline
	•	PR hygiene
	•	architectural constraints
Those can be valuable but not measured. So “context files don’t improve success” does not mean “context files are useless overall.” It means they don’t reliably improve this success criterion, and they add cost.

	3.	Repo-wide monolithic scope vs layered scope:
The paper is fundamentally about root-level repo context (AGENTS.md/CLAUDE.md) and shows that broad requirements encourage exploration/testing and drive up cost.  ￼
If your repo uses layered AGENTS.md (root minimal + local deep guides), that could plausibly avoid the “unnecessary requirements” trap by narrowing relevance. The paper doesn’t directly test that.
	4.	The init prompts themselves encourage broad content:
Look at the Codex prompt: it recommends including project structure, style, testing, commit/PR rules.  ￼
That’s basically a recipe for writing a “general contributor guide,” not a “minimal agent checklist.” If the agent then dutifully follows that guide (run lots of tests, explore lots of files), you get exactly what the paper observes: higher cost, sometimes lower completion.  ￼
This is less a flaw and more “the point”: they’re testing what vendors recommend.

Net: their methods are reasonable for the question “Do current recommended AGENTS.md practices improve autonomous issue resolution success?” The main generalization caveat for you is Java/Maven + layered scoping.

⸻

Are their context files representative of real AGENTS.md? Compare to ta4j

What ta4j’s AGENTS.md looks like (as a measuring stick)

ta4j’s root AGENTS.md is not a fluffy overview—it’s a strict workflow spec. Examples:
	•	Mandatory: run scripts/run-full-build-quiet.sh before completion for most code changes, and report test totals/log path.  ￼
	•	Very explicit tooling and environment guidance (Maven commands the script runs, Windows shell guidance, permission notes).  ￼
	•	It also explicitly encourages scoped guides: “Many packages… have their own AGENTS.md… run rg --files -g 'AGENTS.md'… open the closest file.”  ￼

This is absolutely representative of a major real-world pattern: AGENTS.md used as a “do-not-break-CI + conventions” contract.

How that compares to what the paper evaluated

Match (representative):
	•	The paper’s LLM prompts encourage exactly the same categories ta4j includes: build/test commands, conventions, structure/architecture.  ￼
	•	The paper’s core behavioral finding (“tools mentioned get used; more tests/exploration happen”) is directly relevant to ta4j because your AGENTS.md mentions very specific commands and mandates them.  ￼

Mismatch (where ta4j goes beyond what they tested):
	•	ta4j uses layered / scoped AGENTS.md (root + package-specific). The paper largely treats context as “a single context file setting” per run; it does not isolate whether “scoping” is a fix for the unnecessary-requirements problem.  ￼

So: the paper is representative of ta4j’s root-level AGENTS.md pattern, but it does not fully cover ta4j’s more advanced “scoped guides near the code” strategy—which is one of the more promising ways to keep instructions relevant.

⸻

Actionable learnings for your repos like ta4j

Here’s what I would actually change/do, based on the paper’s evidence and what ta4j’s AGENTS.md currently tells agents to do.

1) Minimize “always-on” requirements; push the rest behind conditions

The paper’s punchline is basically: extra requirements increase work and can reduce completion.  ￼
In ta4j terms:
	•	Keep “musts” that prevent wasted time (e.g., the one correct build/test entrypoint).
	•	Convert the rest into conditional instructions:
	•	“If you touched X area, do Y”
	•	“If you’re done and ready to finalize, run Z once”

Why: they show context files increase testing/exploration and reasoning tokens. If your AGENTS.md makes the agent run the full build repeatedly (because it’s emphasized as “before completing ANY code changes”), you’re very likely to pay the ~20%+ overhead they measured—or more.  ￼

Concrete rewrite pattern for ta4j:
	•	Instead of: “Before completing ANY code changes, you MUST run full build…”
	•	Use: “Do fast loop while iterating; run full build exactly once when you have a candidate final patch.”

Your file already hints at “during development use narrow Maven tests.” Make it explicitly cost-aware so the agent doesn’t interpret it as “run full build after each edit.”  ￼

2) Treat tool mentions as levers, not documentation

Paper finding: if a tool is mentioned in the context file, usage jumps dramatically; they use uv as an example.  ￼
Implication: every command you name in AGENTS.md is a behavioral nudge.

For ta4j:
	•	Mention one canonical “final verification” command (you already do).
	•	Mention one canonical “fast feedback” pattern.
	•	Avoid listing a buffet of equivalent commands, or the agent may run them all.

3) Avoid “repo overview” sections unless the repo lacks docs

They find codebase overviews in context files generally don’t help agents reach the right files sooner.  ￼
They also find LLM-generated context only starts helping when docs are removed—suggesting it’s mostly acting as a documentation substitute.  ￼

So for a mature repo like ta4j:
	•	Don’t spend AGENTS.md tokens listing directory trees or explaining obvious structure.
	•	Prefer: “If working on serialization, read .../serialization/AGENTS.md first” (which you already have).  ￼

This is one place ta4j’s approach (scoped AGENTS.md) is likely better than what the paper’s negative results capture.

4) Split “agent execution contract” vs “human contributor process”

ta4j’s root file includes non-trivial process guidance (worktrees, PRD/TODO docs, changelog discipline).  ￼
Those are fine for humans, but for autonomous agents they can be pure overhead that doesn’t improve “tests pass.”

A practical pattern:
	•	Keep AGENTS.md focused on “how to succeed on coding tasks in this repo.”
	•	Move process-heavy guidance to CONTRIBUTING.md / docs, and in AGENTS.md just link: “For human workflow conventions, see CONTRIBUTING.md.”

That aligns with the paper’s conclusion: “human-written context files should describe only minimal requirements.”  ￼

5) Use layered scoping aggressively (ta4j is already doing the right thing)

Your root file explicitly tells agents to find local AGENTS.md files and points to an example package.  ￼
Double down on that:
	•	Root AGENTS.md should be short and mostly pointers:
	•	canonical build/test workflow
	•	how to locate scoped guides
	•	a short “definition of done”
	•	Put domain gotchas (like serialization normalization rules) in the closest subtree guide.  ￼

This is the best available mitigation for the “unnecessary requirements” mechanism the paper identifies, because you reduce irrelevant instructions for most tasks.

6) Add a “don’t reread this file” hint to reduce wasted steps

They observed at least one model wasting steps by searching for and rereading context files repeatedly even though they were already provided in context.  ￼
It’s not guaranteed to fix it, but it’s low-cost to add one line like:

“If your agent framework already injected AGENTS.md/CLAUDE.md into context, don’t spend tool calls opening them again.”

7) If you want evidence for your repo, run the same ablation the paper ran

You can copy their logic without building a benchmark suite:
	1.	Pick 10 representative ta4j issues/PR-sized tasks.
	2.	Run your agent with:
	•	no AGENTS.md (or a minimal stub)
	•	current AGENTS.md
	•	a “minimal requirements” rewrite
	3.	Measure:
	•	time-to-green
	•	number of tool calls
	•	whether it gets stuck running expensive builds
	•	total token cost (if available)

That’s exactly the kind of “does this instruction actually help” loop their results suggest you should do, because generic best practices often add cost without improving success.

## Evaluation of ta4j's AGENTS

What your current root AGENTS.md is doing well

It gives one canonical “definition of done”

You clearly establish a “final gate”:
	•	Run scripts/run-full-build-quiet.sh before completing almost any change (with narrow exceptions)
	•	Require a green build and require reporting test totals/log path  ￼

This is exactly the kind of instruction the paper says is worth keeping: “specific tooling to use with this repository” / minimal requirements.  ￼

It already embraces scoped guides (this is the right architecture)

You tell agents to find and follow closer AGENTS.md files in subdirectories (e.g., serialization).  ￼
And you actually have a strong example scoped guide that’s genuinely domain-specific and useful.  ￼

That’s important, because a lot of the paper’s downside comes from “one big context file applies to everything,” which inevitably includes irrelevant requirements for many tasks.  ￼

⸻

Where this root file is likely hurting you (per the paper’s mechanism)

The paper’s key behavioral finding is:
	•	With context files present, agents run more tests, grep/read more files, and write more files.  ￼
	•	Tool mentions are “behavior levers”: if you mention a tool/command, usage jumps.  ￼
	•	This increases “thinking” (reasoning tokens) materially.  ￼

Now look at your root file through that lens:

1) Too many “always-on” rules that are irrelevant for most tasks

Your root file includes:
	•	worktree + PRD/TODO process requirements  ￼
	•	a bunch of unit-testing rules (no reflection, assertThrows, DI preferences, etc.)  ￼
	•	broad style rules (logger usage, avoid FQNs, var guidance, DTO design, toString JSON guidance)  ￼
	•	component-specific guidance (TimeBarBuilder semantics, NetMomentumIndicator behavior)  ￼

An agent that tries to be compliant will spend time checking/optimizing for these even when they don’t matter to the issue at hand. That’s the exact “unnecessary requirements” failure mode the paper identifies.  ￼

2) The “full build” instruction is correct, but easy for agents to over-apply

You do say “during development, use narrow Maven tests,” but the top section reads like “run the full build after every code change” (and it’s emphasized as CRITICAL).  ￼

Agents tend to be literal. Given the paper’s evidence that context instructions increase testing and cost, you want to make it unambiguous:
	•	Fast loop: targeted tests while iterating
	•	Slow loop: full build once at the end (and only again if the full build fails)

Otherwise you’ll pay the “more testing → more cost” tax repeatedly.  ￼

⸻

A “keep / move / rewrite” pass on your root AGENTS.md

Keep in root (high value, low ambiguity)

These are “minimal requirements” that actually prevent failure:
	•	The canonical final verification command (scripts/run-full-build-quiet.sh) and what “green” means  ￼
	•	The explicit exceptions (workflow-only / changelog / docs-only)  ￼
	•	“Don’t ignore failures / don’t skip tests without approval”  ￼
	•	The instruction to find scoped AGENTS.md files  ￼

Rewrite in root (same intent, much less accidental overhead)
	•	Change “after every code change” language into: “run full build once when you have a candidate final patch; rerun only if it fails.”
	•	Make “required_permissions” conditional on the harness you’re using (otherwise it’s noise or even confusing).  ￼

Move out of root into scoped guides or CONTRIBUTING

This is the big payoff.
	•	Worktrees + PRD/TODO process → move to CONTRIBUTING.md or .agents/PROCESS.md and link it (optional for agents)  ￼
	•	Unit testing style (assertThrows, no reflection, DI preferences, Windows CRLF advice) → move to ta4j-core/AGENTS.md or docs/engineering/testing.md  ￼
	•	Serialization-specific rules → keep in the serialization scoped guide (you already have one that’s excellent) and remove duplication from root  ￼
	•	TimeBarBuilder / NetMomentumIndicator rules → move into their local areas (e.g., bars package guide, indicators guide). Keeping them at root guarantees they’re injected into unrelated tasks.  ￼

This is also consistent with broader empirical observations that context files in the wild are dominated by “make it functional” info (commands, implementation notes, architecture), and they tend to grow like config code. Putting domain gotchas in local guides is how you avoid root-level bloat.  ￼

⸻

A drop-in rewrite of your root AGENTS.md (minimal + scoped)

If you want to align with the paper’s “minimal requirements” recommendation without losing your quality bar, here’s a root file structure that keeps the important gates but reduces accidental overhead.

```
# AGENTS.md — ta4j (root)

## Goal
Help agents make correct, reviewable changes with minimal wasted work. This file contains only repo-wide “musts”.
For domain-specific areas, follow the nearest scoped `AGENTS.md` (see below).

## Definition of done (MUST)
Before declaring the task complete, run the full build script once:

- `scripts/run-full-build-quiet.sh`
- Exceptions: ONLY if all changes are exclusively in:
  - `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (e.g., `*.md`, `docs/`)

Build must be GREEN (0 failures, 0 errors).
In your completion message, include:
- the aggregated `Tests run / Failures / Errors / Skipped`
- the log path printed by the script (under `.agents/logs/`)

### Fast loop (recommended)
While iterating, use targeted tests for speed (example):
- `mvn -pl ta4j-core test -Dtest=...`

Do NOT run the full build repeatedly during iteration.
Run it when you have a candidate final patch; rerun only if it fails.

## Test policy (MUST)
- Do not ignore failing tests or build errors.
- Do not skip tests unless the user explicitly approves skipping.

## Scoped guides (MUST when applicable)
Many subsystems have their own `AGENTS.md` with local conventions.
Before editing a class or feature area gather the prevailing set of instructions by traversing between project root and the dir containing target class gathering all AGENTS.md encountered. Walk up/down the path only, do not enter other directories.
- use `agents_for_target.sh` in the scripts dir to identify prevailing AGENTS.md files: `bash scripts/agents_for_target.sh XorRule.java` in descending order of precedence
- in the case of conflicting agent instructions, the instructions from the deeper/closer AGENTS.md file override those higher (aka closer to project root)
```

Recommended next step
	1.	Make the root file minimal (like above).
	2.	Create or expand two scoped guides to absorb what you removed from root:

	•	ta4j-core/.../bars/AGENTS.md (TimeBarBuilder semantics)
	•	ta4j-core/.../indicators/AGENTS.md (NetMomentumIndicator rules)

	3.	Run a quick A/B on a handful of real tasks: current root vs minimal root. Track:

	•	number of test runs
	•	number of repo traversals/reads
	•	time-to-green / completion rate

That directly tests the paper’s claim in your exact environment, with your exact repo norms. 

## Implementation Checklist

Run each task once in worktree A (`feature/agents-reorg`) and once in worktree B (`feature/agents-reorg-b`) with the same model/prompt settings. For each run, capture completion status, time-to-green, number of full-build invocations, targeted test invocations, total tool calls, and token usage (if available).

Global decision protocol for every task:
1. Correctness gate first: a run only counts if the required code/tests are implemented and the full build is green.
2. If only one variant (A or B) passes the correctness gate, that variant wins the task.
3. If both pass, compare efficiency in this order: lower time-to-green, then fewer total tool calls, then lower token usage, then fewer full-build invocations.
4. If still tied, run one extra replicate for that task and select the variant with the lower median time-to-green across replicates.

- [ ] **A/B Task 1 - TimeBarBuilder Gap Regression:** Add or update tests proving `TimeBarBuilder` preserves chronological gap placement without backfilling missing periods.  
Task-specific winner check: both runs must include explicit gap-case assertions (at least one contiguous series and one gapped series) and pass all touched tests. Better variant is the one that reaches green with fewer repo-navigation reads/tool calls while preserving identical semantic assertions.

- [ ] **A/B Task 2 - NetMomentum Decay Behavior:** Add/adjust unit tests for decay semantics (`decay=1` legacy behavior and `decay<1` exponential fade) using deterministic steady-state expectations.  
Task-specific winner check: both runs must assert legacy parity at `decay=1` and formula-based expectations for `decay<1`. Better variant is the one that delivers complete decay coverage (including boundary cases) with lower time-to-green and fewer correction cycles.

- [ ] **A/B Task 3 - Serialization Schema Compatibility:** Implement a small serialization change that must preserve `rules` vs `components`, indicator label suppression, and legacy `children`/`baseIndicators` compatibility; extend regression tests accordingly.  
Task-specific winner check: both runs must keep backward compatibility and pass serialization round-trip suites without schema regressions. Better variant is the one that achieves this with fewer failed test iterations and fewer full-build reruns.

- [ ] **A/B Task 4 - Named Strategy Reconstruction Hardening:** Add validation and tests for malformed vararg constructor inputs while preserving `<SimpleName>_<param...>` label reconstruction behavior.  
Task-specific winner check: both runs must include positive reconstruction tests plus malformed-input negative tests (`IllegalArgumentException` paths). Better variant is the one with complete constructor-contract coverage and the lower total tool-call/token cost.

- [ ] **A/B Task 5 - Cross-Package Feature Patch:** Implement a small indicator enhancement plus corresponding docs/Javadoc and mirrored tests to measure multi-file navigation overhead under A vs B AGENTS layouts.  
Task-specific winner check: both runs must touch all required artifact types (production code, tests, and docs/Javadoc) and finish with green build. Better variant is the one that completes the multi-file workflow with lower navigation overhead (fewer file reads/searches) and faster time-to-green.
