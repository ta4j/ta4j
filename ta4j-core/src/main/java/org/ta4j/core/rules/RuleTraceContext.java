/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.ta4j.core.Rule;

/**
 * Evaluation-scoped trace policy for rule execution.
 *
 * <p>
 * A trace context is stored in a thread-local stack for the duration of one
 * rule or strategy evaluation. Composite rules use it to pass trace mode, path,
 * and depth information to children without mutating shared rule instances.
 *
 * @since 0.22.7
 */
final class RuleTraceContext {

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private RuleTraceContext() {
    }

    /**
     * Runs the supplied evaluation with a root trace frame.
     *
     * @param traceMode      the trace mode to apply to the evaluation
     * @param path           root path label
     * @param parentRuleName optional parent name, such as a strategy name
     * @param supplier       evaluation callback
     * @return the callback result
     * @since 0.22.7
     */
    static boolean evaluate(Rule.TraceMode traceMode, String path, String parentRuleName, BooleanSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        push(new Frame(normalize(traceMode), normalizePath(path), 0, parentRuleName));
        try {
            return supplier.getAsBoolean();
        } finally {
            pop();
        }
    }

    static Rule.TraceMode activeMode() {
        Frame frame = currentFrame();
        if (frame != null) {
            return frame.traceMode();
        }
        return Rule.TraceMode.VERBOSE;
    }

    static Frame currentFrame() {
        return FRAMES.get().peek();
    }

    static Scope openChild(String parentRuleName, String relation) {
        Frame currentFrame = currentFrame();
        Rule.TraceMode parentMode = activeMode();
        Rule.TraceMode childMode = parentMode == Rule.TraceMode.VERBOSE ? Rule.TraceMode.VERBOSE : null;
        int childDepth = currentFrame == null ? 1 : currentFrame.depth() + 1;
        String parentPath = currentFrame == null ? "root" : currentFrame.path();
        String childPath = parentPath + "." + normalizePath(relation);

        push(new Frame(childMode, childPath, childDepth, parentRuleName));
        return RuleTraceContext::pop;
    }

    private static void push(Frame frame) {
        FRAMES.get().push(frame);
    }

    private static void pop() {
        Deque<Frame> frames = FRAMES.get();
        frames.pop();
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private static Rule.TraceMode normalize(Rule.TraceMode traceMode) {
        return traceMode == null ? Rule.TraceMode.VERBOSE : traceMode;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "root";
        }
        return path.trim().replace(' ', '_');
    }

    record Frame(Rule.TraceMode traceMode, String path, int depth, String parentRuleName) {
    }

    interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
