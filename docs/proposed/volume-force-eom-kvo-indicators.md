# Volume Indicators Increment: Force Index, Ease of Movement, Klinger Volume Oscillator

## Scope
- Add `ForceIndexIndicator` in `org.ta4j.core.indicators.volume`.
- Add `EaseOfMovementIndicator` in `org.ta4j.core.indicators.volume`.
- Add `KlingerVolumeOscillatorIndicator` in `org.ta4j.core.indicators.volume`.
- Add regression tests under `ta4j-core/src/test/java/org/ta4j/core/indicators/volume`.
- Use inline spreadsheet reference sequences for bullish, bearish, and sideways scenarios.

## Requirements
- Reuse helper indicators and composition where practical.
- Follow unstable-bar contract and warm-up boundary behavior.
- Guard against invalid input conditions (`NaN`, zero volume, zero range).
- Include complete Javadoc formulas with credible references.
- Include serialization round-trip tests for each indicator.
- Update `CHANGELOG.md` under Unreleased.

## Design Notes
- Force Index formula:
  - Raw force: `(close(i) - close(i-1)) * volume(i)`
  - Indicator output: `EMA(rawForce, N)` (default `N=13`)
- Ease of Movement formula:
  - `distanceMoved = ((high(i)+low(i))/2) - ((high(i-1)+low(i-1))/2)`
  - `boxRatio = (volume(i) / volumeDivisor) / (high(i)-low(i))`
  - `EMV(1) = distanceMoved / boxRatio`
  - Indicator output: `SMA(EMV(1), N)` (default `N=14`, `volumeDivisor=100000000`)
- Klinger Volume Oscillator formula:
  - `trend(i) = sign((high+low+close)_i - (high+low+close)_{i-1})`
  - `dm(i) = high(i) - low(i)`
  - `cm(i) = cm(i-1)+dm(i)` when trend unchanged, else `dm(i-1)+dm(i)`
  - `vf(i) = volume(i) * trend(i) * |2 * ((dm(i)/cm(i)) - 1)| * 100`
  - `KVO = EMA(vf, shortPeriod) - EMA(vf, longPeriod)` (defaults 34/55)

## Test Plan
- For each indicator:
  - Bullish scenario regression against spreadsheet sequence.
  - Bearish scenario regression against spreadsheet sequence.
  - Sideways scenario regression against spreadsheet sequence.
  - Unstable-bar count assertion and boundary checks (`unstable-1`, `unstable`).
  - Serialization round-trip equality checks.

## Progress Checklist
- [x] Create branch/worktree from `master`.
- [x] Merge `feature/candlestick-production-readiness`.
- [x] Merge `feature/vortex-ultimate-production-audit`.
- [x] Implement `ForceIndexIndicator`.
- [x] Implement `EaseOfMovementIndicator`.
- [x] Implement `KlingerVolumeOscillatorIndicator`.
- [x] Add spreadsheet-regression tests for all three indicators.
- [x] Update `CHANGELOG.md`.
- [x] Run full build script and confirm green.
- [ ] Stage and commit.

## Decisions
- Use `KlingerVolumeOscillatorIndicator` naming to align with ta4j indicator conventions.
