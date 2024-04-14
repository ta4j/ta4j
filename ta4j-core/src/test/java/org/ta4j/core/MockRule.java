package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;

/**
 * @author Lukáš Kvídera
 */
public class MockRule implements Rule {

    private List<Indicator<Num>> indicators = new ArrayList<>();

    public MockRule(final List<Indicator<Num>> indicators) {
        this.indicators = indicators;
    }

    @Override
    public boolean isSatisfied(final TradingRecord tradingRecord) {
        return false;
    }

    @Override
    public void refresh() {
        this.indicators.forEach(Indicator::refresh);
    }

    @Override
    public boolean isStable() {
        return true;
    }
}
