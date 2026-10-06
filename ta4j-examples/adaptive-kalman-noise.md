# Adaptive Kalman noise: an opt-in composition example

`ta4jexamples.analysis.forecast.AdaptiveKalmanNoiseExample` keeps the filter
engine independent of the indicators that supply its noise. It compares three
kinematic Kalman models and a last-close benchmark on the same one-step forecast
origins. It does not change any core defaults or claim statistically calibrated
"auto-tuning".

## Run

From the repository root:

```bash
./mvnw -pl ta4j-examples -am install \
  && ./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.analysis.forecast.AdaptiveKalmanNoiseExample
```

To supply the previous bar's dynamic Q/R instead of same-bar Q/R, append:

```bash
-Dexec.args="--lag-noise"
```

The example reuses the offline S&P 500 weekly fixture from
`KinematicKalmanForecastExample`. Its final July 27, 2026 aggregate is a partial
week as of July 30; this example excludes that terminal bar rather than treating
it as a completed one-week target. No market-data download is needed. The output
reports common sample count, skipped origins, MAE/RMSE, each model's MAE relative
to the last-close benchmark (above `1.000x` means worse than repeating the
current close), and the latest corrected state's Q/R.

## Recipe and units

With bar index `t`, the illustrative policy is:

```text
B[t] = max(ATR(14)[t]^2, 1e-8)
u[t] = clip(relativeVolume[t], 0.25, 4)
Q[t] = 0.01 * B[t]
R[t] = B[t] / u[t]^0.5
```

`B`, Q, and R are price-variance scales for this per-bar model. The floor is an
explicit example constant in squared-price units, not a universal minimum tick
or a floor silently imposed by `KalmanNoiseIndicator`. Because it is applied
before scaling, the Q floor here is `1e-10`, not `1e-8`. Choose a floor and scales
appropriate to the instrument and numeric precision.

Raw ATR is a price-scale quantity, not a variance. Raw volume is not a price
variance either. Moreover, using `R = volume` directly gives high-volume bars
*less* measurement weight, the opposite of this example's confidence hypothesis.
The existing wrapper deliberately validates numeric shape, not economic units.

The implementation reuses `NumericIndicator.squared/max/min/pow/dividedBy`,
`SMAIndicator`, `LowestValueIndicator`, and `KalmanNoiseIndicator` rather than
teaching the Kalman engine about ATR or volume. The small private relative-volume
indicator exists only to express the example's missing-data policy; it is not a
new public core API or a serialization contract.

## Volume policy

Relative volume divides **single-bar volume** by its 20-bar simple moving
average, including the current bar. `VolumeIndicator(series, 20)` would instead
be a rolling sum, so it is not used as the denominator.

| Situation | Relative-volume input before clipping |
| --- | --- |
| Full usable window and positive mean | Current volume / SMA(volume) |
| Volume window still warming up | 1: neutral confidence |
| Missing/non-finite current volume or mean | 1: neutral confidence |
| Zero or nonpositive mean | 1: neutral confidence |
| Negative historical volume remains in the averaging window | 1: neutral confidence until it leaves the window |
| Actual zero current volume with a usable positive mean | 0, subsequently clipped to 0.25 |
| Negative current volume | Unavailable, not silently repaired |

The helper waits for the SMA's declared full-window boundary even though the
underlying SMA can expose partial-window values. It reports zero unstable bars
because its neutral fallback is intentionally defined during that warm-up.
A rolling minimum detects negative historical volume: a later valid observation
must not gain confidence from an artificially depressed average. Adaptation
resumes once the corrupt value leaves the window.
The ATR variance still stays unavailable during ATR warm-up: the variance floor
does **not** replace an unavailable ATR with a valid number.

For fixed ATR, relative volume of 4 halves R; relative volume of 0.25 doubles R.
High volume implying greater confidence is a hypothesis, not a guarantee. Verify
the meaning and consistency of the feed's volume before using that relationship.
To weaken or disable the hypothesis, change `VOLUME_EXPONENT` toward zero; to
strengthen it, move toward one. The supplied value is 0.5.

