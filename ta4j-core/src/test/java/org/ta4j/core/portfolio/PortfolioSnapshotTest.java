/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

public class PortfolioSnapshotTest {

    private static final NumFactory NUM = DoubleNumFactory.getInstance();

    @Test
    public void exposesAchievedAssetAndCashWeights() {
        PortfolioSnapshot snapshot = snapshot(NUM.numOf(200), NUM.numOf(1000));

        assertNumEquals(600, snapshot.getAssetValue("ALPHA"));
        assertNumEquals(0.6, snapshot.getAssetWeight("ALPHA"));
        assertNumEquals(0.2, snapshot.getAssetWeight("BETA"));
        assertNumEquals(0.2, snapshot.getCashWeight());
        assertEquals(List.of("ALPHA", "BETA"), List.copyOf(snapshot.getAssetWeights().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getHoldings().put("ALPHA", NUM.one()));
    }

    @Test
    public void zeroValuePortfolioHasZeroWeights() {
        PortfolioSnapshot snapshot = snapshot(NUM.zero(), NUM.zero());

        assertNumEquals(0, snapshot.getCashWeight());
        assertNumEquals(0, snapshot.getAssetWeight("ALPHA"));
    }

    @Test
    public void rejectsUnknownAssets() {
        PortfolioSnapshot snapshot = snapshot(NUM.numOf(200), NUM.numOf(1000));

        assertThrows(IllegalArgumentException.class, () -> snapshot.getAssetWeight("MISSING"));
    }

    private static PortfolioSnapshot snapshot(Num cash, Num value) {
        Map<String, Num> prices = new LinkedHashMap<>();
        prices.put("ALPHA", NUM.numOf(100));
        prices.put("BETA", NUM.numOf(50));
        Map<String, Num> holdings = new LinkedHashMap<>();
        boolean empty = value.isZero();
        holdings.put("ALPHA", empty ? NUM.zero() : NUM.numOf(6));
        holdings.put("BETA", empty ? NUM.zero() : NUM.numOf(4));
        return new PortfolioSnapshot(0, PortfolioFixtures.START, prices, holdings, cash, value, NUM.zero(), NUM.zero(),
                NUM.zero(), NUM.zero(), RebalanceStatus.NOT_SCHEDULED);
    }
}
