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
