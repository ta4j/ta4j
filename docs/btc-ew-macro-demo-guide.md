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

## Maintainer note

- Treat the BTC anchor registry and ossified BTC dataset as validation assets for core Elliott behavior.
- When changing Elliott ranking, swing preservation, or current-cycle fitting, rerun the real BTC macro regression and compare:
  - selected profile / hypothesis
  - accepted historical cycles / segments
  - current wave
  - phase and structural invalidation output
- Historical truth-set validation answers “does core still explain the locked BTC cycle turns?”
- Live runtime analysis answers “what does the unified core engine infer from the bars supplied right now?”
