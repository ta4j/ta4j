/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Computes the aggregate directional bias across Elliott wave scenarios.
 *
 * <p>
 * Use this indicator to derive a single bullish/bearish/neutral signal from a
 * scenario set. It is useful for strategy filters or dashboards that do not
 * need the full scenario list.
 *
 * @since 0.22.2
 */
public class ElliottTrendBiasIndicator extends CachedIndicator<ElliottTrendBias> {

    private final ElliottScenarioIndicator scenarioIndicator;
    private final double neutralThreshold;

    /**
     * Creates a trend bias indicator using the default neutral threshold.
     *
     * @param scenarioIndicator source of scenario data
     * @since 0.22.2
     */
    public ElliottTrendBiasIndicator(final ElliottScenarioIndicator scenarioIndicator) {
        this(validatedConfig(scenarioIndicator, ElliottTrendBias.DEFAULT_NEUTRAL_THRESHOLD));
    }

    /**
     * Creates a trend bias indicator with a custom neutral threshold.
     *
     * @param scenarioIndicator source of scenario data
     * @param neutralThreshold  absolute score below which bias is neutral
     * @since 0.22.2
     */
    public ElliottTrendBiasIndicator(final ElliottScenarioIndicator scenarioIndicator, final double neutralThreshold) {
        this(validatedConfig(scenarioIndicator, neutralThreshold));
    }

    private ElliottTrendBiasIndicator(final Config config) {
        super(config.series());
        this.scenarioIndicator = config.scenarioIndicator();
        this.neutralThreshold = config.neutralThreshold();
    }

    private static Config validatedConfig(final ElliottScenarioIndicator scenarioIndicator,
            final double neutralThreshold) {
        final BarSeries series = requireSeries(scenarioIndicator);
        final ElliottScenarioIndicator validatedScenarioIndicator = Objects.requireNonNull(scenarioIndicator,
                "scenarioIndicator");
        return new Config(series, validatedScenarioIndicator, neutralThreshold);
    }

    private static BarSeries requireSeries(final ElliottScenarioIndicator scenarioIndicator) {
        final BarSeries series = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Scenario indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected ElliottTrendBias calculate(final int index) {
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(index);
        return ElliottTrendBias.fromScenarios(scenarioSet.all(), neutralThreshold);
    }

    @Override
    public int getCountOfUnstableBars() {
        return scenarioIndicator.getCountOfUnstableBars();
    }

    /**
     * @return the underlying scenario indicator
     * @since 0.22.2
     */
    public ElliottScenarioIndicator getScenarioIndicator() {
        return scenarioIndicator;
    }

    /**
     * @return neutral threshold used to classify bias
     * @since 0.22.2
     */
    public double getNeutralThreshold() {
        return neutralThreshold;
    }

    private record Config(BarSeries series, ElliottScenarioIndicator scenarioIndicator, double neutralThreshold) {
    }
}
