# BTC Macro Elliott Demo Guide

`ElliottWaveBtcMacroCycleDemo` is now the BTC reporting wrapper over the unified core Elliott engine. The historical BTC macro study and the live BTC preset no longer rely on a preferred demo-local interpretation path.

## What you can run

- Historical truth-set validation:
  - uses the ossified BTC daily dataset plus the locked BTC anchor registry
  - scores the candidate profiles against the major macro turns
  - emits the macro-cycle JSON report and regression chart
- Live BTC preset:
  - uses only the `BarSeries` you provide at runtime
  - infers the current-cycle start from that supplied window alone
  - emits the current-cycle JSON summary plus the base case and alternate charts

## What stays the same

- The BTC demo still writes the same chart/report artifacts.
- The live preset still keeps compatibility with the existing file naming and reporting flow.

## What changed

- Historical anchor-to-anchor fits and live current-cycle fits now come from the same core-ranked Elliott logic.
- The BTC truth set is the regression guardrail for the core engine, not a second “smarter BTC-only engine.”
- The preferred BTC answer is no longer a legacy local-structure path. If the wrapper remains, it is there to load BTC resources, choose the prevalidated default profile, and render outputs.

## BTC truth target

The BTC anchor registry is now a calibration asset, not a runtime input. It encodes the committed macro truth target the canonical engine must learn to reproduce from price alone.

- Reference early segment:
  - `2011-06-09` top
  - `2011-11-18` low
  - calibration-only evidence, not production runtime structure
- Accepted validation cycle turns:
  - `2013-11-30` top, expected `WAVE5`
  - `2015-08-19` low, expected `CORRECTIVE_C`
  - `2017-12-18` top, expected `WAVE5`
  - `2018-12-16` low, expected `CORRECTIVE_C`
- Holdout cycle turns:
  - `2021-11-11` top, expected `WAVE5`
  - `2022-11-22` low, expected `CORRECTIVE_C`

The harness resolves broad committed registry windows against the ossified BTC daily dataset, then converts the distance from the resolved extremum to each window edge into `toleranceBefore` and `toleranceAfter`. That keeps acceptable match windows pinned to the committed truth target instead of drifting via runtime heuristics.

## Runtime contract

- Runtime historical decomposition must not depend on the BTC anchor registry.
- Live current-cycle analysis must not depend on the BTC anchor registry.
- The registry is valid only for:
  - offline calibration
  - offline regression
  - holdout scoring
  - debugging and evidence output
- If a runtime chart or report needs anchors to decide structure, that path is still not the canonical engine.

## Maintainer note

- Treat the BTC anchor registry and ossified BTC dataset as validation assets for core Elliott behavior.
- When changing Elliott ranking, swing preservation, or current-cycle fitting, rerun the real BTC macro regression and compare:
  - selected profile / hypothesis
  - accepted historical cycles / segments
  - current wave
  - phase and structural invalidation output
- Historical truth-set validation answers “does core still explain the locked BTC cycle turns?”
- Live runtime analysis answers “what does the unified core engine infer from the bars supplied right now?”
