/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Net Momentum Indicator.
 *
 * <p>
 * This indicator measures the cumulative deviation of an oscillating indicator
 * from its neutral pivot point over a specified timeframe. It helps identify:
 * <ul>
 * <li>Persistent momentum bias (bullish/bearish energy)</li>
 * <li>Potential mean reversion opportunities at extremes</li>
 * <li>Divergences between price action and momentum</li>
 * </ul>
 *
 * <p>
 * The calculation process:
 * <ol>
 * <li>Applies Kalman filter smoothing to the input oscillator</li>
 * <li>Calculates deviation from the neutral pivot value</li>
 * <li>Maintains a running total over the specified timeframe</li>
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
 * <li><b>Regime filter</b>: Sustained positive readings indicate a bullish
 * environment; sustained negative readings a bearish one. The zero line can act
 * as a trend filter for enabling/disabling strategies.</li>
 * <li><b>Breakout readiness</b>: A steady rise from negative to positive while
 * price is still range-bound can foreshadow directional breaks.</li>
 * <li><b>Continuation vs. exhaustion</b>: New price highs with flattening/falling
 * net momentum warn of waning fuel; rising net momentum confirms trend
 * continuation.</li>
 * <li><b>Mean reversion extremes</b>: Unusually high/low cumulative values versus
 * a rolling history highlight stretched conditions that often mean-revert.</li>
 * <li><b>Noise reduction in ranges</b>: Oscillators often whipsaw around the
 * pivot in ranges; net momentum tends to hover near zero, reducing false
 * signals.</li>
 * </ul>
 *
 * <p>
 * RSI-specific intuition (pivot at 50):
 * <ul>
 * <li>Brief RSI readings just above 50 produce small net momentum; RSI between
 * 55–70 for many bars produces large positive net momentum (persistent buying
 * pressure).</li>
 * <li>Two series can end at RSI = 60; the one that spent more time and distance
 * above 50 will show higher net momentum.</li>
 * <li>Zero-line crosses and slope changes in net momentum can be used as robust
 * filters or timing aids compared to single RSI threshold checks.</li>
 * </ul>
 *
 * <p>
 * Common usage with RSI:
 *
 * <pre>{@code
 * RSIIndicator rsi = new RSIIndicator(closePrice, 14);
 * NetMomentumIndicator netMomentum = new NetMomentumIndicator(rsi, 20);
 * }</pre>
 *
 * @see RSIIndicator
 * @see KalmanFilterIndicator
 * @see RunningTotalIndicator
 */
public class NetMomentumIndicator extends CachedIndicator<Num> {

    private static final int DEFAULT_RSI_NEUTRAL_PIVOT = 50;

    private final RunningTotalIndicator runningTotalIndicator;
    private final KalmanFilterIndicator smoothedIndicator;
    private final Indicator<Num> oscillatingIndicator;

    /**
     * Constructor for Net Momentum Indicator.
     *
     * @param oscillatingIndicator the input oscillating indicator (e.g., RSI,
     *                             Stochastic)
     * @param timeFrame            the period for the running total calculation
     *                             (must be > 0)
     * @param neutralPivotValue    the neutral/equilibrium value of the oscillator
     * @throws IllegalArgumentException if timeFrame <= 0 or oscillatingIndicator is
     *                                  null
     */
    public NetMomentumIndicator(Indicator<Num> oscillatingIndicator, int timeFrame, int neutralPivotValue) {
        super(oscillatingIndicator);

        if (timeFrame <= 0) {
            throw new IllegalArgumentException("Time frame must be greater than 0");
        }

        this.oscillatingIndicator = oscillatingIndicator;

        this.smoothedIndicator = new KalmanFilterIndicator(oscillatingIndicator);
        BinaryOperationIndicator deltaFromNeutralIndicator = BinaryOperationIndicator.difference(smoothedIndicator,
                neutralPivotValue);
        this.runningTotalIndicator = new RunningTotalIndicator(deltaFromNeutralIndicator, timeFrame);
    }

    /**
     * Constructor specifically for RSI indicator with default neutral pivot of 50.
     *
     * @param rsiIndicator the RSI indicator
     * @param timeFrame    the period for the running total calculation (must be >
     *                     0)
     * @throws IllegalArgumentException if timeFrame <= 0 or rsiIndicator is null
     */
    public NetMomentumIndicator(RSIIndicator rsiIndicator, int timeFrame) {
        this(rsiIndicator, timeFrame, DEFAULT_RSI_NEUTRAL_PIVOT);
    }

    @Override
    protected Num calculate(int index) {
        return runningTotalIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(oscillatingIndicator.getCountOfUnstableBars(), smoothedIndicator.getCountOfUnstableBars());
    }
}