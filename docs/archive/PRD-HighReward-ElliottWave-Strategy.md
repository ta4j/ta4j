# PRD: High-Reward Elliott Wave Strategy (ta4j-examples)

## Goals
- Implement the HighRewardElliottWaveStrategy as a NamedStrategy using the new ElliottWaveAnalyzer APIs.
- Provide a backtest demo that runs against ossified ETH-USD daily data.
- Add unit tests for entry/exit gating and parameter parsing.
- Iterate parameters and diagnostics so the strategy is viable for paper trading.

## Requirements
- Impulse-only scenarios, Wave 3/5 entries with confidence gating.
- Risk/reward filter based on Elliott Wave targets and stop levels.
- Trend + momentum confirmation; bias alignment.
- Use logger outputs only (no System.out).

## Key Decisions
- Adaptive ZigZag + MinMagnitudeSwingFilter; impulse-only PatternSet.
- Risk/reward computed against wave 2/4 corrective stop and furthest Fibonacci target.
- Momentum confirmation uses RSI or MACD (either).
- Exit on invalidation, completion, target hit, corrective stop breach, trend/momentum breakdown, or time stop.

## Implementation Checklist
- [x] Strategy class and config defaults
- [x] Backtest demo with dataset loader
- [x] Unit tests for entry/exit rules and parsing
- [x] Backtest runs captured for ETH-USD daily
- [x] Full build (scripts/run-full-build-quiet.sh)
- [x] Archive PRD after completion
