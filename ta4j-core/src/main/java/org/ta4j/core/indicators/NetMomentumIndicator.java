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
 * This indicator measures the cumulative deviation of an oscillating indicator
 * from its neutral pivot point over a specified timeframe. Its primary use is
 * short-memory continuation detection: when recent oscillator pressure crosses
 * and remains above or below the neutral line, the market is showing
 * directional pressure that can continue into nearby swing highs or swing lows.
 * It helps identify:
 * <ul>
 * <li>Persistent momentum bias (bullish/bearish energy)</li>
 * <li>Continuation pressure after zero-line crosses</li>
 * <li>Divergences between price action and momentum</li>
 * </ul>
 *
 * <p>
 * The calculation process:
 * <ol>
 * <li>Applies Kalman filter smoothing to the input oscillator</li>
 * <li>Calculates deviation from the neutral pivot value</li>
 * <li>Maintains a running total over the specified timeframe</li>
 * <li>Optionally applies exponential decay so that momentum can reset through
 * time/sideways action</li>
 * </ol>
 *
 * <p>
 * Why use this over the raw oscillator? The raw oscillator shows the
 * instantaneous state, while the Net Momentum integrates both the magnitude and
 * the duration spent above/below the neutral pivot (e.g., RSI 50). This
 * distinguishes persistent pressure from fleeting spikes and provides a more
 * stable signal for regime detection.
 * <ul>
 * <li>Two periods can end with the same oscillator value, but the one that
 * accumulated more time and distance above the pivot will have higher net
 * momentum.</li>
 * <li>Kalman smoothing reduces whipsaw before accumulation, improving the
 * usefulness of the running total for decision-making.</li>
 * </ul>
 *
 * <p>
 * Practical scenarios where Net Momentum adds insight beyond the oscillator:
 * <ul>
 * <li><b>Continuation gate</b>: Sustained positive readings indicate bullish
 * pressure; sustained negative readings indicate bearish pressure. A cross up
 * through zero is commonly treated as confirmation that upside pressure is
 * active, while a cross down through zero confirms downside pressure.</li>
 * <li><b>Breakout follow-through</b>: A fresh cross up can confirm that a local
 * high is being pursued by recent momentum, while a fresh cross down can
 * confirm pressure toward a local low.</li>
 * <li><b>Continuation vs. exhaustion</b>: New price highs with
 * flattening/falling net momentum warn of waning fuel; rising net momentum
 * confirms trend continuation.</li>
 * <li><b>Control studies</b>: Reversal interpretations of zero-line crosses can
 * be evaluated as controls, but the indicator's short-memory configuration is
 * generally better suited to continuation hypotheses.</li>
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
 * <li>Brief RSI readings just above 50 produce small net momentum; RSI between
 * 55–70 for many bars produces large positive net momentum (persistent buying
 * pressure).</li>
 * <li>Two series can end at RSI = 60; the one that spent more time and distance
 * above 50 will show higher net momentum.</li>
 * <li>Zero-line crosses and slope changes in net momentum can be used as
 * continuation filters or timing aids compared to single RSI threshold
 * checks.</li>
 * </ul>
 *
 * <p>
 * Common short-memory continuation usage with RSI:
 *
 * <pre>{@code
 * RSIIndicator rsi = new RSIIndicator(closePrice, 7);
 * NetMomentumIndicator netMomentum = NetMomentumIndicator.forRsiWithDecay(rsi, 14, 0.5);
 * }</pre>
 *
 * A faster downside sit-out lens can use the same RSI source with a shorter
 * memory:
 *
 * <pre>{@code
 * RSIIndicator rsi = new RSIIndicator(closePrice, 7);
 * NetMomentumIndicator downsidePressure = NetMomentumIndicator.forRsiWithDecay(rsi, 7, 0.5);
 * }</pre>
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

        double rawPivot = neutralPivotValue.doubleValue();
        if (Double.isNaN(rawPivot) || Double.isInfinite(rawPivot)) {
            throw new IllegalArgumentException("Neutral pivot value must be finite (not NaN or infinite)");
        }

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
        this.decayFactor = numFactory.numOf(decayFactor);
        this.decayFactorAtWindowLimit = this.decayFactor.pow(timeFrame);
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

        Num delta = deltaFromNeutralIndicator.getValue(index);
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
            Num expiredContribution = expiredIndex < getCountOfUnstableBars() ? zero
                    : deltaFromNeutralIndicator.getValue(expiredIndex);
            if (Num.isNaNOrNull(expiredContribution)) {
                expiredContribution = zero;
            }
            expiredContribution = expiredContribution.multipliedBy(decayFactorAtWindowLimit);
            return decayedWithCurrent.minus(expiredContribution);
        }

        return decayedWithCurrent;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }
}
