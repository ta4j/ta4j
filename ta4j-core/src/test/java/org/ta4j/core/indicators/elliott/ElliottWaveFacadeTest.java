/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

/**
 * Tests for {@link ElliottWaveFacade}.
 */
class ElliottWaveFacadeTest {

    @Test
    void fractalFactoryShouldCreateAllIndicators() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);

        assertThat(suite.series()).isSameAs(series);
        assertThat(suite.swing()).isNotNull();
        assertThat(suite.phase()).isNotNull();
        assertThat(suite.ratio()).isNotNull();
        assertThat(suite.channel()).isNotNull();
        assertThat(suite.waveCount()).isNotNull();
        assertThat(suite.confluence()).isNotNull();
        assertThat(suite.invalidation()).isNotNull();
    }

    @Test
    void indicatorsAreReused() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);

        // Subsequent calls return the same instance
        var phase1 = suite.phase();
        var phase2 = suite.phase();
        assertThat(phase1).isSameAs(phase2);

        // Same for other indicators
        assertThat(suite.ratio()).isSameAs(suite.ratio());
        assertThat(suite.channel()).isSameAs(suite.channel());
    }

    @Test
    void zigZagFactoryShouldCreateIndicators() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4, 18, 3 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.zigZag(series, ElliottDegree.INTERMEDIATE);

        assertThat(suite.swing()).isNotNull();
        assertThat(suite.phase().getValue(series.getEndIndex())).isNotNull();
    }

    @Test
    void suiteIndicatorsShareSameSwingSource() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);
        var swing = suite.swing();

        // All indicators should use the same swing source
        assertThat(suite.phase().getSwingIndicator()).isSameAs(swing);
        assertThat(suite.ratio().getSwingIndicator()).isSameAs(swing);
        assertThat(suite.channel().getSwingIndicator()).isSameAs(swing);
        assertThat(suite.waveCount().getSwingIndicator()).isSameAs(swing);
        assertThat(suite.scenarios().getSwingIndicator()).isSameAs(swing);
    }

    @Test
    void customFibToleranceShouldBeUsed() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var customTolerance = series.numFactory().numOf(0.25);
        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR, Optional.of(customTolerance),
                Optional.empty());

        // Phase indicator should use custom validator with custom tolerance
        assertThat(suite.phase()).isNotNull();
        assertThat(suite.phase().getValue(series.getEndIndex())).isNotNull();
    }

    @Test
    void customCompressorShouldBeUsedForFilteredWaveCount() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var compressor = new ElliottSwingCompressor(series.numFactory().numOf(6), 0);
        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR, Optional.empty(),
                Optional.of(compressor));

        var basicCount = suite.waveCount();
        var filteredCount = suite.filteredWaveCount();

        // Filtered count should use compressor and may differ from basic count
        assertThat(filteredCount).isNotNull();
        assertThat(basicCount).isNotNull();
        // Filtered count should be less than or equal to basic count
        assertThat(filteredCount.getValue(series.getEndIndex()))
                .isLessThanOrEqualTo(basicCount.getValue(series.getEndIndex()));
    }

    @Test
    void bothCustomConfigurationsShouldWorkTogether() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var customTolerance = series.numFactory().numOf(0.25);
        var compressor = new ElliottSwingCompressor(series.numFactory().numOf(6), 0);
        var suite = ElliottWaveFacade.zigZag(series, ElliottDegree.MINOR, Optional.of(customTolerance),
                Optional.of(compressor));

        assertThat(suite.phase()).isNotNull();
        assertThat(suite.filteredWaveCount()).isNotNull();
        assertThat(suite.waveCount()).isNotNull();
    }
}
