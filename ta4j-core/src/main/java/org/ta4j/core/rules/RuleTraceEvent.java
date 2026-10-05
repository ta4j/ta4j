/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ta4j.core.Rule;

/**
 * Structured rule trace payload emitted by {@link AbstractRule}.
 *
 * @param index          the evaluated bar index
 * @param ruleType       concrete rule class name
 * @param ruleName       display name used in logs
 * @param traceMode      active trace mode for this event
 * @param satisfied      evaluation result
 * @param path           evaluation path from the trace root
 * @param depth          evaluation depth from the trace root
 * @param parentRuleName optional parent rule or strategy name
 * @param context        deterministic context fields for state-heavy rules
 * @since 0.22.7
 */
record RuleTraceEvent(int index, String ruleType, String ruleName, Rule.TraceMode traceMode, boolean satisfied,
        String path, int depth, String parentRuleName, Map<String, String> context) {

    /**
     * Constructor.
     *
     * @since 0.22.7
     */
    public RuleTraceEvent {
        context = context == null || context.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * Formats this structured event for the legacy SLF4J trace sink.
     *
     * @return stable log message
     * @since 0.22.7
     */
    public String formatMessage() {
        StringBuilder builder = new StringBuilder().append(ruleName)
                .append("#isSatisfied(")
                .append(index)
                .append("): ")
                .append(satisfied)
                .append(" mode=")
                .append(traceMode)
                .append(" ruleType=")
                .append(ruleType)
                .append(" path=")
                .append(path)
                .append(" depth=")
                .append(depth);

        if (parentRuleName != null && !parentRuleName.isBlank()) {
            builder.append(" parent=").append(parentRuleName);
        }
        for (Map.Entry<String, String> entry : context.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }

        return builder.toString();
    }
}
