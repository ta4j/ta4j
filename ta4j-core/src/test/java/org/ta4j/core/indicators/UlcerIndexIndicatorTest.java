/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class UlcerIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries ibmData;

    public UlcerIndexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        ibmData = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(194.75, 195.00, 195.10, 194.46, 190.60, 188.86, 185.47, 184.46, 182.31, 185.22, 184.00,
                        182.87, 187.45, 194.51, 191.63, 190.02, 189.53, 190.27, 193.13, 195.55, 195.84, 195.15, 194.35,
                        193.62, 197.68, 197.91, 199.08, 199.03, 198.42, 199.29, 199.01, 198.29, 198.40, 200.84, 201.22,
                        200.50, 198.65, 197.25, 195.70, 197.77, 195.69, 194.87, 195.08)
                .build();
    }

    @Test
    public void ulcerIndexUsingBarCount14UsingIBMData() {
        var ulcer = new UlcerIndexIndicator(new ClosePriceIndicator(ibmData), 14);

        assertNumEquals(0, ulcer.getValue(0));

        assertNumEquals(0, ulcer.getValue(0));
        assertNumEquals(1.2340096463740846, ulcer.getValue(26));
        assertNumEquals(0.560553282860879, ulcer.getValue(27));
        assertNumEquals(0.39324888828140886, ulcer.getValue(28));
        assertNumEquals(0.38716275079310825, ulcer.getValue(29));
        assertNumEquals(0.3889794194862251, ulcer.getValue(30));
        assertNumEquals(0.4114481689096125, ulcer.getValue(31));
        assertNumEquals(0.42841008722557894, ulcer.getValue(32));
        assertNumEquals(0.42841008722557894, ulcer.getValue(33));
        assertNumEquals(0.3121617589229034, ulcer.getValue(34));
        assertNumEquals(0.2464924497436544, ulcer.getValue(35));
        assertNumEquals(0.4089008481549337, ulcer.getValue(36));
        assertNumEquals(0.667264629592715, ulcer.getValue(37));
        assertNumEquals(0.9913518177402276, ulcer.getValue(38));
        assertNumEquals(1.0921325741850083, ulcer.getValue(39));
        assertNumEquals(1.3156949266800984, ulcer.getValue(40));
        assertNumEquals(1.5606676136361992, ulcer.getValue(41));
    }
}
