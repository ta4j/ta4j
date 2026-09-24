/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

/**
 * Test rule that only {@link NamedRule}'s classpath scan of the default
 * packages registers: it has no self-registering static initializer, and the
 * scan inspects classes without initializing them.
 */
public final class ScanOnlyProbeRule extends NamedRule {

    public ScanOnlyProbeRule(BarSeries series, String... params) {
        super(ScanOnlyProbeRule.class, params);
        Objects.requireNonNull(series, "series");
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        return false;
    }
}
