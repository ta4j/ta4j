/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.strategy.named;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fixture for {@link NamedStrategy} that captures constructor invocation
 * counts and validates indicator parameter handling.
 */
public final class NamedStrategyFixture extends NamedStrategy {

    static {
        registerImplementation(NamedStrategyFixture.class);
    }

    private static final AtomicInteger TYPED_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger VARARGS_CONSTRUCTIONS = new AtomicInteger();

    private final Num threshold;
    private final boolean delegated;

    private NamedStrategyFixture(BarSeries series, Num threshold, int unstableBars, boolean delegated,
            boolean countTypedConstruction) {
        super(buildLabel(threshold, unstableBars), buildEntryRule(series, threshold), buildExitRule(series, threshold),
                unstableBars);
        this.threshold = threshold;
        this.delegated = delegated;
        if (countTypedConstruction) {
            TYPED_CONSTRUCTIONS.incrementAndGet();
        }
    }

    public NamedStrategyFixture(BarSeries series, String... parameters) {
        this(series, parseThreshold(series, parameters), parseUnstable(parameters), true, false);
        VARARGS_CONSTRUCTIONS.incrementAndGet();
    }

    public static NamedStrategyFixture create(BarSeries series, Num threshold, int unstableBars) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(threshold, "threshold");
        return new NamedStrategyFixture(series, threshold, unstableBars, false, true);
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
        if (parameters.length != 2) {
            throw new IllegalArgumentException("NamedStrategyFixture expects [threshold, unstable]");
        }
        String token = parameters[1];
        if (token.startsWith("u") && token.length() > 1) {
            token = token.substring(1);
        }
        return Integer.parseInt(token);
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

    private static String buildLabel(Num threshold, int unstableBars) {
        return NamedStrategy.buildLabel(NamedStrategyFixture.class, formatArgument(threshold), "u" + unstableBars);
    }
}
