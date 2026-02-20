# ta4j `MACDVIndicator` vs Spiroglou MACD‑V (2022)
## Verification + Improvement PRD

---

# Executive Summary

1. ta4j’s current `MACDVIndicator` does **not** implement Spiroglou’s 2022 MACD‑V (volatility‑normalized MACD).
2. It instead implements a volume/ATR‑weighted MACD spread.
3. The implementation also contains documentation inconsistencies and robustness issues.
4. The recommended approach is:
   - Improve the existing `MACDVIndicator` without changing its behavior (backwards compatible).
   - Add a new canonical `VolatilityNormalizedMACDIndicator` that faithfully implements the 2022 MACD‑V.

---

# What MACD‑V (2022) Actually Is

## Canonical Formula

MACD‑V (Volatility Normalized MACD):

MACD‑V = [(EMA_fast − EMA_slow) / ATR_slow] × 100

Typical defaults:
- Fast EMA: 12
- Slow EMA: 26
- ATR: 26
- Signal line: EMA(MACD‑V, 9)
- Histogram: MACD‑V − Signal

This expresses momentum in units of ATR (percentage of volatility).

---

# What ta4j’s `MACDVIndicator` Currently Does

Current behavior:

- Builds VolumeIndicator
- Builds ATR for short and long periods
- Creates weights = volume / ATR
- Computes two “VWEMAs” (VWMA with EMA factory)
- Returns difference: shortVWEMA − longVWEMA

This is NOT volatility normalization. It is a volume/ATR weighted MACD variant.

Key differences:

| Topic | Paper MACD‑V | ta4j MACDVIndicator |
|-------|--------------|--------------------|
| Volatility use | Divide EMA spread by ATR | ATR only used in weights |
| Volume use | None | Explicit |
| Scaling | ×100 | None |
| Units | ATR percentage | Price units |

Conclusion: Not faithful to 2022 MACD‑V.

---

# Improvement Plan

Split into two tracks:

A) Improve existing MACDVIndicator (no behavior change)
B) Add new canonical volatility‑normalized MACD‑V indicator

---

# Deliverable A: Improve Existing `MACDVIndicator`

## A1 Documentation Fix

- Remove references implying this implements volatility‑normalized MACD‑V.
- Explicitly state it is a volume/ATR‑weighted MACD variant.

## A2 API Improvements (Additive, Backwards Compatible)

Add fields:
- shortBarCount
- longBarCount
- defaultSignalBarCount

Add constructors:
- (BarSeries, short, long, signal)
- (Indicator<Num>, short, long, signal)

Add convenience methods:
- getSignalLine()
- getHistogram()
- getMacd() → returns this

## A3 Serialization Safety

Sub‑indicators are transient.
Implement lazy rebuilding getters:

- getShortTermVwema()
- getLongTermVwema()

Rebuild sub‑indicator chain if null after deserialization.

## A4 Robust NaN Handling

In calculate():
- If index < unstableBars → return NaN
- Use Num.isNaNOrNull checks
- Guard against unexpected null states

## A5 Type Safety

- Replace raw Indicator types with Indicator<Num>

## A6 Tests

Create MACDVIndicatorTest covering:

- Constructor compatibility
- Signal/histogram correctness
- NaN behavior during unstable window
- Serialization round‑trip
- Numerical equivalence with composed sub‑indicators

Acceptance:
- No behavior regression
- All new APIs additive only

---

# Deliverable B: Add Canonical Volatility Normalized MACD‑V

Create:

VolatilityNormalizedMACDIndicator
(extending CachedIndicator<Num>)

## Defaults

- Fast EMA: 12
- Slow EMA: 26
- ATR length: slow
- Signal length: 9
- Scale factor: 100

## Calculation

spread = EMA_fast − EMA_slow

if ATR == 0:
    if spread == 0 → return 0
    else → return NaN

MACD‑V = (spread / ATR) × scale

Signal = EMA(MACD‑V, signalLen)

Histogram (default):
MACD‑V − Signal

## Public API

Constructors:
- (BarSeries)
- (Indicator<Num>)
- (BarSeries, fast, slow, signal)
- Fully specified constructor

Methods:
- getSignalLine()
- getHistogram()
- getMacdV() → returns this

Optional:
- HistogramMode enum (MACD_MINUS_SIGNAL, SIGNAL_MINUS_MACD)

## Edge Cases

- Return NaN during unstable period
- Validate periods >= 1
- fast <= slow required

## Tests

Create VolatilityNormalizedMACDIndicatorTest:

1. Formula equivalence vs composed MACD/ATR
2. ATR == 0 edge case
3. Histogram correctness
4. Unstable window NaN behavior

Acceptance:
- Matches composition reference within tolerance
- Stable behavior across edge cases

---

# Optional Enhancement: Momentum Lifecycle Classification

Add enum:

MACDVMomentumState

Based on thresholds:
- > +150 → Risk (high)
- +50 to +150 → Rallying / Retracing
- −50 to +50 → Ranging
- −150 to −50 → Rebounding / Reversing
- < −150 → Risk (low)

Provide helper:
getMomentumState(index)

---

# Backwards Compatibility Summary

- Existing MACDVIndicator behavior unchanged.
- New volatility‑normalized indicator added separately.
- All API additions are additive.
- No breaking signature changes required.

---

# Recommended Execution Order

1. Implement VolatilityNormalizedMACDIndicator + tests.
2. Improve MACDVIndicator robustness + docs.
3. Update wiki and examples.
4. Optional: lifecycle helper.

---

# Outcome

After implementation:

- ta4j will contain a correct, research‑faithful MACD‑V.
- Existing users are unaffected.
- Documentation confusion eliminated.
- Edge cases handled safely.
- API symmetry with MACDIndicator maintained.

This produces a production‑grade, canonical MACD‑V implementation suitable for research, institutional use, and advanced strategy design.

## Implementation Checklist

- [x] Add canonical `VolatilityNormalizedMACDIndicator` and API surface.
- [x] Add robust unit tests for canonical indicator (formula, unstable bars, zero ATR, serialization).
- [x] Improve existing `MACDVIndicator` docs and additive API (constructors, signal/histogram helpers).
- [x] Add lazy transient rebuild and NaN/unstable protections in `MACDVIndicator`.
- [x] Add/refresh `MACDVIndicatorTest` coverage (compatibility, signal/histogram, unstable bars, serialization).
- [x] Run full build script and verify zero failures/errors.
