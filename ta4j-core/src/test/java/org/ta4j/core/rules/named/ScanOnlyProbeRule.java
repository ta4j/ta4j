/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

/**
 * Test rule registered exclusively through the default package scan. The test
 * JVM never references this class directly, so its static initializer cannot
 * register it; only {@link NamedRule}'s classpath scan of the default packages
 * can.
 */
public final class ScanOnlyProbeRule extends NamedRule {

    static {
        registerImplementation(ScanOnlyProbeRule.class);
    }

    public ScanOnlyProbeRule(BarSeries series, String... params) {
        super(buildLabel(ScanOnlyProbeRule.class, params));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        return false;
    }
}
