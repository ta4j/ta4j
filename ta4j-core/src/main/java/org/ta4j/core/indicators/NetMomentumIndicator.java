/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Objects;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Net Momentum Indicator.
 *
 * <p>
 * This indicator models oscillator extremes as a finite psychological battery
 * over a specified timeframe. For RSI-style inputs, readings below the neutral
 * pivot charge positive rebound energy, while readings above the pivot deplete
 * it. It helps identify:
 * <ul>
 * <li>Potential troughs when rebound battery crosses back above zero after
 * downside pressure</li>
 * <li>Potential tops when rebound energy is worked off and crosses below
 * zero</li>
 * <li>Divergences between price action and remaining oscillator fuel</li>
 * </ul>
 *
 * <p>
 * The calculation process:
 * <ol>
 * <li>Applies Kalman filter smoothing to the input oscillator</li>
 * <li>Calculates inverted distance from the neutral pivot value</li>
 * <li>Adds a pivot-normalized squared distance term so extremes count more than
 * repeated mild deviations</li>
 * <li>Maintains a running battery balance over the specified timeframe</li>
 * <li>Optionally applies exponential decay so that momentum can reset through
 * time/sideways action</li>
 * </ol>
 *
 * <p>
 * Why use this over the raw oscillator? The raw oscillator shows the
 * instantaneous state, while the Net Momentum integrates both the non-linear
 * magnitude and the duration spent away from the neutral pivot (e.g., RSI 50).
 * This distinguishes a real store of regime fuel from fleeting pivot noise and
 * provides a more stable signal for swing-scale regime exhaustion.
 * <ul>
 * <li>Two periods can end with the same oscillator value, but the one that
 * accumulated more time and distance below the pivot will have higher rebound
 * battery.</li>
 * <li>A larger extreme contributes more than multiple mild deviations; for
 * example, an RSI move to 90 has more impact than two readings at 70.</li>
 * <li>Kalman smoothing reduces whipsaw before accumulation, improving the
 * usefulness of the running total for decision-making.</li>
 * </ul>
 *
 * <p>
 * Practical scenarios where Net Momentum adds insight beyond the oscillator:
 * <ul>
 * <li><b>Rebound readiness</b>: A steady rise through zero after downside RSI
 * pressure often marks a local trough or the early transition away from
 * one.</li>
 * <li><b>Exhaustion timing</b>: A fall through zero after upside RSI pressure
 * often marks a local top or the late transition into one.</li>
 * <li><b>Mean reversion extremes</b>: Unusually high/low cumulative battery
 * values versus a rolling history highlight stretched conditions that often
 * mean-revert.</li>
 * <li><b>Noise reduction in ranges</b>: Oscillators often whipsaw around the
 * pivot in ranges; net momentum tends to hover near zero, reducing false
 * signals.</li>
 * </ul>
 *
 * <p>
 * The optional decay factor (defaults to {@code 1}) models how sideways price
 * action can reset extreme readings. Values below {@code 1} exponentially
 * reduce the influence of older contributions inside the window, pulling the
 * running tally back toward zero unless fresh momentum persists.
 *
 * <p>
 * RSI-specific intuition (pivot at 50):
 * <ul>
 * <li>Brief RSI readings just below 50 produce small positive charge; RSI
 * between 0 and 30 for many bars produces large positive rebound battery.</li>
 * <li>Readings above 50 subtract from that battery; a cross below zero suggests
 * the prior rebound fuel has been worked off.</li>
 * <li>Zero-line crosses and slope changes can be used as swing-turn timing aids
 * compared to single RSI threshold checks.</li>
 * </ul>
 *
 * <p>
 * Common usage with RSI:
 *
 * <pre>{@code
 * RSIIndicator rsi = new RSIIndicator(closePrice, 14);
 * NetMomentumIndicator netMomentum = NetMomentumIndicator.forRsi(rsi, 20);
 * }</pre>
 *
 * Including a decay factor to emphasize time-based mean reversion:
 *
 * <pre>{@code
 * RSIIndicator rsi = new RSIIndicator(closePrice, 14);
 * NetMomentumIndicator netMomentum = NetMomentumIndicator.forRsiWithDecay(rsi, 20, 0.85);
 * }</pre>
 *
 * <p>
 * Battery-model adaptation by TheCookieLab.
 *
 * @see RSIIndicator
 * @see KalmanFilterIndicator
 *
 * @since 0.19
 */
