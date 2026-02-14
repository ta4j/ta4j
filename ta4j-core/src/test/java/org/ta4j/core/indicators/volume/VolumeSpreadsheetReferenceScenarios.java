/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * Shared bullish, bearish, and sideways OHLCV fixtures used for spreadsheet
 * regression tests.
 */
final class VolumeSpreadsheetReferenceScenarios {

    private static final double[][] BULLISH_OHLCV = { { 100, 102, 103, 99, 1000 }, { 102, 104, 105, 101, 1100 },
            { 104, 107, 108, 103, 1200 }, { 107, 109, 110, 106, 1300 }, { 109, 112, 113, 108, 1400 },
            { 112, 114, 115, 111, 1500 }, { 114, 117, 118, 113, 1600 }, { 117, 119, 120, 116, 1700 },
            { 119, 121, 122, 118, 1800 }, { 121, 124, 125, 120, 1900 }, { 124, 126, 127, 123, 2000 },
            { 126, 129, 130, 125, 2100 } };

    private static final double[][] BEARISH_OHLCV = { { 130, 128, 131, 127, 1050 }, { 128, 126, 129, 125, 1150 },
            { 126, 123, 127, 122, 1250 }, { 123, 121, 124, 120, 1350 }, { 121, 118, 122, 117, 1450 },
            { 118, 116, 119, 115, 1550 }, { 116, 113, 117, 112, 1650 }, { 113, 111, 114, 110, 1750 },
            { 111, 109, 112, 108, 1850 }, { 109, 106, 110, 105, 1950 }, { 106, 104, 107, 103, 2050 },
            { 104, 101, 105, 100, 2150 } };

    private static final double[][] SIDEWAYS_OHLCV = { { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 980 },
            { 100, 101, 102, 99, 1020 }, { 101, 100, 102, 99, 1000 }, { 100, 101, 102, 99, 990 },
            { 101, 100, 102, 99, 1010 }, { 100, 101, 102, 99, 995 }, { 101, 100, 102, 99, 1005 },
            { 100, 101, 102, 99, 1000 }, { 101, 100, 102, 99, 1002 }, { 100, 101, 102, 99, 998 },
            { 101, 100, 102, 99, 1001 } };

    private VolumeSpreadsheetReferenceScenarios() {
    }

    static BarSeries bullish(final NumFactory numFactory) {
        return buildSeries(numFactory, BULLISH_OHLCV);
    }

    static BarSeries bearish(final NumFactory numFactory) {
        return buildSeries(numFactory, BEARISH_OHLCV);
    }

    static BarSeries sideways(final NumFactory numFactory) {
        return buildSeries(numFactory, SIDEWAYS_OHLCV);
    }

    private static BarSeries buildSeries(final NumFactory numFactory, final double[][] ohlcvData) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double[] row : ohlcvData) {
            series.barBuilder()
                    .openPrice(row[0])
                    .closePrice(row[1])
                    .highPrice(row[2])
                    .lowPrice(row[3])
                    .volume(row[4])
                    .add();
        }
        return series;
    }
}