## Construction and timing

The whole recipe is a fluent `NumericIndicator` chain that ends at the existing
constructor boundary:

```java
NumericIndicator variance = NumericIndicator.of(new ATRIndicator(series, 14)).squared().max(1e-8);
NumericIndicator confidence = NumericIndicator.of(relativeVolume).max(0.25).min(4).pow(0.5);
KalmanNoiseIndicator q = new KalmanNoiseIndicator(variance, 0.01);
KalmanNoiseIndicator r = new KalmanNoiseIndicator(variance.dividedBy(confidence));
KinematicKalmanFilterIndicator filter =
        new KinematicKalmanFilterIndicator(new ClosePriceIndicator(series), q, r);
Forecast nextClose = filter.forecast().getValue(series.getEndIndex());
```

`relativeVolume` can be any `Indicator<Num>`. On a clean feed, the plain ratio
`volume.dividedBy(volume.sma(20))` with
`NumericIndicator volume = NumericIndicator.of(new VolumeIndicator(series))` is
enough; it uses partial windows during warm-up and does not neutralize missing
or corrupt volume. The example's private `RelativeVolumeIndicator` adds the
policy below, and the package-local `NoiseInputs` record exposes the
intermediate sources for focused tests. No new constructor overload or policy
enum is necessary.

Default timing uses the finalized current bar's ATR and volume when correcting
that same bar's price. This is causal at bar close, but those quantities are not
known before the bar arrives. A strictly prior-bar noise policy uses:

```java
KalmanNoiseIndicator priorQ = new KalmanNoiseIndicator(new PreviousValueIndicator(q));
KalmanNoiseIndicator priorR = new KalmanNoiseIndicator(new PreviousValueIndicator(r));
```

`--lag-noise` applies that composition to the dynamic sources. Constant baseline
variances are already known and are not shifted. Lagging adds one warm-up bar.
Neither option makes a future-leaking upstream source safe.

The filter remains uninitialized until its source, Q, and R are usable. It then
seeds at the observed price with zero velocity. After initialization, an
unavailable input makes only that bar unavailable and preserves the last usable
state; this example does not change the core's skipped-bar transition semantics.

## Read the comparison correctly

The three models are fixed Q/R (`0.01`, `1`), ATR-squared Q with fixed R (`1`), and
the ATR/relative-volume recipe above. Those fixed values are illustrative
absolute variances, not separately optimized baselines.

At origin `t`, the forecast mean is the corrected position plus velocity,
`position[t] + velocity[t]`, and the target is `close[t + 1]`. Scoring the
corrected same-bar price against `close[t]` would instead reward reducing
smoothing and would not measure one-step forecasting ability.

The final 520 origin positions are considered chronologically. An origin is
scored only if **all three models** and the observation are usable; the last-close
benchmark uses that identical accepted set. Skipped origins are reported. With
no common samples, errors are unavailable, not zero. The final bar of the
supplied evaluation series is a target only, never an origin with an unobserved
next bar.

This is fixed-parameter walk-forward scoring, not a parameter search or proof
of out-of-sample superiority. For research, choose parameters using training
periods, freeze them before evaluating held-out periods, and compare across
instruments and regimes. Examine missingness as well as errors. If adding
predictive intervals, evaluate coverage and width separately; this example
reports point errors and makes no calibration claim.

Finally, when Q and R both share the ATR-squared scale, their ratio is
`0.01 * u[t]^0.5`. Sustained higher ATR alone does not change that ratio. The
fixed-R alternative is included to isolate the effect of changing Q without
simultaneously changing R.

## Verification

`AdaptiveKalmanNoiseExampleTest` uses small synthetic fixtures and exercises
both `DoubleNumFactory` and `DecimalNumFactory` for the numerical behavior. It
covers noise units/formulas, clipping, warm-up, missing versus zero volume,
negative-volume unavailability and subsequent rolling-window recovery, the
variance floor, first-observation seeding, prior-bar timing, common-origin
scoring, next-bar targets, and empty evaluations. It does not assert that an
uncalibrated adaptive recipe must beat a benchmark.