public class NetMomentumIndicator extends RecursiveCachedIndicator<Num> {

    private static final double DEFAULT_RSI_NEUTRAL_PIVOT = 50.0;
    private static final double DEFAULT_DECAY_FACTOR = 1.0;

    private final KalmanFilterIndicator smoothedIndicator;
    private final Indicator<Num> oscillatingIndicator;
    private final Indicator<Num> deltaFromNeutralIndicator;
    private final int timeFrame;
    private final Num decayFactor;
    private final Num decayFactorAtWindowLimit;
    private final Num convexityScale;
    private final Num zero;
    private final int unstableBars;

    /**
     * Constructor for Net Momentum Indicator.
     *
     * @param oscillatingIndicator the input oscillating indicator (e.g., RSI,
     *                             Stochastic)
     * @param timeFrame            the period for the running total calculation
     *                             (must be > 0)
     * @param neutralPivotValue    the neutral/equilibrium value of the oscillator;
     *                             fractional pivots are supported
     * @throws IllegalArgumentException if timeFrame {@literal <=} 0
     * @throws NullPointerException     if oscillatingIndicator or neutralPivotValue
     *                                  is null
     */
    public NetMomentumIndicator(Indicator<Num> oscillatingIndicator, int timeFrame, Number neutralPivotValue) {
        this(oscillatingIndicator, timeFrame, neutralPivotValue, DEFAULT_DECAY_FACTOR);
    }

    /**
     * Constructor for Net Momentum Indicator with configurable decay factor.
     *
     * @param oscillatingIndicator the input oscillating indicator (e.g., RSI,
     *                             Stochastic)
     * @param timeFrame            the period for the running total calculation
     *                             (must be > 0)
     * @param neutralPivotValue    the neutral/equilibrium value of the oscillator;
     *                             fractional pivots are supported
     * @param decayFactor          the per-bar retention factor in [0, 1]. Use 1 for
     *                             no decay, values below 1 pull the indicator back
     *                             toward the neutral pivot over time
     * @throws IllegalArgumentException if timeFrame {@literal <=} 0 or decayFactor
     *                                  is outside [0, 1]
     * @throws NullPointerException     if oscillatingIndicator, neutralPivotValue
     *                                  or decayFactor is null
     * @since 0.19
     */
    public NetMomentumIndicator(Indicator<Num> oscillatingIndicator, int timeFrame, Number neutralPivotValue,
            Number decayFactor) {
        super(Objects.requireNonNull(oscillatingIndicator, "Oscillating indicator must not be null"));

        Objects.requireNonNull(neutralPivotValue, "Neutral pivot value must not be null");
        Objects.requireNonNull(decayFactor, "Decay factor must not be null");

        if (timeFrame <= 0) {
            throw new IllegalArgumentException("Time frame must be greater than 0");
        }

        this.oscillatingIndicator = oscillatingIndicator;
        this.timeFrame = timeFrame;
        this.smoothedIndicator = new KalmanFilterIndicator(oscillatingIndicator);
        this.deltaFromNeutralIndicator = BinaryOperationIndicator.difference(smoothedIndicator, neutralPivotValue);

        double rawDecay = decayFactor.doubleValue();
        if (Double.isNaN(rawDecay) || Double.isInfinite(rawDecay) || rawDecay < 0.0 || rawDecay > 1.0) {
            throw new IllegalArgumentException("Decay factor must be between 0 and 1 inclusive");
        }

        NumFactory numFactory = oscillatingIndicator.getBarSeries().numFactory();
        Num neutralPivot = numFactory.numOf(neutralPivotValue);
        double rawPivot = neutralPivot.doubleValue();
        if (Num.isNaNOrNull(neutralPivot) || Double.isInfinite(rawPivot)) {
            throw new IllegalArgumentException("Neutral pivot value must be finite (not NaN or infinite)");
        }
        this.decayFactor = numFactory.numOf(decayFactor);
        this.decayFactorAtWindowLimit = this.decayFactor.pow(timeFrame);
        this.convexityScale = neutralPivot.abs().max(numFactory.one());
        this.zero = numFactory.zero();
        int smoothingUnstable = Math.max(oscillatingIndicator.getCountOfUnstableBars(),
                smoothedIndicator.getCountOfUnstableBars());
        int windowUnstable = Math.max(0, timeFrame - 1);
        this.unstableBars = smoothingUnstable + windowUnstable;
    }

