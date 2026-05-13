/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, String> context = new LinkedHashMap<>();
        context.put("reason", reason);
        rule.traceIsSatisfied(index, false, context);
    }

    static void traceDecision(AbstractRule rule, int index, boolean satisfied, boolean buy, Num currentPrice,
            Num entryPrice, Num stopPrice, String amountField, Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        Map<String, String> context = new LinkedHashMap<>();
        context.put("currentPrice", value(currentPrice));
        context.put("entryPrice", value(entryPrice));
        context.put("stopPrice", value(stopPrice));
        context.put("side", side(buy));
        context.put(amountField, value(amount));
        if (reason != null && !reason.isBlank()) {
            context.put("reason", reason);
        }
        rule.traceIsSatisfied(index, satisfied, context);
    }

    static void traceTrailingDecision(AbstractRule rule, int index, boolean satisfied, boolean buy, Num currentPrice,
            Num entryPrice, Num stopPrice, String extremeField, Num extremePrice, int lookback, String amountField,
            Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        Map<String, String> context = new LinkedHashMap<>();
        context.put("currentPrice", value(currentPrice));
        context.put("entryPrice", value(entryPrice));
        context.put("stopPrice", value(stopPrice));
        context.put("side", side(buy));
        context.put(extremeField, value(extremePrice));
        context.put("lookback", Integer.toString(lookback));
        context.put(amountField, value(amount));
        if (reason != null && !reason.isBlank()) {
            context.put("reason", reason);
        }
        rule.traceIsSatisfied(index, satisfied, context);
    }

    static void traceTrailingGainDecision(AbstractRule rule, int index, boolean satisfied, boolean buy,
            Num currentPrice, Num entryPrice, Num stopPrice, String extremeField, Num extremePrice, Num activationPrice,
            int lookback, String amountField, Num amount, String reason) {
        if (!rule.isTraceEnabled()) {
            return;
        }
        Map<String, String> context = new LinkedHashMap<>();
        context.put("currentPrice", value(currentPrice));
        context.put("entryPrice", value(entryPrice));
        context.put("stopPrice", value(stopPrice));
        context.put("side", side(buy));
        context.put(extremeField, value(extremePrice));
        context.put("activationPrice", value(activationPrice));
        context.put("lookback", Integer.toString(lookback));
        context.put(amountField, value(amount));
        if (reason != null && !reason.isBlank()) {
            context.put("reason", reason);
        }
        rule.traceIsSatisfied(index, satisfied, context);
    }

    private static String side(boolean buy) {
        return buy ? "BUY" : "SELL";
    }

    private static String value(Num value) {
        return String.valueOf(value);
    }
}
