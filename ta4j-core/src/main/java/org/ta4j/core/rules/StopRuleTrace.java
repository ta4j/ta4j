/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.num.Num;

/**
 * Package-private stop-rule trace field builder. Centralizing these field names
 * keeps stop-loss and stop-gain diagnostics consistent without adding public
 * API.
 *
 * @since 0.22.7
 */
final class StopRuleTrace {

    private StopRuleTrace() {
    }

    static void traceUnavailable(AbstractRule rule, int index, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        rule.traceIsSatisfied(index, false, AbstractRule.traceContext("reason", reason));
    }

    static void traceDecision(AbstractRule rule, int index, boolean satisfied, boolean buy, Num currentPrice,
            Num entryPrice, Num stopPrice, String amountField, Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        rule.traceIsSatisfied(index, satisfied,
                AbstractRule.traceContext("currentPrice", currentPrice, "entryPrice", entryPrice, "stopPrice",
                        stopPrice, "side", side(buy), amountField, amount, "reason", optionalReason(reason)));
    }

    static void traceTrailingDecision(AbstractRule rule, int index, boolean satisfied, boolean buy, Num currentPrice,
            Num entryPrice, Num stopPrice, String extremeField, Num extremePrice, int lookback, String amountField,
            Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        rule.traceIsSatisfied(index, satisfied,
                AbstractRule.traceContext("currentPrice", currentPrice, "entryPrice", entryPrice, "stopPrice",
                        stopPrice, "side", side(buy), extremeField, extremePrice, "lookback", lookback, amountField,
                        amount, "reason", optionalReason(reason)));
    }

    static void traceTrailingGainDecision(AbstractRule rule, int index, boolean satisfied, boolean buy,
            Num currentPrice, Num entryPrice, Num stopPrice, String extremeField, Num extremePrice, Num activationPrice,
            int lookback, String amountField, Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        rule.traceIsSatisfied(index, satisfied,
                AbstractRule.traceContext("currentPrice", currentPrice, "entryPrice", entryPrice, "stopPrice",
                        stopPrice, "side", side(buy), extremeField, extremePrice, "activationPrice", activationPrice,
                        "lookback", lookback, amountField, amount, "reason", optionalReason(reason)));
    }

    private static String side(boolean buy) {
        return buy ? "BUY" : "SELL";
    }

    private static String optionalReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason;
    }
}
