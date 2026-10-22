/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.named.conflict.ConflictingStrategies;
import org.ta4j.core.strategy.named.NamedStrategy;

class NamedComponentRegistryTest {

    private static final String CONFLICT_PACKAGE = "org.ta4j.core.named.conflict";

    @Test
    public void failedPackageScanLeavesNoConflictingRegistrationOnRetry() {
        NamedComponentRegistry<NamedStrategy> registry = newRegistry();
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThrows(IllegalStateException.class, () -> registry.initializeRegistry(CONFLICT_PACKAGE));

            assertFalse(registry.lookup("ConflictingStrategy").isPresent());
            assertThrows(IllegalArgumentException.class, () -> registry.requireRegistered("ConflictingStrategy"));
        }
    }

    @Test
    public void failedPackageScanPreservesExplicitRegistrationsAndAllowsRecovery() {
        NamedComponentRegistry<NamedStrategy> registry = newRegistry();
        registry.registerImplementation(ConflictingStrategies.First.ConflictingStrategy.class);

        assertThrows(IllegalStateException.class, () -> registry.initializeRegistry(CONFLICT_PACKAGE));

        assertEquals(ConflictingStrategies.First.ConflictingStrategy.class,
                registry.requireRegistered("ConflictingStrategy"));
        assertTrue(registry.unregisterImplementation(ConflictingStrategies.First.ConflictingStrategy.class));
        assertFalse(registry.lookup("ConflictingStrategy").isPresent());
        registry.registerImplementation(ConflictingStrategies.Second.ConflictingStrategy.class);
        assertEquals(ConflictingStrategies.Second.ConflictingStrategy.class,
                registry.requireRegistered("ConflictingStrategy"));
    }

    @Test
    public void failedPackageScanRetainsPreviouslySuccessfulPackages() {
        NamedComponentRegistry<NamedStrategy> registry = newRegistry();

        assertThrows(IllegalStateException.class,
                () -> registry.initializeRegistry("org.ta4j.core.strategy.named", CONFLICT_PACKAGE));

        assertTrue(registry.lookup("ScanOnlyStrategyProbe").isPresent());
        assertFalse(registry.lookup("ConflictingStrategy").isPresent());
        registry.resetScanState();
        assertThrows(IllegalStateException.class, () -> registry.initializeRegistry(CONFLICT_PACKAGE));
        assertTrue(registry.lookup("ScanOnlyStrategyProbe").isPresent());
        assertFalse(registry.lookup("ConflictingStrategy").isPresent());
    }

    @Test
    public void failedJarScanLeavesNoConflictingRegistration(@TempDir Path tempDir) throws Exception {
        String packagePath = CONFLICT_PACKAGE.replace('.', '/');
        Path jar = tempDir.resolve("conflicting-components.jar");
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(jar))) {
            archive.putNextEntry(new JarEntry(packagePath + '/'));
            archive.closeEntry();
            for (Class<?> type : List.of(ConflictingStrategies.First.ConflictingStrategy.class,
                    ConflictingStrategies.Second.ConflictingStrategy.class)) {
                String resource = type.getName().replace('.', '/') + ".class";
                archive.putNextEntry(new JarEntry(resource));
                try (InputStream bytecode = type.getResourceAsStream('/' + resource)) {
                    assertNotNull(bytecode, resource);
                    bytecode.transferTo(archive);
                }
                archive.closeEntry();
            }
        }
        URL packageUrl = URI.create("jar:" + jar.toUri() + "!/" + packagePath).toURL();
        ClassLoader jarLoader = new ClassLoader(NamedStrategy.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return name.equals(packagePath) ? Collections.enumeration(List.of(packageUrl)) : super.getResources(name);
            }
        };
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(jarLoader);
            NamedComponentRegistry<NamedStrategy> registry = newRegistry();

            assertThrows(IllegalStateException.class, () -> registry.initializeRegistry(CONFLICT_PACKAGE));

            assertFalse(registry.lookup("ConflictingStrategy").isPresent());
            assertThrows(IllegalArgumentException.class, () -> registry.requireRegistered("ConflictingStrategy"));
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static NamedComponentRegistry<NamedStrategy> newRegistry() {
        return new NamedComponentRegistry<>(NamedStrategy.class, "strategy", "NamedStrategy", NamedStrategy.class);
    }
}
