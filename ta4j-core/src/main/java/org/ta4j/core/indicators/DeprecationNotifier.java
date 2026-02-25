/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

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
final class DeprecationNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(DeprecationNotifier.class);
    private static final Set<String> EMITTED_TYPES = ConcurrentHashMap.newKeySet();

    private DeprecationNotifier() {
    }

    static void warnOnce(Class<?> deprecatedType, String replacementType, String removalVersion) {
        Objects.requireNonNull(deprecatedType, "deprecatedType");
        Objects.requireNonNull(replacementType, "replacementType");
        Objects.requireNonNull(removalVersion, "removalVersion");
        if (replacementType.isBlank()) {
            throw new IllegalArgumentException("replacementType must not be blank");
        }
        if (removalVersion.isBlank()) {
            throw new IllegalArgumentException("removalVersion must not be blank");
        }

        String deprecatedTypeName = deprecatedType.getName();
        if (!EMITTED_TYPES.add(deprecatedTypeName)) {
            return;
        }

        Deprecated deprecatedMetadata = deprecatedType.getAnnotation(Deprecated.class);
        String sinceVersion = deprecatedMetadata == null || deprecatedMetadata.since().isBlank() ? "an earlier release"
                : deprecatedMetadata.since();
        LOG.warn("{} is deprecated since {} and is scheduled for removal in {}. Use {} instead.", deprecatedTypeName,
                sinceVersion, removalVersion, replacementType);
    }

    static void resetForTests() {
        EMITTED_TYPES.clear();
    }
}
