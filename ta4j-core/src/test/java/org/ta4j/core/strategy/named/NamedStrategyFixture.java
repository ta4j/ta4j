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
package org.ta4j.core.strategy.named;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Test fixture for {@link NamedStrategy} that captures constructor invocation
 * counts and validates indicator parameter handling.
 */
public final class NamedStrategyFixture extends NamedStrategy {

    private static final AtomicInteger TYPED_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger VARARGS_CONSTRUCTIONS = new AtomicInteger();
    private static final String NAME = "FixtureNamedStrategy";

    private final Num threshold;
    private final boolean delegated;

    private NamedStrategyFixture(BarSeries series, Num threshold, int unstableBars, boolean delegated) {
        super(NAME, buildEntryRule(series, threshold), buildExitRule(series, threshold), unstableBars,
                List.of(formatArgument(threshold)));
        this.threshold = threshold;
        this.delegated = delegated;
        TYPED_CONSTRUCTIONS.incrementAndGet();
    }

    protected NamedStrategyFixture(BarSeries series, Num threshold, int unstableBars) {
        this(series, threshold, unstableBars, false);
    }

    public NamedStrategyFixture(BarSeries series, String... parameters) {
        this(series, parseThreshold(series, parameters), parseUnstable(parameters), true);
        VARARGS_CONSTRUCTIONS.incrementAndGet();
    }

    public static NamedStrategyFixture create(BarSeries series, Num threshold, int unstableBars) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(threshold, "threshold");
        return new NamedStrategyFixture(series, threshold, unstableBars, false);
    }

    public static void resetConstructionCounters() {
        TYPED_CONSTRUCTIONS.set(0);
        VARARGS_CONSTRUCTIONS.set(0);
    }

    public static int typedConstructionCount() {
        return TYPED_CONSTRUCTIONS.get();
    }

    public static int varargsConstructionCount() {
        return VARARGS_CONSTRUCTIONS.get();
    }

    public Num getThreshold() {
        return threshold;
    }

    public boolean isDelegated() {
        return delegated;
    }

    private static Num parseThreshold(BarSeries series, String... parameters) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.length != 2) {
            throw new IllegalArgumentException("NamedStrategyFixture expects [threshold, unstable]");
        }
        return series.numFactory().numOf(parameters[0]);
    }

    private static int parseUnstable(String... parameters) {
        return Integer.parseInt(parameters[1]);
    }

    private static Rule buildEntryRule(BarSeries series, Num threshold) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> constant = new ConstantIndicator<>(series, threshold);
        return new CrossedUpIndicatorRule(close, constant);
    }

    private static Rule buildExitRule(BarSeries series, Num threshold) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> constant = new ConstantIndicator<>(series, threshold);
        return new UnderIndicatorRule(close, constant);
    }

    private static String formatArgument(Num threshold) {
        double value = threshold.doubleValue();
        return Double.isNaN(value) ? "NaN" : threshold.toString();
    }
}
