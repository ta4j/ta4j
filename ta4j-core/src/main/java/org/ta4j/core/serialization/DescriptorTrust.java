/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

/**
 * Trust boundary for reflective rule and strategy reconstruction.
 * <p>
 * Descriptors are untrusted by default. Untrusted reconstruction only resolves
 * types that ta4j ships ({@value #CORE_PACKAGE_PREFIX}*), checking the name
 * before the class is loaded so a rejected class is never initialized, and only
 * infers public constructors so a descriptor cannot bypass the validation of a
 * public constructor through a private one. Trusted reconstruction, entered
 * through the {@code fromTrusted*} methods, keeps the historical behavior for
 * custom types and non-public constructors.
 * <p>
 * The trust decision is scoped to the current call, so it covers every nested
 * rule and strategy component that call reconstructs and ends when it returns.
 */
final class DescriptorTrust {

    /** Package prefix of the types untrusted descriptors may name. */
    static final String CORE_PACKAGE_PREFIX = "org.ta4j.core.";

    private static final ScopedValue<Boolean> TRUSTED = ScopedValue.newInstance();

    private DescriptorTrust() {
    }

    /**
     * @return {@code true} while running inside {@link #trusted(Supplier)}
     */
    static boolean isTrusted() {
        return TRUSTED.orElse(Boolean.FALSE);
    }

    /**
     * Runs {@code action} with trusted reconstruction enabled.
     *
     * @param action reconstruction to run
     * @param <T>    result type
     * @return the action's result
     */
    static <T> T trusted(Supplier<T> action) {
        return ScopedValue.where(TRUSTED, Boolean.TRUE).call(action::get);
    }

    /**
     * Resolves a descriptor type under the current trust level.
     *
     * @param typeName         fully qualified or simple class name
     * @param expectedType     required supertype
     * @param fallbackPackages packages tried, in order, for simple names
     * @param <T>              required supertype
     * @return the resolved, uninitialized subtype, or {@code null} when the type is
     *         unknown, of the wrong kind, or not allowed for untrusted input
     */
    static <T> Class<? extends T> resolve(String typeName, Class<T> expectedType, String... fallbackPackages) {
        if (isTrusted()) {
            return ComponentDescriptor.resolveSubtype(typeName, expectedType, fallbackPackages);
        }
        if (typeName.indexOf('.') < 0) {
            // Simple names resolve only inside the caller's ta4j fallback packages,
            // never the default package.
            Class<? extends T> resolved = null;
            for (int i = 0; resolved == null && i < fallbackPackages.length; i++) {
                resolved = ComponentDescriptor.resolveSubtype(fallbackPackages[i] + '.' + typeName, expectedType);
            }
            return resolved;
        }
        return typeName.startsWith(CORE_PACKAGE_PREFIX) ? ComponentDescriptor.resolveSubtype(typeName, expectedType)
                : null;
    }

    /**
     * Whether a constructor or factory may be used by inferred reconstruction under
     * the current trust level.
     *
     * @param executable candidate constructor or method
     * @return {@code true} if trusted or the executable and its class are public
     */
    static boolean mayInvoke(Executable executable) {
        if (isTrusted()) {
            return true;
        }
        Class<?> owner = executable.getDeclaringClass();
        return Modifier.isPublic(executable.getModifiers()) && Modifier.isPublic(owner.getModifiers());
    }

}
