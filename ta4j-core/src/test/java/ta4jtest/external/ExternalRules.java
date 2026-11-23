/*
 * SPDX-License-Identifier: MIT
 */
package ta4jtest.external;

import java.util.concurrent.atomic.AtomicInteger;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.AbstractRule;

/**
 * Rule types outside {@code org.ta4j.core}, used to check that untrusted
 * descriptors cannot load them.
 */
public final class ExternalRules {

    /** Counts initializations of {@link UntrustedProbe}. */
    public static final AtomicInteger PROBE_INITIALIZATIONS = new AtomicInteger();

    private ExternalRules() {
    }

    /** Never restored through a trusted path, so it must stay uninitialized. */
    public static final class UntrustedProbe extends AbstractRule {

        static {
            PROBE_INITIALIZATIONS.incrementAndGet();
        }

        public UntrustedProbe() {
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return true;
        }
    }

    /** A legitimate custom rule restored through the trusted API. */
    public static final class TrustedRule extends AbstractRule {

        public TrustedRule() {
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return true;
        }
    }
}
