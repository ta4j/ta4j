package org.ta4j.core;

import java.util.List;

import org.ta4j.core.backtest.BacktestStrategy;

/**
 * @author Lukáš Kvídera
 */
public class MockStrategy extends BacktestStrategy {
    /**
     * Constructor.
     *
     * @param mockRule the mocked rule
     */
    public MockStrategy(final Rule mockRule) {
        super("mock strategy", mockRule, new MockRule(List.of()));
    }
}
