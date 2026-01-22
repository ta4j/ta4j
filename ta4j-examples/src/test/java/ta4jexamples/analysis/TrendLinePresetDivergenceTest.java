/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

import static org.junit.jupiter.api.Assertions.*;

class TrendLinePresetDivergenceTest {

    @Test
    void supportPresetsProduceDistinctSegmentsForAppleSeries() {
        final BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        assertFalse(series.isEmpty(), "Example bar series should not be empty");

        final int lookback = Math.min(series.getBarCount(), 200);
        final int surroundingBars = 5;
        final int endIndex = series.getEndIndex();

        final TrendLineSupportIndicator defaultLine = new TrendLineSupportIndicator(series, surroundingBars, lookback);
        final TrendLineSupportIndicator touchBiasedLine = new TrendLineSupportIndicator(series, surroundingBars,
                lookback, TrendLineSupportIndicator.ScoringWeights.touchCountBiasPreset());
        final TrendLineSupportIndicator extremeBiasedLine = new TrendLineSupportIndicator(series, surroundingBars,
                lookback, TrendLineSupportIndicator.ScoringWeights.extremeSwingBiasPreset());

        defaultLine.getValue(endIndex);
        touchBiasedLine.getValue(endIndex);
        extremeBiasedLine.getValue(endIndex);

        final TrendLineSegment defaultSegment = defaultLine.getCurrentSegment();
        final TrendLineSegment touchSegment = touchBiasedLine.getCurrentSegment();
        final TrendLineSegment extremeSegment = extremeBiasedLine.getCurrentSegment();

        assertAll(() -> assertNotNull(defaultSegment), () -> assertNotNull(touchSegment),
                () -> assertNotNull(extremeSegment));

        assertAll(() -> assertEquals(123, defaultSegment.firstIndex),
                () -> assertEquals(177, defaultSegment.secondIndex), () -> assertEquals(177, touchSegment.firstIndex),
                () -> assertEquals(243, touchSegment.secondIndex), () -> assertEquals(74, extremeSegment.firstIndex),
                () -> assertEquals(123, extremeSegment.secondIndex));

        assertNotEquals(anchorLabel(defaultSegment), anchorLabel(touchSegment));
        assertNotEquals(anchorLabel(defaultSegment), anchorLabel(extremeSegment));
        assertNotEquals(anchorLabel(touchSegment), anchorLabel(extremeSegment));

        assertTrue(touchSegment.touchCount > defaultSegment.touchCount,
                "Touch-biased preset should prefer the line with the most swing touches");
        assertTrue(extremeSegment.touchesExtreme, "Extreme-biased preset should include the extreme swing");
        assertFalse(defaultSegment.touchesExtreme,
                "Default preset should select a line that balances proximity over extreme anchoring");
    }

    private String anchorLabel(TrendLineSegment segment) {
        return segment.firstIndex + "-" + segment.secondIndex;
    }
}
