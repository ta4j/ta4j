/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Verifies that {@link EquityBundle} reuses one curve instance per
 * configuration key across repeated requests.
 */
public class EquityBundleTest {

    private final DoubleNumFactory numFactory = DoubleNumFactory.getInstance();

    private BarSeries series() {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 2d, 4d, 3d, 5d, 4d, 6d, 5d, 7d)
                .build();
    }

    private TradingRecord closedPositionsRecord(BarSeries series) {
        return new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series), Trade.buyAt(6, series), Trade.sellAt(9, series));
    }

    @Test
    public void bundleReusesOneCurveInstancePerConfigurationKey() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CashFlow cashFlowMarkToMarket = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowAgain = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowRealized = equityBundle.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        assertSame(cashFlowMarkToMarket, cashFlowAgain);
        assertNotSame(cashFlowMarkToMarket, cashFlowRealized);

        InvestedInterval investedInterval = equityBundle.investedInterval(OpenPositionHandling.MARK_TO_MARKET);
        assertSame(investedInterval, equityBundle.investedInterval(OpenPositionHandling.MARK_TO_MARKET));

        CumulativePnL cumulativePnL = equityBundle.cumulativePnL(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertSame(cumulativePnL,
                equityBundle.cumulativePnL(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
    }
}
