/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Options controlling how windowed criterion analysis is resolved.
 *
 * <p>
 * The default context is conservative and deterministic:
 * </p>
 * <ul>
 * <li>{@link MissingHistoryPolicy#STRICT}</li>
 * <li>{@link PositionInclusionPolicy#EXIT_IN_WINDOW}</li>
 * <li>{@link OpenPositionPolicy#EXCLUDE}</li>
 * <li>no explicit anchor/as-of instant</li>
 * </ul>
 *
 * @param missingHistoryPolicy    behavior when requested history is unavailable
 * @param positionInclusionPolicy policy used to include closed positions in the
 *                                window
 * @param openPositionPolicy      handling strategy for open position at window
 *                                end
 * @param asOf                    optional anchor/as-of instant; when null, the
 *                                series end is used
 * @since 0.22.3
 */
public record AnalysisContext(MissingHistoryPolicy missingHistoryPolicy,
        PositionInclusionPolicy positionInclusionPolicy, OpenPositionPolicy openPositionPolicy, Instant asOf) {

    /**
     * Policy for unavailable historical bars.
     *
     * @since 0.22.3
     */
    public enum MissingHistoryPolicy {
        /**
         * Fail fast when requested window references unavailable series history.
         */
        STRICT,
        /**
         * Intersect the requested window with available series history.
         */
        CLAMP
    }

    /**
     * Policy controlling which closed positions are included in a window.
     *
     * @since 0.22.3
     */
    public enum PositionInclusionPolicy {
        /**
         * Include a closed position when its exit index is inside the window.
         */
        EXIT_IN_WINDOW,
        /**
         * Include a closed position only when both entry and exit are in the window.
         */
        FULLY_CONTAINED
    }

    /**
     * Policy controlling how to handle open position at the window end.
     *
     * @since 0.22.3
     */
    public enum OpenPositionPolicy {
        /**
         * Exclude the open position.
         */
        EXCLUDE,
        /**
         * Synthesize a window-end close for the open position.
         */
        MARK_TO_MARKET_AT_WINDOW_END
    }

    /**
     * Creates a default context.
     *
     * @since 0.22.3
     */
    public AnalysisContext() {
        this(MissingHistoryPolicy.STRICT, PositionInclusionPolicy.EXIT_IN_WINDOW, OpenPositionPolicy.EXCLUDE, null);
    }

    /**
     * Validates the context.
     */
    public AnalysisContext {
        Objects.requireNonNull(missingHistoryPolicy, "missingHistoryPolicy");
        Objects.requireNonNull(positionInclusionPolicy, "positionInclusionPolicy");
        Objects.requireNonNull(openPositionPolicy, "openPositionPolicy");
    }

    /**
     * Creates a default context.
     *
     * @return default window analysis context
     * @since 0.22.3
     */
    public static AnalysisContext defaults() {
        return new AnalysisContext();
    }

    /**
     * Returns a copy with a different missing-history policy.
     *
     * @param policy the policy to use
     * @return updated context
     * @since 0.22.3
     */
    public AnalysisContext withMissingHistoryPolicy(MissingHistoryPolicy policy) {
        return new AnalysisContext(Objects.requireNonNull(policy, "policy"), positionInclusionPolicy,
                openPositionPolicy, asOf);
    }

    /**
     * Returns a copy with a different position-inclusion policy.
     *
     * @param policy the policy to use
     * @return updated context
     * @since 0.22.3
     */
    public AnalysisContext withPositionInclusionPolicy(PositionInclusionPolicy policy) {
        return new AnalysisContext(missingHistoryPolicy, Objects.requireNonNull(policy, "policy"), openPositionPolicy,
                asOf);
    }

    /**
     * Returns a copy with a different open-position policy.
     *
     * @param policy the policy to use
     * @return updated context
     * @since 0.22.3
     */
    public AnalysisContext withOpenPositionPolicy(OpenPositionPolicy policy) {
        return new AnalysisContext(missingHistoryPolicy, positionInclusionPolicy,
                Objects.requireNonNull(policy, "policy"), asOf);
    }

    /**
     * Returns a copy with a different anchor/as-of instant.
     *
     * @param asOf the anchor instant, or null to use series end
     * @return updated context
     * @since 0.22.3
     */
    public AnalysisContext withAsOf(Instant asOf) {
        return new AnalysisContext(missingHistoryPolicy, positionInclusionPolicy, openPositionPolicy, asOf);
    }
}