    /**
     * Creates a {@link NetMomentumIndicator} configured for RSI inputs with the
     * standard neutral pivot of 50.
     *
     * @param rsiIndicator the RSI indicator
     * @param timeFrame    the period for the running total calculation (must be >
     *                     0)
     * @return the configured {@link NetMomentumIndicator}
     * @throws IllegalArgumentException if timeFrame {@literal <=} 0
     * @throws NullPointerException     if rsiIndicator is null
     * @since 0.19
     */
    public static NetMomentumIndicator forRsi(RSIIndicator rsiIndicator, int timeFrame) {
        return new NetMomentumIndicator(rsiIndicator, timeFrame, DEFAULT_RSI_NEUTRAL_PIVOT);
    }

    /**
     * Creates a {@link NetMomentumIndicator} configured for RSI inputs with the
     * standard neutral pivot of 50 and a custom decay factor.
     *
     * @param rsiIndicator the RSI indicator
     * @param timeFrame    the period for the running total calculation (must be >
     *                     0)
     * @param decayFactor  the per-bar retention factor in [0, 1]
     * @return the configured {@link NetMomentumIndicator}
     * @throws IllegalArgumentException if timeFrame {@literal <=} 0 or decayFactor
     *                                  is outside [0, 1]
     * @throws NullPointerException     if rsiIndicator or decayFactor is null
     * @since 0.19
     */
    public static NetMomentumIndicator forRsiWithDecay(RSIIndicator rsiIndicator, int timeFrame, Number decayFactor) {
        return new NetMomentumIndicator(rsiIndicator, timeFrame, DEFAULT_RSI_NEUTRAL_PIVOT, decayFactor);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num delta = contribution(index);
        if (Num.isNaNOrNull(delta)) {
            return NaN;
        }

        if (index == 0) {
            return delta;
        }

        Num previousValue = getValue(index - 1);
        if (Num.isNaNOrNull(previousValue)) {
            previousValue = zero;
        }
        Num decayedWithCurrent = previousValue.multipliedBy(decayFactor).plus(delta);

        if (index >= timeFrame) {
            int expiredIndex = index - timeFrame;
            Num expiredContribution = expiredIndex < getCountOfUnstableBars() ? zero : contribution(expiredIndex);
            if (Num.isNaNOrNull(expiredContribution)) {
                expiredContribution = zero;
            }
            expiredContribution = expiredContribution.multipliedBy(decayFactorAtWindowLimit);
            return decayedWithCurrent.minus(expiredContribution);
        }

        return decayedWithCurrent;
    }

    private Num contribution(int index) {
        Num rawDistance = deltaFromNeutralIndicator.getValue(index);
        if (Num.isNaNOrNull(rawDistance)) {
            return NaN;
        }

        Num distance = rawDistance.abs();
        Num convexDistance = distance.plus(distance.pow(2).dividedBy(convexityScale));
        return rawDistance.isNegative() ? convexDistance : convexDistance.negate();
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }
}
