/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class CorrentropyKalmanExampleTest {

    @Test
    void trailingWindowIncludesTheLatestOssifiedBar() {
        BarSeries full = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE
                .loadSeries(CorrentropyKalmanExample.SP500_RESOURCE);
        assertNotNull(full, "S&P 500 resource should be available");

        BarSeries trailing = CorrentropyKalmanExample.loadSeries();

        assertEquals(Math.min(CorrentropyKalmanExample.WALK_BARS, full.getBarCount()), trailing.getBarCount());
        assertEquals(full.getLastBar().getEndTime(), trailing.getLastBar().getEndTime());
    }

    @Test
    void rejectionEvidenceUsesTheComplementOfMeasurementWeight() {
        BarSeries series = new MockBarSeriesBuilder().withData(0, 0).build();
        NumFactory numFactory = series.numFactory();
        Indicator<Num> residual = new FixedIndicator<>(series, numFactory.numOf(4), numFactory.numOf(-2));
        Indicator<Num> measurementWeight = new FixedIndicator<>(series, numFactory.one(), numFactory.numOf(0.25));

        Indicator<Num> evidence = CorrentropyKalmanExample.rejectionWeightedResidual(residual, measurementWeight);

        assertEquals(0.0, evidence.getValue(0).doubleValue(), 0.0);
        assertEquals(-1.5, evidence.getValue(1).doubleValue(), 0.0);
    }
}
