/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.utils;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits runtime deprecation notices for compatibility shims.
 *
 * <p>
 * Each deprecated type is logged only once per classloader to avoid log spam.
 *
 * @since 0.22.3
 */
public final class DeprecationNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(DeprecationNotifier.class);
    private static final Set<String> EMITTED_TYPES = ConcurrentHashMap.newKeySet();

    private DeprecationNotifier() {
    }

    /**
     * Emits a one-time warning for a deprecated compatibility type.
     *
     * <p>
     * If {@code removalVersion} is null or blank, the warning highlights that
     * removal is still planned but not scheduled for a specific release yet.
     *
     * @param deprecatedType  deprecated type that is being instantiated
     * @param replacementType fully qualified replacement type name
     * @param removalVersion  target removal version, or null/blank when unscheduled
     * @since 0.22.3
     */
    public static void warnOnce(Class<?> deprecatedType, String replacementType, String removalVersion) {
        Objects.requireNonNull(deprecatedType, "deprecatedType");
        Objects.requireNonNull(replacementType, "replacementType");
        if (replacementType.isBlank()) {
            throw new IllegalArgumentException("replacementType must not be blank");
        }

        String deprecatedTypeName = deprecatedType.getName();
        if (!EMITTED_TYPES.add(deprecatedTypeName)) {
            return;
        }

        Deprecated deprecatedMetadata = deprecatedType.getAnnotation(Deprecated.class);
        String sinceVersion = deprecatedMetadata == null || deprecatedMetadata.since().isBlank() ? "an earlier release"
                : deprecatedMetadata.since();
        String normalizedRemovalVersion = removalVersion == null ? "" : removalVersion.trim();
        if (normalizedRemovalVersion.isBlank()) {
            LOG.warn(
                    "{} is deprecated and will be removed at some point in the future. As these things go, that point will come sooner than you think, so start migrating now. Consider yourself fairly warned! Use {} instead.",
                    deprecatedTypeName, replacementType);
            return;
        }
        LOG.warn("{} is deprecated since {} and is scheduled for removal in {}. Use {} instead.", deprecatedTypeName,
                sinceVersion, normalizedRemovalVersion, replacementType);
    }

    /**
     * Emits a one-time warning for a deprecated type without a scheduled removal
     * version.
     *
     * @param deprecatedType  deprecated type that is being instantiated
     * @param replacementType fully qualified replacement type name
     * @since 0.22.3
     */
    public static void warnOnce(Class<?> deprecatedType, String replacementType) {
        warnOnce(deprecatedType, replacementType, null);
    }

    /**
     * Clears the once-per-type emission cache.
     *
     * <p>
     * Intended for test isolation when multiple test classes validate
     * deprecation-warning output in the same JVM.
     *
     * @since 0.22.3
     */
    public static void resetForTests() {
        EMITTED_TYPES.clear();
    }
}
