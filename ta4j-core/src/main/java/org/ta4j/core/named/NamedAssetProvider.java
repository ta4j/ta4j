/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

/**
 * Service provider interface for contributing immutable named-asset shorthand
 * bindings.
 * <p>
 * Implementations are discovered through {@link java.util.ServiceLoader} when
 * {@link NamedAssetRegistry#defaultRegistry()} is built. Providers should only
 * add deterministic startup-time bindings and must not retain the supplied
 * builder for later mutation.
 *
 * @since 0.22.7
 */
public interface NamedAssetProvider {

    /**
     * Adds bindings to the supplied registry builder.
     *
     * @param builder registry builder that is being assembled
     * @since 0.22.7
     */
    void registerNamedAssets(NamedAssetRegistry.Builder builder);
}
